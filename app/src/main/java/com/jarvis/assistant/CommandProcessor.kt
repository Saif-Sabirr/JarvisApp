package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Parses a spoken sentence — in English or Urdu — and carries out the matching
 * phone action. Everything here is fully offline except product-lookup (in
 * CameraActivity), which is separate. Replies come back in whichever language
 * is currently selected via LanguageManager, regardless of which language the
 * command itself was spoken in.
 *
 * Urdu matching here is keyword-based rather than full grammar parsing — it
 * covers common phrasings but isn't exhaustive. Add more phrases to the
 * `contains(...)` lists below as you find gaps.
 */
class CommandProcessor(
    private val context: Context,
    private val onSpeak: (String) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var torchOn = false

    private fun lang() = LanguageManager.get(context)
    private fun reply(en: String, ur: String) = if (lang() == LanguageManager.Lang.URDU) ur else en

    private val jokesEn = listOf(
        "I would tell you a UDP joke, but you might not get it.",
        "There are 10 kinds of people: those who understand binary, and those who don't.",
        "Why do programmers prefer dark mode? Because light attracts bugs."
    )
    private val jokesUr = listOf(
        "میں نے کمپیوٹر کو بتایا کہ مجھے break چاہیے، اب وہ crash ہو گیا۔",
        "استاد نے پوچھا: زیرو کیا ہے؟ میں نے کہا: کچھ نہیں، بالکل میری copy کی طرح۔",
        "موبائل کی بیٹری اور میری قسمت میں ایک بات مشترک ہے — دونوں تیزی سے ختم ہوتی ہیں۔"
    )

    fun process(rawInput: String) {
        val cmd = rawInput.trim().lowercase()
        if (cmd.isEmpty()) return

        // ---- Scan / camera ----
        if (containsAny(cmd, "scan this", "what is this", "identify this", "اسکین کرو", "یہ کیا ہے", "پہچانو")) {
            return openScanner()
        }

        // ---- Call ----
        matchCall(cmd)?.let { return placeCall(it) }

        // ---- Text ----
        matchText(cmd)?.let { (name, message) -> return sendText(name, message) }

        // ---- Open app ----
        matchOpenApp(cmd)?.let { return openApp(it) }

        // ---- Flashlight ----
        if (containsAny(cmd, "turn on the flashlight", "turn on flashlight", "flashlight on", "torch on",
                "ٹارچ جلاؤ", "لائٹ جلاؤ", "فلیش لائٹ آن کرو")) return setFlashlight(true)
        if (containsAny(cmd, "turn off the flashlight", "turn off flashlight", "flashlight off", "torch off",
                "ٹارچ بند کرو", "لائٹ بند کرو", "فلیش لائٹ آف کرو")) return setFlashlight(false)

        // ---- Volume ----
        if (containsAny(cmd, "volume up", "louder", "increase volume", "آواز بڑھاؤ", "والیوم بڑھاؤ")) return adjustVolume(true)
        if (containsAny(cmd, "volume down", "quieter", "decrease volume", "آواز کم کرو", "والیوم کم کرو")) return adjustVolume(false)

        // ---- Time / date / battery ----
        if (containsAny(cmd, "what's the time", "what time is it", "current time", "وقت کیا ہے", "ٹائم کیا ہے")) {
            val t = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            return onSpeak(reply("It's $t.", "ابھی وقت ہے $t۔"))
        }
        if (containsAny(cmd, "what's the date", "today's date", "what day is it", "تاریخ کیا ہے", "آج کونسی تاریخ ہے")) {
            val d = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
            return onSpeak(reply("Today is $d.", "آج $d ہے۔"))
        }
        if (containsAny(cmd, "battery", "بیٹری")) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            return onSpeak(reply("Battery is at $pct percent.", "بیٹری $pct فیصد ہے۔"))
        }

        // ---- Joke ----
        if (containsAny(cmd, "tell me a joke", "say a joke", "make me laugh", "لطیفہ سناؤ", "کوئی لطیفہ")) {
            return onSpeak(if (lang() == LanguageManager.Lang.URDU) jokesUr.random() else jokesEn.random())
        }

        // ---- Capabilities ----
        if (containsAny(cmd, "what can you do", "your capabilities", "help me", "list commands",
                "تم کیا کر سکتے ہو", "مدد کرو")) {
            return onSpeak(reply(
                "I can call or text a contact, open apps, control the flashlight and volume, check the time, date, or battery, tell a joke, and scan a product or hand gesture with the camera.",
                "میں کسی کو کال یا میسج کر سکتا ہوں، ایپ کھول سکتا ہوں، ٹارچ اور آواز کنٹرول کر سکتا ہوں، وقت، تاریخ یا بیٹری بتا سکتا ہوں، لطیفہ سنا سکتا ہوں، اور کیمرے سے کوئی چیز اسکین کر سکتا ہوں۔"
            ))
        }

        // ---- Stop ----
        if (cmd == "stop" || cmd == "cancel" || cmd == "quiet" || cmd == "silence" ||
            cmd == "خاموش" || cmd == "رک جاؤ" || cmd == "بند کرو") {
            return onSpeak(reply("Stopped.", "رک گیا۔"))
        }

        onSpeak(reply(
            "I heard \"$rawInput\" but I don't have a command for that yet.",
            "میں نے سنا \"$rawInput\" لیکن ابھی اس کے لیے کوئی کمانڈ موجود نہیں ہے۔"
        ))
    }

    private fun containsAny(cmd: String, vararg needles: String) = needles.any { cmd.contains(it) }

    // ---- Bilingual phrase → target extraction ----

    private fun matchCall(cmd: String): String? {
        Regex("^(call|phone|dial) (.+)").find(cmd)?.let { return it.groupValues[2].trim() }
        if (cmd.contains("کال") || cmd.contains("فون")) {
            return cmd.replace("کال کرو", "").replace("کو کال کرو", "")
                .replace("فون کرو", "").replace("کو فون کرو", "")
                .replace("کال", "").replace("فون", "").replace("کو", "").trim()
                .takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun matchText(cmd: String): Pair<String, String>? {
        Regex("^(text|message|sms) (?:to )?(.+?)(?: saying | that says |: | telling (?:him|her|them) )(.+)")
            .find(cmd)?.let { return it.groupValues[2].trim() to it.groupValues[3].trim() }
        Regex("^(text|message|sms) (\\w+) (.+)").find(cmd)?.let {
            return it.groupValues[2].trim() to it.groupValues[3].trim()
        }
        // Urdu: "جان کو میسج کرو کہ میں آ رہا ہوں"
        if (cmd.contains("میسج") || cmd.contains("پیغام")) {
            val parts = cmd.split("کہ", limit = 2)
            if (parts.size == 2) {
                val name = parts[0].replace("کو میسج کرو", "").replace("کو پیغام بھیجو", "")
                    .replace("میسج", "").replace("پیغام", "").replace("کو", "").trim()
                val message = parts[1].trim()
                if (name.isNotBlank() && message.isNotBlank()) return name to message
            }
        }
        return null
    }

    private fun matchOpenApp(cmd: String): String? {
        Regex("^(open|launch|start) (.+)").find(cmd)?.let { return it.groupValues[2].trim() }
        if (cmd.contains("کھولو")) {
            return cmd.replace("کھولو", "").trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    // ---- Actions ----

    private fun openScanner() {
        onSpeak(reply("Opening the scanner.", "اسکینر کھول رہا ہوں۔"))
        val intent = Intent(context, CameraActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun placeCall(name: String) {
        val number = ContactHelper.findNumber(context, name)
        if (number == null) {
            onSpeak(reply("I couldn't find $name in your contacts.", "مجھے $name آپ کے رابطوں میں نہیں ملا۔"))
            return
        }
        if (context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            onSpeak(reply("I need call permission first.", "مجھے پہلے کال کی اجازت چاہیے۔"))
            return
        }
        onSpeak(reply("Calling $name.", "$name کو کال کر رہا ہوں۔"))
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun sendText(name: String, message: String) {
        val number = ContactHelper.findNumber(context, name)
        if (number == null) {
            onSpeak(reply("I couldn't find $name in your contacts.", "مجھے $name آپ کے رابطوں میں نہیں ملا۔"))
            return
        }
        if (context.checkSelfPermission(android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            onSpeak(reply("I need SMS permission first.", "مجھے پہلے میسج بھیجنے کی اجازت چاہیے۔"))
            return
        }
        try {
            val smsManager = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            smsManager.sendTextMessage(number, null, message, null, null)
            onSpeak(reply("Message sent to $name.", "$name کو پیغام بھیج دیا۔"))
        } catch (e: Exception) {
            onSpeak(reply("Sending the text to $name failed.", "$name کو پیغام بھیجنے میں ناکامی ہوئی۔"))
        }
    }

    private fun openApp(appName: String) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val match = apps.firstOrNull { pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true) }
        if (match == null) {
            onSpeak(reply("I couldn't find an app called $appName.", "مجھے $appName نامی ایپ نہیں ملی۔"))
            return
        }
        val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            onSpeak(reply("Opening ${pm.getApplicationLabel(match)}.", "${pm.getApplicationLabel(match)} کھول رہا ہوں۔"))
        } else {
            onSpeak(reply("I found $appName but couldn't launch it.", "$appName ملی لیکن کھل نہیں سکی۔"))
        }
    }

    private fun setFlashlight(on: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) {
                onSpeak(reply("This device doesn't have a flashlight.", "اس ڈیوائس میں ٹارچ موجود نہیں۔"))
                return
            }
            cameraManager.setTorchMode(cameraId, on)
            torchOn = on
            onSpeak(if (on) reply("Flashlight on.", "ٹارچ آن ہو گئی۔") else reply("Flashlight off.", "ٹارچ بند ہو گئی۔"))
        } catch (e: Exception) {
            onSpeak(reply("Couldn't control the flashlight.", "ٹارچ کنٹرول نہیں ہو سکی۔"))
        }
    }

    private fun adjustVolume(up: Boolean) {
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        onSpeak(if (up) reply("Volume up.", "آواز بڑھا دی۔") else reply("Volume down.", "آواز کم کر دی۔"))
    }
}
