package com.superdl.launcher.security

import android.content.Context

object LockPinStore {

    private const val PREFS = "lock_pin_prefs"
    private const val KEY_ENABLED = "pin_lock_enabled"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_SALT = "pin_salt"

    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8

    fun isEnabled(context: Context): Boolean =
        com.superdl.launcher.storage.SafePrefs.get(context, PREFS)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        com.superdl.launcher.storage.SafePrefs.get(context, PREFS).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun hasPinSet(context: Context): Boolean {
        val prefs = com.superdl.launcher.storage.SafePrefs.get(context, PREFS)
        return !prefs.getString(KEY_HASH, null).isNullOrBlank() &&
            !prefs.getString(KEY_SALT, null).isNullOrBlank()
    }

    fun savePin(context: Context, pin: String) {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash(pin, salt)
        com.superdl.launcher.storage.SafePrefs.get(context, PREFS).edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_HASH, hash)
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = com.superdl.launcher.storage.SafePrefs.get(context, PREFS)
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val stored = prefs.getString(KEY_HASH, null) ?: return false
        return PinHasher.hash(pin, salt) == stored
    }

    fun clearPin(context: Context) {
        com.superdl.launcher.storage.SafePrefs.get(context, PREFS).edit()
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .putBoolean(KEY_ENABLED, false)
            .apply()
    }

    fun speakStatus(context: Context): String = when {
        !hasPinSet(context) -> "PIN zárolás nincs beállítva."
        isEnabled(context) -> "PIN zárolás bekapcsolva. A launcher feloldáshoz PIN kód szükséges."
        else -> "PIN zárolás kikapcsolva, de a PIN kód el van mentve."
    }
}