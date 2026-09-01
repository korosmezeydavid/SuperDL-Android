package com.superdl.launcher.dictaphone

import android.content.Context
import java.io.File

object DictaphoneLibrary {

    /**
     * A felvételek helye: /Recordings/Diktafon — nyilvános mappa, amit a
     * fájlkezelő és a számítógép is lát. (Régen a program saját mappájába
     * kerültek, ahova az Android 11 óta senki nem lát be.)
     */
    fun recordingsDir(context: Context): File =
        com.superdl.launcher.files.RecordingsDirs.dictaphone(context)

    fun createOutputFile(context: Context, format: DictaphoneFormat): File {
        val stamp = System.currentTimeMillis()
        return File(recordingsDir(context), "felvetel_$stamp.${format.extension}")
    }

    fun listRecordings(context: Context): List<DictaphoneRecordingEntry> {
        val dir = recordingsDir(context)
        return dir.listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.mapNotNull { file ->
                val format = DictaphoneFormat.entries.firstOrNull { file.name.endsWith(".${it.extension}") }
                    ?: return@mapNotNull null
                DictaphoneRecordingEntry(file, file.lastModified(), format)
            }
            ?.sortedByDescending { it.createdAtMillis }
            ?: emptyList()
    }

    fun delete(entry: DictaphoneRecordingEntry): Boolean = entry.file.delete()
}