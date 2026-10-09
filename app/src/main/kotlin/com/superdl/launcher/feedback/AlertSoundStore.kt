package com.superdl.launcher.feedback

import android.content.Context
import android.net.Uri

object AlertSoundStore {

    private const val PREFS = "superdl"
    private const val KEY_PREFIX = "alert_sound_"
    private const val URI_PREFIX = "alert_sound_uri_"
    private const val TITLE_PREFIX = "alert_sound_title_"

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
            .remove(URI_PREFIX + category.name)
            .remove(TITLE_PREFIX + category.name)
            .apply()
    }

    fun getToneUri(context: Context, category: AlertSoundCategory): Uri? =
        com.superdl.launcher.storage.SafePrefs.get(context, PREFS)
            .getString(URI_PREFIX + category.name, null)?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    fun getToneTitle(context: Context, category: AlertSoundCategory): String? =
        com.superdl.launcher.storage.SafePrefs.get(context, PREFS)
            .getString(TITLE_PREFIX + category.name, null)?.takeIf { it.isNotBlank() }

    fun setTone(context: Context, category: AlertSoundCategory, uri: String, title: String) {
        com.superdl.launcher.storage.SafePrefs.get(context, PREFS).edit()
            .putString(URI_PREFIX + category.name, uri)
            .putString(TITLE_PREFIX + category.name, title)
            .apply()
    }

    fun speakSummary(context: Context, category: AlertSoundCategory): String =
        "${category.label}: ${getToneTitle(context, category) ?: getPreset(context, category).label}"

    private fun key(category: AlertSoundCategory): String = KEY_PREFIX + category.name
}
