package com.jarvis.assistant

import android.graphics.PointF
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Full-screen scanning view, launched by the "scan this" / "what is this" voice
 * command. Two things run at once on every camera frame:
 *
 *  1. ML Kit barcode + label detection (fully offline) -> draws HUD brackets
 *     around whatever was found, and if it's a barcode, looks the product up
 *     online (the one part of this screen that needs internet).
 *  2. MediaPipe hand landmarker (fully offline) -> tracks your index fingertip
 *     and draws the glowing Iron-Man-style trail as you point at things.
 */
class CameraActivity : AppCompatActivity() {

    private lateinit var overlay: ScanOverlayView
    private lateinit var statusText: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var tts: TextToSpeech
    private lateinit var lookupService: ProductLookupService

    private val barcodeScanner = BarcodeScanning.getClient()
    private val imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private var handLandmarker: HandLandmarker? = null

    private var lastSpokenCode: String? = null
    private var lookupInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        overlay = findViewById(R.id.scanOverlay)
        statusText = findViewById(R.id.scanStatus)
        cameraExecutor = Executors.newSingleThreadExecutor()
        lookupService = ProductLookupService(this)
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts.language = Locale.US }

        findViewById<android.view.View>(R.id.btnCloseScan).setOnClickListener { finish() }

        setupHandLandmarker()
        startCamera()
    }

    private fun setupHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task") // see README: drop the model file into app/src/main/assets
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setResultListener { result, _ ->
                    val landmarks = result.landmarks()
                    if (landmarks.isNotEmpty()) {
                        // Landmark 8 = index fingertip
                        val tip = landmarks[0][8]
                        runOnUiThread { overlay.pushFingertip(PointF(tip.x(), tip.y())) }
                    } else {
                        runOnUiThread { overlay.pushFingertip(null) }
                    }
                }
                .setErrorListener { }
                .build()
            handLandmarker = HandLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            // Model file missing - hand tracking is skipped but barcode/object scanning still works.
            statusText.text = "Fingertip tracking unavailable (missing hand_landmarker.task model — see README)."
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(findViewById<androidx.camera.view.PreviewView>(R.id.previewView).surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ::analyzeFrame) }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Couldn't start camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun analyzeFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) { imageProxy.close(); return }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes -> handleBarcodes(barcodes, imageProxy) }
            .addOnFailureListener { }

        imageLabeler.process(inputImage)
            .addOnSuccessListener { labels ->
                if (labels.isNotEmpty()) {
                    statusText.text = "See: ${labels.first().text} (${(labels.first().confidence * 100).toInt()}%)"
                }
            }

        // Fingertip tracking, offline, on the same frame
        handLandmarker?.let { landmarker ->
            YuvToBitmap.convert(imageProxy)?.let { bitmap ->
                val mpImage = BitmapImageBuilder(bitmap).build()
                landmarker.detectAsync(mpImage, System.currentTimeMillis())
            }
        }

        imageProxy.close()
    }

    private fun handleBarcodes(barcodes: List<Barcode>, imageProxy: ImageProxy) {
        val barcode = barcodes.firstOrNull() ?: run {
            overlay.setTarget(null, null)
            return
        }
        val box = barcode.boundingBox
        if (box != null) {
            val w = imageProxy.width.toFloat()
            val h = imageProxy.height.toFloat()
            overlay.setTarget(
                ScanOverlayView.NormRect(box.left / w, box.top / h, box.right / w, box.bottom / h),
                "SCANNING…"
            )
        }

        val code = barcode.rawValue ?: return
        if (code == lastSpokenCode || lookupInFlight) return
        lastSpokenCode = code
        lookupInFlight = true

        statusText.text = "Barcode found: $code — looking it up…"
        cameraExecutor.execute {
            if (!lookupService.isOnline()) {
                runOnUiThread {
                    statusText.text = "Barcode $code found, but you're offline — connect to the internet for product details."
                    speak("I found a barcode, but I need an internet connection to look up the product.")
                    overlay.setTarget(overlay.currentBox, "OFFLINE")
                    lookupInFlight = false
                }
                return@execute
            }
            val info = lookupService.lookupBarcode(code)
            runOnUiThread {
                if (info != null) {
                    val summary = buildString {
                        append(info.name)
                        if (!info.brand.isNullOrBlank()) append(" by ${info.brand}")
                    }
                    statusText.text = summary
                    overlay.setTarget(overlay.currentBox, info.name)
                    speak("This is $summary.")
                } else {
                    statusText.text = "Couldn't find product details for that barcode."
                    speak("I scanned the barcode but couldn't find product details for it.")
                }
                lookupInFlight = false
            }
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_scan")
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        handLandmarker?.close()
        tts.stop(); tts.shutdown()
        super.onDestroy()
    }
}
