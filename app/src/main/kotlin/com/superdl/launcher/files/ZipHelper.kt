package com.superdl.launcher.files

import android.util.Log
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * TÖMÖRÍTÉS ÉS KICSOMAGOLÁS — a fájlkezelőbe.
 *
 * MIÉRT KELL: a tömörített fájl a mindennapok része. Ha valaki e-mailben
 * küld egy zipet, vagy a portálról tölt le egyet, azt eddig a telefonon
 * megnyitni sem lehetett — külső alkalmazás kellett hozzá, ami friss
 * telepítésnél nincs. Kifelé ugyanez: több fájlt egyben elküldeni csak úgy
 * lehet, ha előbb egybe csomagoljuk.
 *
 * A ZIP-et a Java maga tudja, könyvtár nélkül. RAR és 7z NEM megy — azokhoz
 * külön kódoló kellene; ezt ki is mondjuk, nem hallgatunk róla.
 *
 * ── BIZTONSÁG: A "ZIP SLIP" ────────────────────────────────────────────────
 *
 * Egy tömörített fájlban a bejegyzés neve lehet ilyen is: `../../valami`.
 * Ha vakon kicsomagolnánk, a fájl a célmappán KÍVÜLRE kerülne — akár egy
 * másik alkalmazás adatai közé. Ezt minden kicsomagolásnál ellenőrizzük:
 * ami nem a célmappán belülre mutat, azt kihagyjuk.
 *
 * Ez nem elméleti óvatosság: pontosan ezzel a trükkel törtek fel évekig
 * alkalmazásokat. Egy vak felhasználó pedig nem fogja észrevenni, hogy a
 * csomag mit tett a telefonjával.
 */
object ZipHelper {

    private const val TAG = "SDL_ZIP"

    /** Ennél nagyobb kicsomagolt méretnél megállunk (2 GB). */
    private const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024

    data class Result(
        val ok: Boolean,
        /** Emberi nyelvű mondat, ami felolvasható. */
        val message: String,
        val target: File? = null
    )

    fun isZip(file: File): Boolean = file.extension.equals("zip", ignoreCase = true)

    /** RAR és 7z: felismerjük, de nem tudjuk kezelni — és ezt megmondjuk. */
    fun isUnsupportedArchive(file: File): Boolean =
        file.extension.lowercase() in setOf("rar", "7z", "tar", "gz", "bz2", "xz")

    // ── KICSOMAGOLÁS ───────────────────────────────────────────────────────

