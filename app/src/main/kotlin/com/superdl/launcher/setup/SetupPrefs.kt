package com.superdl.launcher.setup

import android.content.Context

/**
 * A BEÁLLÍTÁS VARÁZSLÓ EMLÉKEZETE.
 *
 * Három dolgot tart nyilván, és mind a három azért kell, hogy a varázsló
 * ne legyen se hazug, se tolakodó:
 *
 * 1. LEFUTOTT-E MÁR. Friss telepítéskor a varázsló kötelező. Ha egyszer
 *    végigment rajta a felhasználó, többé nem ugrik elé magától.
 *
 * 2. MIT HAGYOTT KÉSŐBBRE. Ami nem alapvető, azt el lehet halasztani —
 *    de akkor ezt meg is jegyezzük, különben minden indításkor újra
 *    ugyanazt kérdeznénk. A menüből bármikor elővehető.
 *
 * 3. MIT ERŐSÍTETT MEG KÉZZEL. Van, amit a program NEM tud lekérdezni
 *    (a gyártói automatikus indítást például semmilyen API nem adja meg).
 *    Ott a felhasználó szava dönt — és ezt őszintén így is mondjuk.
 */
object SetupPrefs {

    private const val PREFS = "superdl_setup"
    private const val KEY_DONE = "wizard_done"
    private const val KEY_SKIPPED = "skipped_ids"
    private const val KEY_ACK = "acknowledged_ids"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Végigment-e már valaha a varázslón. */
    fun isWizardDone(context: Context): Boolean = try {
        prefs(context).getBoolean(KEY_DONE, false)
    } catch (_: Exception) {
        // Direct Boot: inkább NE erőltessük a varázslót, mint hogy összeomoljunk.
        true
    }

    fun setWizardDone(context: Context) {
        try {
            prefs(context).edit().putBoolean(KEY_DONE, true).apply()
        } catch (_: Exception) {
        }
    }

    /** Fejlesztéshez és teszteléshez: a varázsló újra kötelezővé tétele. */
    fun resetWizard(context: Context) {
        try {
            prefs(context).edit()
                .putBoolean(KEY_DONE, false)
                .remove(KEY_SKIPPED)
                .remove(KEY_ACK)
                .apply()
        } catch (_: Exception) {
        }
    }

    // ---- későbbre halasztott tételek ----

    fun isSkipped(context: Context, id: String): Boolean = id in read(context, KEY_SKIPPED)

    fun skip(context: Context, id: String) = add(context, KEY_SKIPPED, id)

    fun clearSkips(context: Context) {
        try {
            prefs(context).edit().remove(KEY_SKIPPED).apply()
        } catch (_: Exception) {
        }
    }

    // ---- kézzel megerősített tételek ----

    fun isAcknowledged(context: Context, id: String): Boolean = id in read(context, KEY_ACK)

    fun acknowledge(context: Context, id: String) = add(context, KEY_ACK, id)

    // ---- segédek ----

    private fun read(context: Context, key: String): Set<String> = try {
        prefs(context).getStringSet(key, emptySet()) ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }

    private fun add(context: Context, key: String, id: String) {
        try {
            val current = read(context, key).toMutableSet()
            current.add(id)
            prefs(context).edit().putStringSet(key, current).apply()
        } catch (_: Exception) {
        }
    }
}
