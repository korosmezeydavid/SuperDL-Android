package com.superdl.launcher.screenrecord

import android.content.Context
import com.superdl.launcher.files.RecordingsDirs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Egy kész képernyőfelvétel. */
data class ScreenRecordEntry(
    val file: File,
    val createdAtMillis: Long,
    val sizeBytes: Long
) {
    fun speakLabel(): String {
        val ido = SimpleDateFormat("MMMM d., HH:mm", Locale("hu")).format(Date(createdAtMillis))
        val mb = sizeBytes / (1024.0 * 1024.0)
        val meret = if (mb < 1.0) {
            "${(sizeBytes / 1024)} kilobájt"
        } else {
            String.format(Locale("hu"), "%.1f megabájt", mb)
        }
        return "$ido, $meret"
    }
}

/**
 * A KÉSZ FELVÉTELEK LISTÁJA.
 *
 * A felvételek a /Movies/SuperDL mappába kerülnek, ahol a telefon
 * lejátszója, a galéria és a számítógép is megtalálja őket.
 */
object ScreenRecordLibrary {

    fun dir(context: Context): File = RecordingsDirs.screen(context)

    fun list(context: Context): List<ScreenRecordEntry> =
        dir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".mp4", true) && it.length() > 0L }
            ?.map { ScreenRecordEntry(it, it.lastModified(), it.length()) }
            ?.sortedByDescending { it.createdAtMillis }
            ?: emptyList()

    fun delete(entry: ScreenRecordEntry): Boolean = try {
        entry.file.delete()
    } catch (_: Exception) {
        false
    }

    fun speakLocation(context: Context): String =
        RecordingsDirs.speakPath(dir(context))
}
