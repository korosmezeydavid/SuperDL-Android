package com.superdl.launcher.crash

import android.content.Context
import android.util.Log

/**
 * INDULÁSI ŐR — védelem a végtelen újraindulási ciklus ellen.
 *
 * A LEGSÚLYOSABB FORGATÓKÖNYV, ami ellen véd:
 * A telefon elindul, a SuperDL betölt egy hibás állapotot, összeomlik. A
 * rendszer újraindítja a launchert, az megint ugyanazt tölti be, megint
 * összeomlik — és így tovább, VÉGTELENÜL. A felhasználó ilyenkor néma,
 * használhatatlan telefont kap, akár feloldás előtt. Vakon ebből nincs kiút:
 * nem tud launchert váltani vagy hibakeresni.
 *
 * MŰKÖDÉS:
 *  1. Induláskor feljegyezzük: "indulás elkezdődött".
 *  2. Ha az indulás SIKERES, töröljük a jelzést.
 *  3. Ha egymás után HÁROM indulás nem fejeződött be, a következő indulás
 *     BIZTONSÁGOS MÓDBAN történik: csak a túlélő mag, minden opcionális
 *     szolgáltatás kikapcsolva.
 *
 * FONTOS: a biztonságos mód NEM büntetés, hanem MENEKÜLÉSI ÚT. A telefon
 * használható marad, és a felhasználó egy menüponttal visszakapcsolhat
 * mindent, ha a hiba elmúlt.
 */
object StartupGuard {

    private const val TAG = "SDL_STARTUP"
    private const val PREFS = "superdl_startup"
    private const val KEY_INCOMPLETE = "incomplete_starts"
    private const val KEY_SAFE_MODE = "safe_mode"

    /** Ennyi félbemaradt indulás után kapcsolunk biztonságos módba. */
    private const val FAILURE_THRESHOLD = 3

    /** Ennyi idő után tekintjük az indulást sikeresnek. */
    const val SUCCESS_DELAY_MS = 8000L

    @Volatile
    private var safeModeActive = false

    /** Biztonságos módban futunk-e ÉPPEN? */
    val isSafeMode: Boolean get() = safeModeActive

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Az indulás KEZDETE. Ezt a lehető legelőbb kell hívni.
     * @return igaz, ha BIZTONSÁGOS MÓDBAN kell indulni
     */
    fun onStartupBegin(context: Context): Boolean {
        val p = prefs(context)
        val incomplete = p.getInt(KEY_INCOMPLETE, 0)
        val forcedSafe = p.getBoolean(KEY_SAFE_MODE, false)

        safeModeActive = forcedSafe || incomplete >= FAILURE_THRESHOLD

        // A számlálót MOST növeljük — ha összeomlunk, a következő induláskor
        // ez a magasabb érték fogad minket.
        p.edit().putInt(KEY_INCOMPLETE, incomplete + 1).apply()

        if (safeModeActive) {
            Log.w(TAG, "BIZTONSAGOS MOD: felbemaradt indulasok=$incomplete, kenyszeritett=$forcedSafe")
        } else {
            Log.i(TAG, "indulas kezdodik (elozo felbemaradt: $incomplete)")
        }
        return safeModeActive
    }

    /**
     * Az indulás SIKERES volt — a számláló nullázódik.
     * Késleltetve hívjuk, hogy a korai összeomlásokat is elkapjuk.
     */
    fun onStartupSuccess(context: Context) {
        try {
            prefs(context).edit().putInt(KEY_INCOMPLETE, 0).apply()
            Log.i(TAG, "indulas sikeres, szamlalo nullazva")
        } catch (_: Exception) {
        }
    }

    /** Biztonságos mód kézi be- vagy kikapcsolása a menüből. */
    fun setSafeMode(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_SAFE_MODE, enabled)
            .putInt(KEY_INCOMPLETE, 0)
            .apply()
        safeModeActive = enabled
    }

    /** Felolvasható állapot a menühöz. */
    fun speakStatus(context: Context): String {
        val forced = prefs(context).getBoolean(KEY_SAFE_MODE, false)
        return when {
            forced -> "Biztonságos mód bekapcsolva, kézzel. " +
                "Csak az alapfunkciók futnak. Kapcsold ki, ha minden rendben."
            safeModeActive -> "Biztonságos módban futok, mert a Super DL többször " +
                "hibába futott indulás közben. Az alapfunkciók működnek. " +
                "A visszakapcsolással megpróbálhatod a teljes indulást."
            else -> "Normál működés. Nincs félbemaradt indulás."
        }
    }
}
