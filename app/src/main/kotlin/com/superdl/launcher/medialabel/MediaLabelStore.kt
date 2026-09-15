package com.superdl.launcher.medialabel

import android.content.Context
import android.util.Log
import com.superdl.launcher.storage.JsonPrefsHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * EGY HANGCÍMKE — a saját hangoddal felmondott név egy képhez vagy videóhoz.
 *
 * @param fileName a média fájlneve (ez a kapocs, lásd a tárnál)
 * @param audioPath a felmondott címke hangfájlja
 * @param savedAt   mikor készült
 */
data class MediaLabel(
    val fileName: String,
    val audioPath: String,
    val savedAt: Long
) {
    val exists: Boolean
        get() = try {
            File(audioPath).let { it.exists() && it.length() > 0L }
        } catch (_: Exception) {
            false
        }
}

/**
 * HANGCÍMKÉK A KÉPEKHEZ ÉS VIDEÓKHOZ.
 *
 * MIÉRT VAN RÁ SZÜKSÉG: egy mentett felvétel neve `SuperDL_20260915_120000.mp4`.
 * Ez nem név, ez egy időbélyeg. Húsz ilyen után képtelenség megtalálni bármit,
 * a bélyegkép pedig — amiből a látók tájékozódnak — vakon semmit nem ér.
 *
 * A hangcímke ráadásul ABBAN A PILLANATBAN készül, amikor még tudod, mit
 * vettél fel — nem utólag, emlékezetből.
 *
 * MIÉRT A FÁJLNÉV A KAPOCS, ÉS NEM A MEDIASTORE-AZONOSÍTÓ: az azonosító
 * újraolvasáskor (gyári visszaállítás, kártyacsere, médiaszkennelés) változhat,
 * a fájlnév viszont a fájllal együtt utazik. A program a saját felvételeit
 * `SuperDL_<időbélyeg>` néven menti, ami egyedi.
 */
object MediaLabelStore {

    private const val TAG = "SDL_HANGCIMKE"
    private const val PREFS = "superdl"
    private const val KEY = "media_hangcimkek"
    private const val KEY_SCHEMA = "media_hangcimkek_schema"
    private const val SCHEMA_VERSION = 1

    fun get(context: Context, fileName: String): MediaLabel? =
        all(context).firstOrNull { it.fileName == fileName }?.takeIf { it.exists }

    fun has(context: Context, fileName: String): Boolean = get(context, fileName) != null

    fun all(context: Context): List<MediaLabel> = try {
        val array = JsonPrefsHelper.readJsonArray(
            context, PREFS, KEY, KEY_SCHEMA, SCHEMA_VERSION
        )
        val out = mutableListOf<MediaLabel>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val name = o.optString("file")
            val path = o.optString("audio")
            if (name.isBlank() || path.isBlank()) continue
            out.add(MediaLabel(name, path, o.optLong("savedAt")))
        }
        out
    } catch (t: Throwable) {
        Log.w(TAG, "olvasas hiba: ${t.message}")
        emptyList()
    }

    /** Címke mentése. A régit — ha volt — a lemezről is törli. */
    fun put(context: Context, fileName: String, audioPath: String) {
        val current = all(context).toMutableList()
        current.firstOrNull { it.fileName == fileName }?.let { regi ->
            if (regi.audioPath != audioPath) deleteFileQuietly(regi.audioPath)
            current.remove(regi)
        }
        current.add(MediaLabel(fileName, audioPath, System.currentTimeMillis()))
        save(context, current)
    }

    fun remove(context: Context, fileName: String) {
        val current = all(context).toMutableList()
        val regi = current.firstOrNull { it.fileName == fileName } ?: return
        deleteFileQuietly(regi.audioPath)
        current.remove(regi)
        save(context, current)
    }

    /**
     * TAKARÍTÁS: azok a bejegyzések, amikhez már nincs hangfájl, kiesnek.
     *
     * Enélkül a lista idővel olyan címkékkel telne meg, amiket nem lehet
     * lejátszani — és a felhasználó hiába keresné, mi történt velük.
     */
    fun prune(context: Context) {
        val current = all(context)
        val elo = current.filter { it.exists }
        if (elo.size != current.size) save(context, elo)
    }

    private fun deleteFileQuietly(path: String) {
        try {
            File(path).delete()
        } catch (_: Exception) {
        }
    }

    private fun save(context: Context, items: List<MediaLabel>) {
        val array = JSONArray()
        items.forEach { e ->
            array.put(JSONObject().apply {
                put("file", e.fileName)
                put("audio", e.audioPath)
                put("savedAt", e.savedAt)
            })
        }
        JsonPrefsHelper.saveJsonArray(context, PREFS, KEY, KEY_SCHEMA, SCHEMA_VERSION, array)
    }
}
