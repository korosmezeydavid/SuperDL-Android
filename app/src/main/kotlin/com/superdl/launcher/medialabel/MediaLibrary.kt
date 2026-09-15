package com.superdl.launcher.medialabel

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EGY MENTETT FELVÉTEL — kép vagy videó, amit a Super DL készített.
 */
data class MediaItem(
    val uri: Uri,
    val fileName: String,
    val isVideo: Boolean,
    val takenAtMillis: Long,
    val durationMs: Long
) {
    private fun tipus(): String = if (isVideo) "videó" else "fénykép"

    private fun idopont(): String = try {
        SimpleDateFormat("MMMM d., HH:mm", Locale("hu")).format(Date(takenAtMillis))
    } catch (_: Exception) {
        ""
    }

    private fun hossz(): String = when {
        !isVideo || durationMs <= 0 -> ""
        durationMs < 60_000 -> ", ${durationMs / 1000} másodperc"
        else -> {
            val perc = durationMs / 60_000
            val mp = (durationMs % 60_000) / 1000
            if (mp == 0L) ", $perc perc" else ", $perc perc $mp másodperc"
        }
    }

    /**
     * A TARTALÉK BEMONDÁS — ha nincs hangcímke.
     *
     * Szándékosan NEM a fájlnevet mondja. A `SuperDL_20260915_120000.mp4`
     * felolvasva értelmezhetetlen; a típus és az időpont legalább fogódzó.
     */
    fun speakFallback(): String = "${tipus()}, ${idopont()}${hossz()}"
}

/**
 * A SUPER DL SAJÁT FELVÉTELEI.
 *
 * Csak a program által készített képeket és videókat listázza (`DCIM/SuperDL`),
 * nem a telefon teljes galériáját. Aki a saját felvételeit keresi, ne kelljen
 * több ezer idegen kép között lépkednie.
 */
object MediaLibrary {

    private const val TAG = "SDL_HANGCIMKE"

    /** Ennél többet vakon végiglépkedni értelmetlen. A legfrissebbek elöl. */
    private const val MAX_ITEMS = 300

    private const val FOLDER = "SuperDL"

    fun list(context: Context): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        out += query(context, video = false)
        out += query(context, video = true)
        return out.sortedByDescending { it.takenAtMillis }.take(MAX_ITEMS)
    }

    private fun query(context: Context, video: Boolean): List<MediaItem> {
        val collection = if (video) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        if (video) projection.add(MediaStore.Video.Media.DURATION)

        val out = mutableListOf<MediaItem>()
        try {
            context.contentResolver.query(
                collection,
                projection.toTypedArray(),
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val pathIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val durIdx = if (video) c.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
                while (c.moveToNext() && out.size < MAX_ITEMS) {
                    val name = c.getString(nameIdx) ?: continue
                    val relPath = if (pathIdx >= 0) c.getString(pathIdx).orEmpty() else ""
                    // A SAJÁT MAPPÁNK. Tartalékként a fájlnév előtagja is
                    // elfogadható — egyes gyártói galériák átrendezik a
                    // relatív útvonalat, a nevet viszont meghagyják.
                    if (!relPath.contains(FOLDER, true) && !name.startsWith("SuperDL_", true)) {
                        continue
                    }
                    val id = c.getLong(idIdx)
                    out.add(
                        MediaItem(
                            uri = android.content.ContentUris.withAppendedId(collection, id),
                            fileName = name,
                            isVideo = video,
                            takenAtMillis = c.getLong(dateIdx) * 1000L,
                            durationMs = if (durIdx >= 0) c.getLong(durIdx) else 0L
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "medialista hiba (${if (video) "video" else "kep"}): ${t.message}")
        }
        return out
    }
}
