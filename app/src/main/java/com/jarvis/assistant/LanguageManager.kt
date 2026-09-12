package com.jarvis.assistant

import android.content.Context
import java.util.Locale

/**
 * Stores which language Jarvis should REPLY in (English or Urdu), and also
 * which language it should listen for, since Android's on-device recognizer
 * needs to be told a locale to transcribe accurately - it can't guess between
 * arbitrary languages on the fly. Switch the language with the EN/UR chip in
 * the floating panel; both recognition and text-to-speech follow it.
 */
object LanguageManager {
    private const val PREFS = "jarvis_prefs"
    private const val KEY_LANG = "reply_language"

    enum class Lang(val code: String, val locale: Locale, val label: String) {
        ENGLISH("en", Locale.US, "EN"),
        URDU("ur", Locale("ur", "PK"), "UR")
    }

    fun get(context: Context): Lang {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, "en")
        return if (saved == "ur") Lang.URDU else Lang.ENGLISH
    }

    fun set(context: Context, lang: Lang) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, lang.code).apply()
    }

    fun toggle(context: Context): Lang {
        val next = if (get(context) == Lang.ENGLISH) Lang.URDU else Lang.ENGLISH
        set(context, next)
        return next
    }
}
