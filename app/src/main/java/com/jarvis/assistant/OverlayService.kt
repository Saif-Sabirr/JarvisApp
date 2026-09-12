package com.jarvis.assistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView

/**
 * Draws a small floating arc-reactor icon that expands into the Jarvis HUD panel,
 * on top of whatever app is currently open. Once this service is running, Jarvis
 * listens continuously - there's no button to hold, you just speak and it acts.
 * Tap "PAUSE LISTENING" in the panel if you want it to stop listening for a while.
 */
class OverlayService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private var panelView: View? = null
    private var collapsedView: View? = null
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null
    private lateinit var commandProcessor: CommandProcessor
    private val mainHandler = Handler(Looper.getMainLooper())

    private var listeningEnabled = true   // user can pause/resume; on by default
    private var micIsActive = false       // whether SpeechRecognizer.startListening is currently live
    private var isSpeaking = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        commandProcessor = CommandProcessor(this) { response -> speak(response) }
        startForegroundNotification()
        showCollapsedOrb()
        setupRecognizer()
        // Start listening immediately - no button press needed.
        mainHandler.postDelayed({ restartListeningIfNeeded() }, 400)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) applyLanguageToTts()
    }

    private fun applyLanguageToTts() {
        val lang = LanguageManager.get(this)
        val result = tts.setLanguage(lang.locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Urdu voice data isn't installed on this device - fall back to English so
            // Jarvis still speaks something, and say so once.
            tts.language = java.util.Locale.US
            if (lang == LanguageManager.Lang.URDU) {
                speak("Urdu voice isn't installed on this device yet. Go to your phone's Text-to-speech settings to download it. Replying in English for now.")
            }
        }
    }

    private fun speak(text: String) {
        isSpeaking = true
        setOrbState(ArcReactorView.State.SPEAKING)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_overlay")
        mainHandler.postDelayed({
            isSpeaking = false
            setOrbState(if (micIsActive) ArcReactorView.State.LISTENING else ArcReactorView.State.IDLE)
        }, 900 + text.length * 45L) // rough estimate of speaking duration

        panelView?.findViewById<TextView>(R.id.logText)?.append("\n\nJARVIS: $text")
        panelView?.findViewById<ScrollView>(R.id.logScroll)?.post {
            panelView?.findViewById<ScrollView>(R.id.logScroll)?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun setOrbState(state: ArcReactorView.State) {
        (collapsedView as? ArcReactorView)?.setState(state)
        (panelView?.findViewById<View>(R.id.orb) as? ArcReactorView)?.setState(state)
    }

    // ---- Foreground notification (required to keep mic + overlay alive) ----

    private fun startForegroundNotification() {
        val channelId = "jarvis_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Jarvis Assistant", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Jarvis is listening")
            .setContentText("Always-on voice commands active. Tap the orb to open the panel.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        startForeground(42, notification)
    }

    // ---- Collapsed floating orb ----

    private fun showCollapsedOrb() {
        val inflater = LayoutInflater.from(this)
        collapsedView = inflater.inflate(R.layout.overlay_orb, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 300

        var lastX = 0; var lastY = 0
        var downX = 0f; var downY = 0f
        var moved = false

        collapsedView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = params.x; lastY = params.y
                    downX = event.rawX; downY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true
                    params.x = lastX + dx
                    params.y = lastY + dy
                    windowManager.updateViewLayout(collapsedView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) expandPanel()
                    true
                }
                else -> false
            }
        }
        windowManager.addView(collapsedView, params)
    }

    // ---- Expanded HUD panel docked to one side of the screen ----

    private fun expandPanel() {
        if (panelView != null) return
        collapsedView?.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        panelView = inflater.inflate(R.layout.overlay_panel, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.END or Gravity.CENTER_VERTICAL

        panelView?.findViewById<View>(R.id.btnClose)?.setOnClickListener { collapsePanel() }

        val langBtn = panelView?.findViewById<TextView>(R.id.btnLanguage)
        langBtn?.text = LanguageManager.get(this).label
        langBtn?.setOnClickListener {
            val newLang = LanguageManager.toggle(this)
            langBtn.text = newLang.label
            applyLanguageToTts()
            restartRecognizerForLanguage()
            speak(if (newLang == LanguageManager.Lang.URDU) "اردو منتخب کر لی گئی ہے۔" else "English selected.")
        }

        val micButton = panelView?.findViewById<android.widget.Button>(R.id.micButton)
        updateMicButtonLabel(micButton)
        micButton?.setOnClickListener {
            listeningEnabled = !listeningEnabled
            updateMicButtonLabel(micButton)
            if (listeningEnabled) restartListeningIfNeeded() else stopListening()
        }

        setOrbState(if (micIsActive) ArcReactorView.State.LISTENING else ArcReactorView.State.IDLE)
        windowManager.addView(panelView, params)
    }

    private fun updateMicButtonLabel(button: android.widget.Button?) {
        button?.text = if (listeningEnabled) "⏸ PAUSE LISTENING" else "▶ RESUME LISTENING"
    }

    private fun collapsePanel() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        collapsedView?.visibility = View.VISIBLE
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ---- Always-on voice recognition ----

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    micIsActive = true
                    if (!isSpeaking) setOrbState(ArcReactorView.State.LISTENING)
                }
                override fun onResults(results: Bundle?) {
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (heard.isNotBlank()) {
                        panelView?.findViewById<TextView>(R.id.transcript)?.text = heard
                        panelView?.findViewById<TextView>(R.id.logText)?.append("\n\nYOU: $heard")
                        commandProcessor.process(heard)
                    }
                    micIsActive = false
                    restartListeningIfNeeded(delayMs = 300)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        panelView?.findViewById<TextView>(R.id.transcript)?.text = partial
                    }
                }
                override fun onError(error: Int) {
                    micIsActive = false
                    // Keep the loop alive on timeouts/no-match - that's normal in always-on mode.
                    restartListeningIfNeeded(delayMs = 500)
                }
                override fun onEndOfSpeech() {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun restartListeningIfNeeded(delayMs: Long = 0) {
        if (!listeningEnabled || isSpeaking) return
        mainHandler.postDelayed({
            if (listeningEnabled && !isSpeaking) startListening()
        }, delayMs)
    }

    private fun startListening() {
        val lang = LanguageManager.get(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.locale)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            restartListeningIfNeeded(delayMs = 1000)
        }
    }

    private fun stopListening() {
        recognizer?.stopListening()
        micIsActive = false
        setOrbState(ArcReactorView.State.IDLE)
    }

    private fun restartRecognizerForLanguage() {
        recognizer?.cancel()
        micIsActive = false
        restartListeningIfNeeded(delayMs = 300)
    }

    override fun onDestroy() {
        collapsedView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
