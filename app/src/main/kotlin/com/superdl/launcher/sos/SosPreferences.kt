package com.superdl.launcher.sos

import android.content.Context

object SosPreferences {

    private const val PREFS_NAME = "superdl"
    private val SLOT_KEYS = arrayOf("sos_1", "sos_2", "sos_3", "sos_4")
    private const val KEY_COUNTDOWN = "sos_countdown_enabled"

    /**
     * VISSZASZÁMLÁLÁS A RIASZTÁS ELŐTT — ki- és bekapcsolható.
     *
     * MIÉRT VAN EGYÁLTALÁN: egy zsebben véletlenül indított S.O.S.-ből ne
     * legyen valódi riasztás. A visszaszámlálás alatt egy balra söpréssel
     * megállítható.
     *
     * MIÉRT LEHET KIKAPCSOLNI: aki tudatosan indít S.O.S.-t, annak a
     * másodpercek számítanak. Ez a felhasználó döntése, nem a miénk —
     * más-más helyzetben más a helyes.
     *
     * ALAPBÓL BE: aki még nem gondolta végig, azt inkább a véletlen
     * riasztástól védjük.
     */
    fun isCountdownEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_COUNTDOWN, true)

    fun setCountdownEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_COUNTDOWN, value).apply()
    }

    fun getNumber(context: Context, slot: Int): String {
        require(slot in 1..4) { "SOS slot must be 1..4" }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(SLOT_KEYS[slot - 1], "") ?: ""
    }

    fun getNumbers(context: Context): List<String> =
        (1..4).map { getNumber(context, it) }

    fun setNumber(context: Context, slot: Int, value: String) {
        require(slot in 1..4) { "SOS slot must be 1..4" }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SLOT_KEYS[slot - 1], value)
            .apply()
    }

    fun clearNumber(context: Context, slot: Int) = setNumber(context, slot, "")

    fun normalizeSpokenNumber(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.equals("töröl", ignoreCase = true) ||
            trimmed.equals("torol", ignoreCase = true) ||
            trimmed.equals("törlés", ignoreCase = true)
        ) {
            return ""
        }
        val digits = trimmed.filter { it.isDigit() || it == '+' }
        if (digits.isBlank()) return null
        return digits
    }
}