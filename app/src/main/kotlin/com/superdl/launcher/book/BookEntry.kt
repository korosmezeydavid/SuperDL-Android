package com.superdl.launcher.book

import java.io.File
import java.util.Locale

/**
 * Egy könyv a könyvtárban.
 *
 * A `path` HÁROMFÉLE lehet, és ez a különbség a törlésnél számít:
 *  - valódi fájl útja (`/storage/emulated/0/Download/konyv.epub`),
 *  - hangoskönyv esetén egy MAPPA útja,
 *  - médiatárból származó bejegyzésnél `content://media/...` azonosító.
 *
 * A `realPath` a médiatáras bejegyzés mögötti valódi fájl útja, ha a rendszer
 * elárulja. Enélkül a `content://` bejegyzést nem lehetett törölni: a program
 * fájlként próbálta, az meg nem létező fájlnak látszott.
 */
data class BookEntry(
    val path: String,
    val title: String,
    val format: String,
    val sizeBytes: Long,
    val realPath: String? = null
) {
    fun speakPreview(): String {
        val sizeHint = when {
            sizeBytes < 1024 -> "egy kilobájtnál kisebb"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} kilobájt"
            else -> String.format(Locale.getDefault(), "%.1f megabájt", sizeBytes / (1024.0 * 1024.0))
        }
        return "$title. Formátum: ${format.uppercase()}. Méret: $sizeHint."
    }

    fun file(): File = File(path)

    fun stableId(): String = path
}