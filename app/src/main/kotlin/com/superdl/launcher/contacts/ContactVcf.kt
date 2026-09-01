package com.superdl.launcher.contacts

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NÉVJEGY MENTÉS ÉS VISSZATÖLTÉS — vCard (.vcf) fájlba.
 *
 * MIÉRT vCard: ez az a formátum, amit MINDEN telefon és minden levelezőprogram
 * ismer. Ha a felhasználó új telefont vesz, a mentett fájlt oda átmásolva a
 * névjegyei átjönnek — akkor is, ha az új telefonon nem SuperDL fut.
 *
 * MIÉRT A LETÖLTÉSEK MAPPA: oda a számítógép is lát, USB-kábellel átmásolható.
 * Ha nincs meg a teljes fájlhozzáférés, a program saját mappájába írunk —
 * az mindig működik, csak nehezebb megtalálni kívülről.
 *
 * SZÁNDÉKOSAN EGYSZERŰ a formátum: név és telefonszám. A program maga sem
 * tárol többet egy névjegyről, tehát nincs mit elveszíteni.
 */
object ContactVcf {

    private const val FOLDER = "SuperDL"

    /** Ahova mentünk, és ahol visszatöltéskor is keresünk. */
    fun exportDir(context: Context): File {
        val downloads = try {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                FOLDER
            )
        } catch (_: Exception) {
            null
        }
        if (downloads != null) {
            try {
                if (downloads.exists() || downloads.mkdirs()) return downloads
            } catch (_: Exception) {
            }
        }
        // Tartalék: a program saját mappája — ide mindig lehet írni.
        val own = File(context.getExternalFilesDir(null) ?: context.filesDir, FOLDER)
        if (!own.exists()) own.mkdirs()
        return own
    }

    /** A visszatöltéskor átnézett mappák (a legvalószínűbb helyek). */
    fun searchDirs(context: Context): List<File> {
        val list = mutableListOf<File>()
        list.add(exportDir(context))
        try {
            list.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        } catch (_: Exception) {
        }
        try {
            list.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
        } catch (_: Exception) {
        }
        context.getExternalFilesDir(null)?.let { list.add(it) }
        return list.filter { it.isDirectory }.distinctBy { it.absolutePath }
    }

    /** Minden .vcf fájl a keresett mappákban, a legfrissebb elöl. */
    fun findVcfFiles(context: Context): List<File> =
        searchDirs(context)
            .flatMap { dir ->
                dir.listFiles()?.filter {
                    it.isFile && it.name.lowercase(Locale.ROOT).endsWith(".vcf")
                } ?: emptyList()
            }
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }

    /**
     * A megadott névjegyek kiírása. Visszaad: a fájl, vagy null hiba esetén.
     */
    fun export(context: Context, contacts: List<ContactMatch>): File? {
        if (contacts.isEmpty()) return null
        return try {
            val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.getDefault()).format(Date())
            val file = File(exportDir(context), "nevjegyek-$stamp.vcf")
            val sb = StringBuilder()
            contacts.forEach { c ->
                sb.append("BEGIN:VCARD\r\n")
                sb.append("VERSION:3.0\r\n")
                sb.append("N:").append(escape(c.name)).append(";;;;\r\n")
                sb.append("FN:").append(escape(c.name)).append("\r\n")
                sb.append("TEL;TYPE=CELL:").append(c.phone.trim()).append("\r\n")
                sb.append("END:VCARD\r\n")
            }
            file.writeText(sb.toString(), Charsets.UTF_8)
            file
        } catch (_: Exception) {
            null
        }
    }

    /** Egy .vcf beolvasása (név, telefonszám) párokká. Hibás sorokat átugor. */
    fun parse(file: File): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        try {
            var name = ""
            var phone = ""
            file.forEachLine(Charsets.UTF_8) { raw ->
                val line = raw.trim()
                val upper = line.uppercase(Locale.ROOT)
                when {
                    upper.startsWith("BEGIN:VCARD") -> {
                        name = ""
                        phone = ""
                    }
                    upper.startsWith("FN") && line.contains(':') ->
                        name = unescape(line.substringAfter(':').trim())
                    // Az N: csak akkor kell, ha FN nem jött (egyes exportok
                    // csak ezt írják). A vezetéknév;keresztnév sorrendet
                    // megfordítjuk, hogy kimondva természetes legyen.
                    upper.startsWith("N:") && name.isBlank() -> {
                        val parts = line.substringAfter(':').split(';')
                        val last = unescape(parts.getOrNull(0)?.trim().orEmpty())
                        val first = unescape(parts.getOrNull(1)?.trim().orEmpty())
                        name = listOf(last, first).filter { it.isNotBlank() }.joinToString(" ")
                    }
                    upper.startsWith("TEL") && line.contains(':') && phone.isBlank() ->
                        phone = line.substringAfter(':').trim()
                    upper.startsWith("END:VCARD") -> {
                        if (name.isNotBlank() && phone.isNotBlank()) out.add(name to phone)
                        name = ""
                        phone = ""
                    }
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Hány névjegy van a fájlban — a megerősítés előtti bemondáshoz. */
    fun countIn(file: File): Int = parse(file).size

    /**
     * Visszatöltés a telefon névjegyzékébe. A MÁR MEGLÉVŐ számokat kihagyja,
     * hogy ne legyen minden visszatöltés után minden névjegy kétszer.
     * Visszaad: (hozzáadott, kihagyott).
     */
    fun import(context: Context, file: File): Pair<Int, Int> {
        val entries = parse(file)
        if (entries.isEmpty()) return 0 to 0
        val existing = ContactStore.getCached(context)
            .map { digitsTail(it.phone) }
            .filter { it.isNotBlank() }
            .toHashSet()
        var added = 0
        var skipped = 0
        entries.forEach { (name, phone) ->
            val tail = digitsTail(phone)
            if (tail.isNotBlank() && tail in existing) {
                skipped++
                return@forEach
            }
            if (ContactHelper.insertContact(context, name, phone)) {
                added++
                if (tail.isNotBlank()) existing.add(tail)
            } else {
                skipped++
            }
        }
        return added to skipped
    }

    /** Az összehasonlítás alapja: az utolsó 9 számjegy (körzetszám-független). */
    private fun digitsTail(phone: String): String =
        phone.filter { it.isDigit() }.takeLast(9)

    private fun escape(text: String): String =
        text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")

    private fun unescape(text: String): String =
        text.replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
