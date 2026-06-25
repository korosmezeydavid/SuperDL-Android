package com.superdl.launcher.tts

import android.content.Context

object TtsEngineStore {

    private const val PREFS = "superdl"
    private const val KEY_ENGINE = "tts_engine_package"
    private const val KEY_VOICE = "tts_voice_name"

    fun getSelectedPackage(context: Context): String? {
        val pkg = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENGINE, null)
        return pkg?.takeIf { it.isNotBlank() }
    }

    fun getSelectedVoiceName(context: Context): String? {
        val voice = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_VOICE, null)
        return voice?.takeIf { it.isNotBlank() }
    }

    fun setSelectedPackage(context: Context, packageName: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENGINE, packageName.orEmpty())
            .apply()
    }

    fun setSelectedVoiceName(context: Context, voiceName: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VOICE, voiceName.orEmpty())
            .apply()
    }

    fun setSelection(context: Context, packageName: String?, voiceName: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENGINE, packageName.orEmpty())
            .putString(KEY_VOICE, voiceName.orEmpty())
            .apply()
    }
}