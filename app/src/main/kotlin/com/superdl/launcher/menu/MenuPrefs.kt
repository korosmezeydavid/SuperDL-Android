package com.superdl.launcher.menu

import android.content.Context

/**
 * A MENÜ megjelenítési beállításai.
 */
object MenuPrefs {

    private const val PREFS = "superdl_menu"
    private const val KEY_SIMPLE = "simple_mode"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * EGYSZERŰ MÓD.
     *
     * ALAPBÓL BE VAN KAPCSOLVA az első indításkor: aki most ismerkedik a
     * telefonnal, ne háromszáz menüpontba fusson bele. Egyetlen ponttal
     * kinyitható a teljes készlet, és a döntés megmarad.
     */
    fun isSimpleMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SIMPLE, true)

    fun setSimpleMode(context: Context, simple: Boolean) {
        prefs(context).edit().putBoolean(KEY_SIMPLE, simple).apply()
    }

    fun toggle(context: Context): Boolean {
        val next = !isSimpleMode(context)
        setSimpleMode(context, next)
        return next
    }
}
