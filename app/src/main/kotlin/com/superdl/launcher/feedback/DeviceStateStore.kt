package com.superdl.launcher.feedback

import android.content.Context

object DeviceStateStore {

    private const val PREFS = "superdl"
    private const val KEY_ENABLED = "device_state_sounds_enabled"
    private const val KEY_FULL_ANNOUNCED = "battery_full_announced"

    fun isEnabled(context: Context): Boolean =
        com.superdl.launcher.storage.SafePrefs.get(context, PREFS)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        com.superdl.launcher.storage.SafePrefs.get(context, PREFS)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun isFullAnnounced(context: Context): Boolean =
        com.superdl.launcher.storage.SafePrefs.get(context, PREFS)
            .getBoolean(KEY_FULL_ANNOUNCED, false)

    fun setFullAnnounced(context: Context, announced: Boolean) {
        com.superdl.launcher.storage.SafePrefs.get(context, PREFS)
            .edit()
            .putBoolean(KEY_FULL_ANNOUNCED, announced)
            .apply()
    }
}