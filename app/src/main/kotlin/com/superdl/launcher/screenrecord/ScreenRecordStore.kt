package com.superdl.launcher.screenrecord

import android.content.Context
import java.io.File

/**
 * A KÉPERNYŐFELVÉTEL ÁLLAPOTA ÉS BEÁLLÍTÁSAI.
 *
 * MIÉRT VAN EZ A FUNKCIÓ (Alph, 2026-09-21): vak felhasználóként egy hibát
 * szavakkal leírni nehéz, sokszor lehetetlen. Egy videó, amin a képernyő,
 * a program beszéde ÉS a felhasználó saját kommentárja is rajta van,
 * nagyságrenddel többet ér bármilyen hibajelentésnél.
 */
object ScreenRecordStore {

    private const val PREFS = "superdl_kepernyofelvetel"

    private const val KEY_MIC = "mikrofon"
    private const val KEY_DEVICE = "telefon_hangja"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Beállítások ──────────────────────────────────────────────────────

    /** A felhasználó saját hangja. ALAPBÓL BE: ez a funkció fél értelme. */
    fun isMicEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MIC, true)

    fun setMicEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_MIC, value).apply()
    }

    /** Amit a telefon kiad. ALAPBÓL BE: ez a funkció másik fele. */
    fun isDeviceAudioEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEVICE, true)

    fun setDeviceAudioEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEVICE, value).apply()
    }

    // ── Élő állapot ──────────────────────────────────────────────────────

    /**
     * MIÉRT A MEMÓRIÁBAN: a felvétel csak addig él, amíg a program fut.
     * Egy lemezre mentett "épp felvesz" jelzés az újraindítás után hazudna.
     */
    @Volatile
    var isRecording: Boolean = false
        private set

    @Volatile
    var startedAtMillis: Long = 0L
        private set

    @Volatile
    var currentFile: File? = null
        private set

    /** Mi került ténylegesen a hangsávra — a leállításkori bemondáshoz. */
    @Volatile
    var micWasActive: Boolean = false
        private set

    @Volatile
    var deviceAudioWasActive: Boolean = false
        private set

    fun noteStarted(file: File, mic: Boolean, device: Boolean) {
        currentFile = file
        startedAtMillis = System.currentTimeMillis()
        micWasActive = mic
        deviceAudioWasActive = device
        isRecording = true
    }

    fun noteStopped() {
        isRecording = false
        startedAtMillis = 0L
        currentFile = null
    }

    fun elapsedMillis(): Long =
        if (startedAtMillis == 0L) 0L else System.currentTimeMillis() - startedAtMillis

    // ── Kimondható szövegek ──────────────────────────────────────────────

    fun speakElapsed(): String {
        val sec = (elapsedMillis() / 1000).toInt()
        val perc = sec / 60
        val mp = sec % 60
        return when {
            perc == 0 -> "$mp másodperc"
            mp == 0 -> "$perc perc"
            else -> "$perc perc $mp másodperc"
        }
    }

    fun speakSettings(context: Context): String {
        val mic = if (isMicEnabled(context)) "A saját hangod rákerül. " else "A saját hangod nem kerül rá. "
        val dev = if (isDeviceAudioEnabled(context)) {
            "A telefon hangja rákerül. "
        } else {
            "A telefon hangja nem kerül rá. "
        }
        return mic + dev
    }
}