    /**
     * Kicsomagolás a fájl mellé, a fájl nevével megegyező mappába.
     *
     * MIÉRT SAJÁT MAPPÁBA: sok zip a gyökerében tucatnyi fájlt tartalmaz. Ha
     * oda csomagolnánk, ahol a zip van, a felhasználó mappája egy csapásra
     * áttekinthetetlenné válna — és vakon ez sokkal nehezebben helyrehozható,
     * mint látva.
     */
    fun extract(zip: File): Result {
        if (!zip.exists()) return Result(false, "Ez a fájl nem érhető el.")
        if (isUnsupportedArchive(zip)) {
            return Result(
                false,
                "Ezt a tömörítést nem tudom kibontani. Csak a zip megy; " +
                    "a rar és a hét zip formátumhoz külön program kell."
            )
        }
        if (!isZip(zip)) return Result(false, "Ez nem tömörített fájl.")

        val targetDir = uniqueDir(zip.parentFile ?: return Result(false, "Nincs hova kicsomagolni."), zip.nameWithoutExtension)

        var count = 0
        var totalBytes = 0L
        try {
            if (!targetDir.mkdirs() && !targetDir.isDirectory) {
                return Result(false, "A célmappát nem tudtam létrehozni.")
            }
            val canonicalTarget = targetDir.canonicalPath

            ZipFile(zip).use { archive ->
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    val entry: ZipEntry = entries.nextElement()
                    val outFile = File(targetDir, entry.name)

                    // ── A ZIP SLIP ELLENŐRZÉS ──────────────────────────────
                    if (!outFile.canonicalPath.startsWith(canonicalTarget + File.separator) &&
                        outFile.canonicalPath != canonicalTarget
                    ) {
                        Log.w(TAG, "gyanus bejegyzes kihagyva: ${entry.name}")
                        continue
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                        continue
                    }
                    outFile.parentFile?.mkdirs()

                    archive.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                totalBytes += read
                                if (totalBytes > MAX_TOTAL_BYTES) {
                                    throw IllegalStateException("tul nagy")
                                }
                            }
                        }
                    }
                    count++
                }
            }
        } catch (e: IllegalStateException) {
            return Result(false, "Ez a csomag túl nagy a kicsomagoláshoz.", targetDir)
        } catch (e: Exception) {
            Log.w(TAG, "kicsomagolas hiba: ${e.message}")
            return Result(
                false,
                "A kicsomagolás nem sikerült. Lehet, hogy a fájl sérült, " +
                    "vagy jelszóval védett."
            )
        }

        if (count == 0) {
            return Result(false, "A csomag üres volt, vagy nem tartalmazott kibontható fájlt.", targetDir)
        }
        return Result(
            true,
            "Kicsomagolva: $count fájl, ide: ${targetDir.name}.",
            targetDir
        )
    }

    // ── TÖMÖRÍTÉS ──────────────────────────────────────────────────────────

    /**
     * Egy fájl vagy egy egész mappa becsomagolása, mellé.
     *
     * Mappánál az egész tartalom bekerül, a szerkezetével együtt.
     */
    fun compress(source: File): Result {
        if (!source.exists()) return Result(false, "Ez a fájl nem érhető el.")
        val parent = source.parentFile ?: return Result(false, "Nincs hova tömöríteni.")
        val zipFile = uniqueFile(parent, source.nameWithoutExtension, "zip")

        var count = 0
        try {
            ZipOutputStream(zipFile.outputStream().buffered()).use { out ->
                if (source.isDirectory) {
                    count = addDir(out, source, source.name)
                } else {
                    addFile(out, source, source.name)
                    count = 1
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "tomorites hiba: ${e.message}")
            try {
                zipFile.delete()
            } catch (_: Exception) {
            }
            return Result(false, "A tömörítés nem sikerült.")
        }

        val sizeMb = zipFile.length() / (1024.0 * 1024.0)
        val sizeText = if (sizeMb < 1) {
            "${zipFile.length() / 1024} kilobájt"
        } else {
            String.format(java.util.Locale.getDefault(), "%.1f megabájt", sizeMb)
        }
        return Result(
            true,
            "Kész: ${zipFile.name}. $count fájl, $sizeText.",
            zipFile
        )
    }

    private fun addDir(out: ZipOutputStream, dir: File, prefix: String): Int {
        var count = 0
        val children = try {
            dir.listFiles()
        } catch (_: Exception) {
            null
        } ?: return 0
        for (child in children) {
            val name = "$prefix/${child.name}"
            if (child.isDirectory) {
                count += addDir(out, child, name)
            } else {
                addFile(out, child, name)
                count++
            }
        }
        return count
    }

    private fun addFile(out: ZipOutputStream, file: File, name: String) {
        out.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(out, 64 * 1024) }
        out.closeEntry()
    }

    // ── Névütközés elkerülése ──────────────────────────────────────────────
    //
    // MIÉRT KELL: ha egy zip kétszer kerül kicsomagolásra, a második NEM
    // írhatja felül az elsőt. Vakon egy csendben felülírt mappa észrevétlen
    // adatvesztés — a "2" a név végén viszont hallható.

    private fun uniqueDir(parent: File, baseName: String): File {
        var candidate = File(parent, baseName)
        var i = 2
        while (candidate.exists()) {
            candidate = File(parent, "$baseName $i")
            i++
        }
        return candidate
    }

    private fun uniqueFile(parent: File, baseName: String, ext: String): File {
        var candidate = File(parent, "$baseName.$ext")
        var i = 2
        while (candidate.exists()) {
            candidate = File(parent, "$baseName $i.$ext")
            i++
        }
        return candidate
    }

    /**
     * MI VAN A CSOMAGBAN — kicsomagolás ELŐTT.
     *
     * MIÉRT: mielőtt valaki százhúsz fájlt kiszór a mappájába, jó, ha tudja,
     * mire számítson. Látva ez egy pillantás; vakon ez egy menüpont.
     */
    fun describe(zip: File): String {
        if (isUnsupportedArchive(zip)) {
            return "${zip.name}. Ezt a tömörítést nem tudom kibontani, csak a zipet."
        }
        return try {
            var files = 0
            var dirs = 0
            var uncompressed = 0L
            ZipInputStream(zip.inputStream().buffered()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.isDirectory) dirs++ else {
                        files++
                        if (entry.size > 0) uncompressed += entry.size
                    }
                    input.closeEntry()
                }
            }
            val sizeText = if (uncompressed > 0) {
                val mb = uncompressed / (1024.0 * 1024.0)
                if (mb < 1) ", kibontva ${uncompressed / 1024} kilobájt"
                else String.format(java.util.Locale.getDefault(), ", kibontva %.1f megabájt", mb)
            } else ""
            "${zip.name}. $files fájl${if (dirs > 0) ", $dirs mappa" else ""}$sizeText."
        } catch (e: Exception) {
            "${zip.name}. A tartalmát nem tudtam megnézni — lehet, hogy sérült vagy jelszóval védett."
        }
    }
}
