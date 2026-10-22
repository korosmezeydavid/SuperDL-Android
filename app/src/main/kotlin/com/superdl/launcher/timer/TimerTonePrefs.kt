package com.superdl.launcher.timer

import android.content.Context

/** A lejárati hang az időzítőktől független, közös beállítás. */
object TimerTonePrefs {
    private const val PREFS = "superdl_timer_tone"
    private const val KEY_URI = "uri"
    private const val KEY_TITLE = "title"

    fun getUri(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URI, null)?.takeIf { it.isNotBlank() }

    fun getTitle(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TITLE, null)?.takeIf { it.isNotBlank() }

    fun set(context: Context, uri: String?, title: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URI, uri)
            .putString(KEY_TITLE, title)
            .apply()
    }
}
