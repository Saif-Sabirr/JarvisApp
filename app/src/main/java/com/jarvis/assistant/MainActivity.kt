package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Home screen: request every permission Jarvis needs once, then hand off to
 * the floating OverlayService so the assistant is available on top of any app.
 */
class MainActivity : AppCompatActivity() {

    private val runtimePermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CAMERA
    ).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            it + Manifest.permission.POST_NOTIFICATIONS
        else it
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnGrantPermissions).setOnClickListener {
            requestRuntimePermissions()
        }
        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            requestOverlayPermission()
        }
        findViewById<Button>(R.id.btnLaunchOverlay).setOnClickListener {
            launchOverlay()
        }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val statusView = findViewById<TextView>(R.id.permissionStatus)
        val missing = runtimePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        val overlayGranted = Settings.canDrawOverlays(this)

        val sb = StringBuilder()
        sb.append(if (missing.isEmpty()) "✔ Voice, call, text, contacts, flashlight — granted\n"
                   else "✘ Still needs: ${missing.joinToString(", ") { it.substringAfterLast('.') }}\n")
        sb.append(if (overlayGranted) "✔ Floating overlay — granted" else "✘ Floating overlay — not granted yet")
        statusView.text = sb.toString()

        findViewById<Button>(R.id.btnLaunchOverlay).isEnabled = missing.isEmpty() && overlayGranted
    }

    private fun requestRuntimePermissions() {
        val missing = runtimePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        } else {
            Toast.makeText(this, "Already granted.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Toast.makeText(this, "Already granted.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchOverlay() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Jarvis is now floating — you can leave this screen.", Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }
}
