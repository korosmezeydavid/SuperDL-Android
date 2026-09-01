package com.superdl.launcher.youtube

import android.content.Context

/**
 * TAKARÉKOS MÓD — kapcsoló és emlékezet.
 *
 * MIÉRT KÜLÖN KAPCSOLÓ, ÉS MIÉRT NEM ALAPBÓL BE:
 * A takarékos mód CSAK A HANGOT játssza. Egy vak felhasználónak ez az esetek
 * nagy részében pontosan az, amit akar — de nem mindig. Ha egy látó ismerőssel
 * néznek valamit, vagy ha a videót később meg akarja mutatni, a kép kell.
 * Ezért a felhasználó dönt, nem mi döntünk helyette.
 *
 * ALAPBÓL KI, mert a viselkedés-változást senki ne kapja meglepetésként:
 * aki eddig videót indított, az videót várjon. A bekapcsolás egy menüpont,
 * és a program elmondja, mit nyer vele.
 */
object YoutubeSaverPrefs {

    private const val PREFS = "superdl"
    private const val KEY_ENABLED = "youtube_saver_mode"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun toggle(context: Context): Boolean {
        val next = !isEnabled(context)
        prefs(context).edit().putBoolean(KEY_ENABLED, next).apply()
        return next
    }

    /** Amit a menüpont kimond — a HASZNOT is, nem csak az állapotot. */
    fun speakState(on: Boolean): String = if (on) {
        "Takarékos mód bekapcsolva. A YouTube mostantól csak a hangot tölti le: " +
            "töredék annyi adat, kevesebb akkumulátor, és a képernyő lezárása után " +
            "is tovább szól. Ha képet is akarsz, kapcsold ki."
    } else {
        "Takarékos mód kikapcsolva. A YouTube videóval együtt indul, " +
            "és a képernyő lezárásakor megáll."
    }
}
