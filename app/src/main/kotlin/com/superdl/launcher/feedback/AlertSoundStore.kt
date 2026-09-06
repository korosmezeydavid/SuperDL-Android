package com.superdl.launcher.feedback

import android.content.Context

object AlertSoundStore {

    private const val PREFS = "superdl"
    private const val KEY_PREFIX = "alert_sound_"

    fun getPreset(context: Context, category: AlertSoundCategory): AlertSoundPreset {
        val raw = com.superdl.launcher.storage.SafePrefs.get(context, PREFS)
            .getString(key(category), null)
            ?: return category.defaultPreset
        return AlertSoundPreset.entries.firstOrNull { it.name == raw } ?: category.defaultPreset
    }

    fun setPreset(context: Context, category: AlertSoundCategory, preset: AlertSoundPreset) {
        com.superdl.launcher.storage.SafePrefs.get(context, PREFS)
            .edit()
            .putString(key(category), preset.name)
            .apply()
    }

    fun speakSummary(context: Context, category: AlertSoundCategory): String =
        "${category.label}: ${getPreset(context, category).label}"

    private fun key(category: AlertSoundCategory): String = KEY_PREFIX + category.name
}