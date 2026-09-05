package com.superdl.launcher.voicetheme

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.superdl.launcher.patrol.PatrolAnnouncer
import com.superdl.launcher.tts.TtsSettingsStore
import java.io.File

/**
 * A BESZÉDTÉMA LEJÁTSZÓJA.
 *
 * RÉTEGEK — és emiatt SOHA NINCS NÉMASÁG. Egy esemény hangja ebben a
 * sorrendben dől el:
 *
 *   1. SAJÁT FELVÉTEL (files/elena/) — ez mindig nyer. Ettől lehet egy
 *      letöltött téma mellett is az unokád hangja a „jó reggelt".
 *   2. AZ AKTÍV LETÖLTÖTT TÉMA (files/hangtemak/<téma>/).
 *   3. A BEÉPÍTETT MONDAT, felolvasóval.
 *
 * A téma MAPPA, nem szétszórt fájlok — mert később a csomagolás és a
 * megosztás erre épül. Ha most laposan tárolnánk, azt újra kellene írni.
 */
object VoiceThemePlayer {

    private const val TAG = "SDL_VOICETHEME"

    private var player: MediaPlayer? = null

    /** A saját felvételek mappája. Ugyanaz, amit az ElenaVoice használ. */
    fun ownDir(context: Context): File =
        File(context.getExternalFilesDir(null), "elena").apply { mkdirs() }

    /** A letöltött témák gyűjtőmappája. */
    fun themesRoot(context: Context): File =
        File(context.getExternalFilesDir(null), "hangtemak").apply { mkdirs() }

    /** A telepített témák mappanevei, ábécében. */
    fun installedThemes(context: Context): List<String> = try {
        themesRoot(context).listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sortedBy { it.lowercase() }
            ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Megkeresi az eseményhez tartozó hangfájlt a rétegsor szerint.
     * A kiterjesztést nem kötjük ki — aki telefonnal vesz fel, ne kelljen
     * konvertálnia.
     */
    fun resolve(context: Context, event: VoiceEvent): File? {
        findIn(ownDir(context), event)?.let { return it }
        val active = VoiceThemeStore.getActiveTheme(context)
        if (active.isNotBlank()) {
            findIn(File(themesRoot(context), active), event)?.let { return it }
        }
        // ELENA AZ ALAP, MINDEN ESETBEN.
        //
        // Egy megosztott téma nem köteles mind a hat hangot tartalmazni —
        // aki egy vicces „töltő kihúzva" mondatot vesz fel, attól nem várható
        // el, hogy a reggeli köszönést is felmondja. Eddig ilyenkor az adott
        // esemény visszaesett a felolvasott mondatra, és a téma közepén
        // váltott hangot a telefon.
        //
        // Ezért Elena a beszédtémák ALAPRÉTEGE: amit az aktív téma nem
        // mond ki, azt Elena mondja. Csak akkor marad a felolvasott mondat,
        // ha Elena hangja sincs meg (kicsomagolás előtt).
        if (active != VoiceThemeAssets.BUILT_IN_ID) {
            findIn(File(themesRoot(context), VoiceThemeAssets.BUILT_IN_ID), event)
                ?.let { return it }
        }
        return null
    }

    private fun findIn(dir: File, event: VoiceEvent): File? {
        if (!dir.isDirectory) return null
        for (ext in VoiceEvent.EXTENSIONS) {
            val file = File(dir, "${event.baseName}.$ext")
            // A pár száz bájtos fájl jellemzően félbemaradt másolás.
            if (file.exists() && file.length() > 1000) return file
        }
        return null
    }

    fun hasClip(context: Context, event: VoiceEvent): Boolean = resolve(context, event) != null

    /** Egy KONKRÉT témában van-e hang ehhez az eseményhez. */
    fun clipIn(context: Context, themeId: String, event: VoiceEvent): File? =
        findIn(File(themesRoot(context), themeId), event)

    /**
     * A BESZÉD CSATORNÁJA — ugyanaz, amin a program egyébként beszél.
     *
     * MIÉRT FONTOS: ha ez eltér, keletkezik egy olyan hang, amit a hangerő
     * gomb nem fog. Pontosan ezt a hibát javítottuk ki a beszédmotornál
     * 2026-09-05-én; ne építsük vissza egy másik ajtón.
     */
    private fun speechStream(context: Context): Int =
        if (TtsSettingsStore.getSpeechChannel(context) == TtsSettingsStore.CHANNEL_ACCESSIBILITY)
            AudioManager.STREAM_ACCESSIBILITY
        else
            AudioManager.STREAM_MUSIC

    /**
     * Egy klip lejátszása.
     * @param onDone akkor fut le, ha a hang véget ért VAGY el sem indult —
     *               tehát a rákövetkező mondat sosem marad el.
     */
    fun play(context: Context, file: File, onDone: () -> Unit = {}) {
        try {
            stop()
            val stream = speechStream(context)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            if (stream == AudioManager.STREAM_ACCESSIBILITY)
                                AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
                            else
                                AudioAttributes.USAGE_MEDIA
                        )
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stop()
                    onDone()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onDone()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "lejatszas hiba (${file.name}): ${e.message}")
            stop()
            onDone()
        }
    }

    fun stop() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    /**
     * A TELJES ÚT egy hangulati eseményhez: kapcsolók, napi keret, klip vagy
     * beépített mondat.
     *
     * @param extraText amit a klip UTÁN mindenképp ki kell mondani (pl. a
     *                  töltöttség). Ez az, ami az információt átmenti.
     * @return igaz, ha megszólaltunk
     */
    fun announce(
        context: Context,
        event: VoiceEvent,
        extraText: String = "",
        countsAgainstQuota: Boolean = true
    ): Boolean {
        if (!VoiceThemeStore.isEnabled(context)) return false
        if (!VoiceThemeStore.isEventEnabled(context, event)) return false
        if (countsAgainstQuota && !VoiceThemeStore.consumeQuota(context)) {
            Log.i(TAG, "napi keret betelt, ${event.id} kihagyva")
            return false
        }
        val clip = resolve(context, event)
        if (clip != null) {
            play(context, clip) {
                if (extraText.isNotBlank()) {
                    PatrolAnnouncer.announce(context, extraText, withBeep = false)
                }
            }
            return true
        }
        val text = listOf(event.fallback, extraText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (text.isBlank()) return false
        PatrolAnnouncer.announce(context, text, withBeep = false)
        return true
    }

    /** Mi van felvéve — a beállításokban felolvasható. */
    fun speakStatus(context: Context): String {
        val found = VoiceEvent.entries.filter { hasClip(context, it) }
        if (found.isEmpty()) {
            return "Egyik eseményhez sincs még felvett hang. Ilyenkor a program a " +
                "szokásos mondatokat mondja."
        }
        val missing = VoiceEvent.entries.filterNot { hasClip(context, it) }
        return "Felvett hang van ehhez: ${found.joinToString(", ") { it.label }}. " +
            if (missing.isEmpty()) "Mind a hat megvan."
            else "Még hiányzik: ${missing.joinToString(", ") { it.label }}."
    }
}
