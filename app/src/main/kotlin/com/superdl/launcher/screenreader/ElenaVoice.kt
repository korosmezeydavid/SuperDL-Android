package com.superdl.launcher.screenreader

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * ELENA TANÁRNŐ SAJÁT HANGJA.
 *
 * A fejlesztő felmondott hangfájlokat tesz a telefonra — így Elena nem a
 * gépi felolvasóval szólal meg, hanem VALÓDI hanggal. Ettől lesz "valaki",
 * nem pedig a rendszer egy újabb üzenete.
 *
 * MIÉRT FÁJLBÓL ÉS NEM BEÉPÍTVE: így ÚJ HANGOT BÁRMIKOR CSERÉLHETSZ,
 * anélkül hogy az alkalmazást újra kellene fordítani és telepíteni.
 * Egyszerűen felmásolod a fájlt, és legközelebb már az szól.
 *
 * HOVA KELL MÁSOLNI (a WiFi portálról vagy a géppel):
 *     Android/data/com.superdl.launcher.debug/files/elena/
 *
 * MILYEN FÁJLOK:
 *     start.wav   — biztató köszöntés a tanulás indításakor
 *     otos.wav    — a jeles vizsga jutalma
 *     egyes.wav   — a bukás kommentje
 *     ora_kesz.wav — (nem kötelező) egy óra teljesítésekor
 *
 * Ha egy fájl HIÁNYZIK, a program a szokásos felolvasót használja —
 * tehát a tanulás akkor is működik, ha még nincs felvéve semmi.
 */
object ElenaVoice {

    private const val TAG = "SDL_ELENA"

    private var player: MediaPlayer? = null

    /** A hangfájlok mappája a telefonon. */
    fun voiceDir(context: Context): File =
        File(context.getExternalFilesDir(null), "elena").apply { mkdirs() }

    fun hasClip(context: Context, name: String): Boolean =
        File(voiceDir(context), name).let { it.exists() && it.length() > 1000 }

    /**
     * Egy hangfájl lejátszása.
     * @return igaz, ha volt ilyen fájl és elindult
     */
    fun play(context: Context, name: String): Boolean {
        val file = File(voiceDir(context), name)
        if (!file.exists() || file.length() < 1000) return false
        return try {
            stop()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            Log.i(TAG, "Elena hangja: $name")
            true
        } catch (e: Exception) {
            Log.w(TAG, "hang lejatszas hiba ($name): ${e.message}")
            false
        }
    }

    fun stop() {
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    /** Milyen hangok vannak már felvéve — a beállításokban felolvasható. */
    fun speakStatus(context: Context): String {
        val expected = listOf(
            "start.wav" to "köszöntés",
            "ora_kesz.wav" to "óra teljesítve",
            "otos.wav" to "jeles",
            "negyes.wav" to "jó",
            "harmas.wav" to "közepes",
            "kettes.wav" to "elégséges",
            "egyes.wav" to "elégtelen"
        )
        val found = expected.filter { hasClip(context, it.first) }
        val missing = expected.filterNot { hasClip(context, it.first) }
        return if (found.isEmpty()) {
            "Elena tanárnőnek még nincs saját hangja. A hangfájlokat az " +
                "Android data, com.superdl.launcher, files, elena mappába kell másolni."
        } else {
            "Elena saját hangja megvan ehhez: ${found.joinToString(", ") { it.second }}. " +
                if (missing.isEmpty()) "Mind a hét felvétel kész."
                else "Még hiányzik: ${missing.joinToString(", ") { it.second }}."
        }
    }
}
