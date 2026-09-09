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

    private const val KEY_ATTEMPTS = "attempt_counts"
    private const val KEY_RESULT = "last_result"
    private const val KEY_ELAPSED = "last_elapsed"

    private fun prefs(context: Context) =
        com.superdl.launcher.storage.SafePrefs.get(context.applicationContext, PREFS)

    // ---- próbálkozások száma tételenként ----

    /**
     * HÁNYSZOR PRÓBÁLTA MÁR MEGADNI — ÉS MIÉRT KELL EZT MEGJEGYEZNI.
     *
     * A varázsló egy alapvető tételt két sikertelen próbálkozás után enged
     * kihagyni. Ez a kijárat viszont csak akkor ér valamit, ha a számláló
     * TÚLÉLI a program leállítását.
     *
     * A HIBA, AMIT EZ JAVÍT (Xiaomi M2103K19G, Android 13, három egymás
     * utáni hibajelentés húsz perc alatt): a MIUI a háttérben futó
     * alkalmazásokat magától leállítja — ugyanezen a telefonon a
     * „korlátlan háttérfutás" engedélyt is VISSZAVONTA két jelentés között.
     * Minden leállítás után a memóriában tartott számláló nullázódott, tehát
     * a felhasználó soha nem érte el a kettőt, és a varázsló újra és újra
     * azt mondta neki, hogy ezt nem lehet kihagyni.
     *
     * Vakon, a kezdőképernyő helyén ragadva ez nem apró kényelmetlenség.
     */
    fun attemptCount(context: Context, id: String): Int = try {
        prefs(context).getInt(KEY_ATTEMPTS + "_" + id, 0)
    } catch (_: Exception) {
        0
    }

    fun noteAttempt(context: Context, id: String): Int {
        val next = attemptCount(context, id) + 1
        try {
            prefs(context).edit().putInt(KEY_ATTEMPTS + "_" + id, next).apply()
        } catch (_: Exception) {
        }
        return next
    }

    // ---- mi történt a rendszerképernyőn ----

    /**
     * A NÉMA VISSZAUTASÍTÁS MÉRÉSE.
     *
     * A HIBA, AMI EZT KIKÉNYSZERÍTETTE (szonye48, Xiaomi 24094RAD4G,
     * Android 16, 1.63.5): az „alapértelmezett üzenet alkalmazás" TIZENEGY
     * próbálkozás után is hiányzott. A naplóból viszont csak a szám látszott,
     * az nem, hogy MI történt közben. Két teljesen különböző dolog adja
     * ugyanazt a számot:
     *
     *   1. a rendszer ablaka MEGJELENT, és a felhasználó nemet mondott;
     *   2. az ablak MEG SEM JELENT — a rendszer azonnal, némán visszadobta.
     *
     * A kettőt csak az idő és az eredménykód különbözteti meg: ha a képernyő
     * fél másodpercen belül visszatér RESULT_CANCELED-del, ott senki nem
     * döntött semmiről, hanem a telefon nem engedte. Ez a szerepköröknél
     * ismert eset (lásd az asszisztens szerepkört a SetupRequirements-ben),
     * és a felhasználónak joga van megtudni, hogy nem ő rontotta el.
     *
     * Vakon ez a különbség minden: az egyik esetben újra kell próbálni, a
     * másikban a kézi út az egyetlen járható.
     */
    fun noteOutcome(context: Context, id: String, resultCode: Int, elapsedMs: Long) {
        try {
            prefs(context).edit()
                .putInt(KEY_RESULT + "_" + id, resultCode)
                .putLong(KEY_ELAPSED + "_" + id, elapsedMs)
                .apply()
        } catch (_: Exception) {
        }
    }

    /** Az utolsó próbálkozás eredménye emberi szóval — a hibajelentéshez. */
    fun lastOutcome(context: Context, id: String): String? = try {
        val elapsed = prefs(context).getLong(KEY_ELAPSED + "_" + id, -1L)
        if (elapsed < 0) {
            null
        } else {
            val result = prefs(context).getInt(KEY_RESULT + "_" + id, 0)
            val kod = when (result) {
                -1 -> "rendben"
                0 -> "megszakítva"
                else -> "eredménykód $result"
            }
            "$kod, $elapsed ezredmásodperc alatt" +
                if (silentRefusal(context, id)) " (a képernyő meg sem jelent)" else ""
        }
    } catch (_: Exception) {
        null
    }

    /**
     * NÉMÁN VISSZADOBTA-E A RENDSZER.
     *
     * A határ 900 ezredmásodperc. Ennyi idő alatt egy ember nem olvas el egy
     * kérdést és nem is válaszol rá — még látóként sem, felolvasóval pedig
     * végképp nem. Ha ennyin belül jött vissza megszakítással, akkor nem
     * döntés történt, hanem elutasítás.
     */
    fun silentRefusal(context: Context, id: String): Boolean = try {
        val elapsed = prefs(context).getLong(KEY_ELAPSED + "_" + id, -1L)
        val result = prefs(context).getInt(KEY_RESULT + "_" + id, 0)
        elapsed in 0..899 && result != -1
    } catch (_: Exception) {
        false
    }

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
