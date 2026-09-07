package com.superdl.launcher.files

import android.content.Context
import android.os.Environment
import android.text.format.Formatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Egy elem a fájlkezelőben (mappa vagy fájl).
 */
data class FileItem(
    val file: File,
    val isParent: Boolean = false,
    /**
     * A MAPPA VÉGÉN ÁLLÓ "Menü" SOR. Nem fájl és nem mappa: innen nyílnak a
     * csoportos műveletek (kijelölés, teljes tartalom törlése, keresés).
     * MIÉRT A LISTA VÉGÉN: az eleje a "vissza a szülőbe" helye, és aki
     * végiglapozza a mappát, az úgyis ideér — aki meg siet, egy felfelé
     * söpréssel a lista végén azonnal itt van.
     */
    val isMenu: Boolean = false
) {
    val name: String get() = when {
        isParent -> "Vissza a szülő mappába"
        isMenu -> "Menü"
        else -> file.name
    }
    val isDirectory: Boolean get() = !isMenu && file.isDirectory

    /** Igaz, ha ez valódi fájl vagy mappa (nem navigációs sor). */
    val isReal: Boolean get() = !isParent && !isMenu

    /** Vak felhasználónak felolvasható előnézet: mi ez, mekkora, mikori. */
    fun speakPreview(context: Context): String {
        if (isParent) return name
        if (isMenu) return "Menü. Csoportos műveletek ebben a mappában."
        return if (isDirectory) {
            val count = lathatoElemszam(file)
            "$name, mappa, $count elem"
        } else {
            val size = Formatter.formatShortFileSize(context, file.length())
            val kind = FileKind.of(file).hungarianName
            "$name, $kind, $size"
        }
    }

    fun speakDetails(context: Context): String {
        val modified = SimpleDateFormat("yyyy. MMMM d. HH:mm", Locale("hu", "HU"))
            .format(Date(file.lastModified()))
        val size = if (isDirectory) {
            val count = lathatoElemszam(file)
            "$count elemet tartalmaz"
        } else {
            Formatter.formatShortFileSize(context, file.length())
        }
        return "$name. ${FileKind.of(file).hungarianName}. $size. Módosítva: $modified."
    }
}

/**
 * EGY MAPPA ELEMSZÁMA — UGYANÚGY SZÁMOLVA, AHOGY MAJD LISTÁZZUK.
 *
 * A HIBA, AMIT EZ JAVÍT (Péter, 2026-09-06): „érdekes, hogy egy-egy mappánál
 * azt írja, hogy van benne elem, mint a Movies, Music mappánál, aztán ha
 * megnyitom, akkor meg azt mondja, hogy üres."
 *
 * Két helyen, kétféleképpen számoltunk:
 *
 *   előnézet:  file.listFiles()?.size                        ← MINDENT
 *   listázás:  dir.listFiles()?.filterNot { it.isHidden }    ← a rejtetteket nem
 *
 * A Movies és a Music mappában jellemzően csak rejtett bejegyzések vannak
 * (`.thumbnails`, `.nomedia`), ezért az előnézet elemet ígért, a megnyitás
 * pedig ürességet talált.
 *
 * Vakon egy ilyen ellentmondás elbizonytalanít: a felhasználó azt hiszi,
 * ő rontott el valamit, vagy hogy a program nem találja a fájljait.
 *
 * Ezért MINDKÉT helyen ez az egyetlen függvény számol.
 */
private fun lathatoElemszam(dir: java.io.File): Int = try {
    dir.listFiles()?.count { !it.isHidden } ?: 0
} catch (_: Exception) {
    0
}

/**
 * TELJES FÁJLHOZZÁFÉRÉS — van-e, és ha nincs, hogyan kérjük.
 *
 * A BAJ, AMIT ORVOSOL: Android 11 óta egy alkalmazás a saját mappáin kívül
 * SEMMIT nem törölhet, nem helyezhet át és nem hozhat létre, ha nincs meg ez
 * a külön engedély. A fájlkezelő böngészni tudott — ezért úgy TŰNT, hogy
 * működik —, de a törlés, az áthelyezés és a tömörítés némán elhasalt.
 *
 * Vakon ez a legrosszabb hibafajta: a menüpont kimondja magát, lefut, és
 * utána semmi. A felhasználó azt hiszi, ő rontott el valamit.
 *
 * Ez az engedély NEM a szokásos "engedélyezed?" ablak: a rendszer egy külön
 * beállítás-oldalra visz. Ezért kell hozzá magyarázat, nem elég egy kérés.
 */
object StorageAccess {

    /** Megvan-e a teljes hozzáférés. Android 10 alatt mindig igen. */
    fun hasFullAccess(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                android.os.Environment.isExternalStorageManager()
            } catch (_: Exception) {
                false
            }
        } else {
            true
        }

    /** A beállítás-oldal megnyitása, ahol a felhasználó megadhatja. */
    fun openSettings(context: android.content.Context): Boolean = try {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            )
        } else {
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        // Egyes készülékeken a közvetlen oldal nem nyílik meg — akkor az
        // általános listát próbáljuk, ott is megtalálható.
        try {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Amit a felhasználó hall, ha hiányzik. Elmondja, MIÉRT és MI a teendő. */
    const val EXPLANATION =
        "Ehhez teljes fájlhozzáférés kell. Az Android 11 óta egy alkalmazás " +
            "csak a saját mappáiban törölhet és hozhat létre fájlt — enélkül a " +
            "fájlkezelő csak böngészni tud. Söpörj jobbra, és megnyitom a " +
            "beállítást: ott kapcsold be a Super DL-nél az összes fájl kezelését, " +
            "aztán gyere vissza."
}

/**
 * Fájltípusok, hogy a felolvasás magyarul és érthetően szóljon.
 */
enum class FileKind(val hungarianName: String, val extensions: Set<String>) {
    AUDIO("hangfájl", setOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "opus", "amr")),
    IMAGE("kép", setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")),
    VIDEO("videó", setOf("mp4", "mkv", "avi", "mov", "3gp", "webm")),
    DOCUMENT("dokumentum", setOf("pdf", "doc", "docx", "odt", "rtf", "epub", "mobi")),
    TEXT("szövegfájl", setOf("txt", "md", "log", "csv")),
    DATA("adatfájl", setOf("json", "xml", "opml", "html", "htm")),
    ARCHIVE("tömörített fájl", setOf("zip", "rar", "7z", "tar", "gz")),
    APP("alkalmazás", setOf("apk")),
    FOLDER("mappa", emptySet()),
    OTHER("fájl", emptySet());

    companion object {
        fun of(file: File): FileKind {
            if (file.isDirectory) return FOLDER
            val ext = file.extension.lowercase()
            return entries.firstOrNull { ext in it.extensions } ?: OTHER
        }
    }
}

/**
 * A fájlkezelő logikája: mappák bejárása, rendezés, műveletek.
 *
 * Vak-barát elvek:
 *  - a mappák előre kerülnek, utána a fájlok (így kiszámítható a lista)
 *  - ábécé szerint, ékezet-érzéketlenül
 *  - a rejtett fájlok alapból nem látszanak (zavarnának)
 *  - a lista élén mindig ott a "Vissza a szülő mappába", ha van hova
 */
object FileManagerHelper {

    /** A gyökér, ahonnan indulunk: a telefon fő tárhelye. */
    fun rootDir(): File = Environment.getExternalStorageDirectory()

    /**
     * A portál mappája: ide kerülnek a gépről feltöltött fájlok.
     * (A WifiPortalServer is ide ír.)
     */
    fun portalDir(): File {
        val dir = File(rootDir(), "SuperDL/Portal")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Szövegfájl tartalmának beolvasása (felolvasáshoz, jelszó-importhoz). */
    fun readTextFile(file: File, maxChars: Int = 20_000): String? = try {
        if (!file.exists() || !file.isFile) null else file.readText().take(maxChars)
    } catch (_: Exception) {
        null
    }

    /**
     * Keresés fájlnév alapján, rekurzívan az adott mappától lefelé.
     * Vak-barát: a találatokat felolvasható sorrendben adja, és korlátozzuk,
     * hogy ne fusson percekig a teljes tárhelyen.
     *
     * @param query amit keresünk (ékezet- és kis/nagybetű-érzéketlen)
     * @param maxResults ennyi találat után megáll
     * @param maxDepth ilyen mélyen megy le a mappákba
     */
    fun search(
        startDir: File,
        query: String,
        maxResults: Int = 50,
        maxDepth: Int = 6
    ): List<FileItem> {
        val needle = normalizeForSearch(query)
        if (needle.isBlank()) return emptyList()
        val results = mutableListOf<FileItem>()
        try {
            startDir.walkTopDown()
                .maxDepth(maxDepth)
                .filterNot { it.isHidden }
                .forEach { f ->
                    if (results.size >= maxResults) return@forEach
                    if (f == startDir) return@forEach
                    if (normalizeForSearch(f.name).contains(needle)) {
                        results.add(FileItem(f))
                    }
                }
        } catch (_: Exception) {
        }
        // Mappák előre, aztán ábécé – ugyanaz a kiszámítható rend, mint a listákban.
        return results.sortedWith(
            compareByDescending<FileItem> { it.isDirectory }
                .thenBy { it.name.lowercase(Locale("hu", "HU")) }
        )
    }

    /** Ékezet- és kisbetű-érzéketlen alak a kereséshez. */
    private fun normalizeForSearch(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(Locale("hu", "HU")), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .trim()

    /** Gyors elérésű helyek – ezekkel indul a fájlkezelő. */
    fun quickPlaces(): List<Pair<String, File>> {
        val root = rootDir()
        val places = mutableListOf<Pair<String, File>>()
        places.add("Fő tárhely" to root)
        listOf(
            "Letöltések" to Environment.DIRECTORY_DOWNLOADS,
            "Zene" to Environment.DIRECTORY_MUSIC,
            "Képek" to Environment.DIRECTORY_PICTURES,
            "Dokumentumok" to Environment.DIRECTORY_DOCUMENTS,
            "Videók" to Environment.DIRECTORY_MOVIES
        ).forEach { (label, type) ->
            val dir = Environment.getExternalStoragePublicDirectory(type)
            if (dir != null && dir.exists()) places.add(label to dir)
        }
        // A rádió- és diktafon-felvételek nyilvános helye.
        val recordings = File(root, "Recordings")
        if (recordings.exists()) places.add("Felvételek" to recordings)
        // A SuperDL saját mappája (ide kerülnek a portálon feltöltött fájlok)
        val superdl = File(root, "SuperDL")
        if (superdl.exists()) places.add("SuperDL mappa" to superdl)
        return places
    }

    /**
     * Egy mappa tartalma, vak-barát sorrendben.
     * @param includeParent tegyünk-e a lista élére "vissza a szülőbe" elemet
     */
    fun listDir(dir: File, includeParent: Boolean = true, includeMenu: Boolean = false): List<FileItem> {
        val items = mutableListOf<FileItem>()
        val parent = dir.parentFile
        if (includeParent && parent != null && parent.canRead() && dir != rootDir()) {
            items.add(FileItem(parent, isParent = true))
        }
        val children = try {
            dir.listFiles()?.filterNot { it.isHidden } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val sorted = children.sortedWith(
            compareByDescending<File> { it.isDirectory }
                .thenBy { it.name.lowercase(Locale("hu", "HU")) }
        )
        items.addAll(sorted.map { FileItem(it) })
        // A csoportos műveletek belépője — mindig a lista legvégén.
        if (includeMenu) items.add(FileItem(dir, isMenu = true))
        return items
    }

    /**
     * CÉLMAPPÁK a mozgatáshoz és másoláshoz — LAPOS listaként, nem fában.
     *
     * MIÉRT LAPOS: fában lépkedve vakon nehéz megmondani, hol tartasz, és
     * pont ott a legnagyobb a tévedés ára ("nem a Recordings, hanem a
     * Ringtones mappába tettem"). Egy lapos listában viszont minden sor
     * KIMONDJA a teljes helyét, tehát nincs mit félreérteni.
     *
     * A sorrend nem véletlen: elöl a mostani mappa és a gyakran használt
     * helyek, utána a többi — így a valószínű célok pár söprésre vannak.
     */
    fun destinationFolders(currentDir: File, maxDepth: Int = 3, maxResults: Int = 300): List<File> {
        val out = LinkedHashSet<File>()
        out.add(currentDir)
        quickPlaces().forEach { (_, dir) -> if (dir.isDirectory) out.add(dir) }
        val rest = sortedSetOf<String>()
        try {
            rootDir().walkTopDown()
                .maxDepth(maxDepth)
                .onEnter { dir ->
                    // Az Android/ mappába nincs értelme belépni: oda az
                    // Android 11 óta úgysem lehet írni.
                    !dir.isHidden && dir.name != "Android"
                }
                .filter { it.isDirectory && !it.isHidden }
                .forEach { d ->
                    if (rest.size < maxResults) rest.add(d.absolutePath)
                }
        } catch (_: Exception) {
        }
        rest.forEach { out.add(File(it)) }
        return out.filter { it.isDirectory && it.canRead() }.take(maxResults)
    }

    /** Egy mappa kimondható neve az útjával együtt ("Zene, ezen belül: Rádió"). */
    fun speakFolder(dir: File): String {
        val root = rootDir().absolutePath
        val rel = dir.absolutePath.removePrefix(root).trim('/')
        if (rel.isBlank()) return "Fő tárhely"
        val parts = rel.split('/')
        return if (parts.size == 1) parts[0]
        else "${parts.last()}, ezen belül: ${parts.dropLast(1).joinToString(", ")}"
    }

    /** Új mappa létrehozása. */
    fun createFolder(parent: File, name: String): Boolean = try {
        val clean = sanitizeName(name)
        if (clean.isBlank()) false else File(parent, clean).mkdirs()
    } catch (_: Exception) {
        false
    }

    /** Törlés (mappa esetén a teljes tartalommal). */
    fun delete(file: File): Boolean = try {
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    } catch (_: Exception) {
        false
    }

    /** Átnevezés. */
    fun rename(file: File, newName: String): Boolean = try {
        val clean = sanitizeName(newName)
        if (clean.isBlank()) false else file.renameTo(File(file.parentFile, clean))
    } catch (_: Exception) {
        false
    }

    /** Másolás egy célmappába. */
    fun copyTo(source: File, targetDir: File): Boolean = try {
        val target = uniqueTarget(targetDir, source.name)
        if (source.isDirectory) {
            source.copyRecursively(target, overwrite = false)
        } else {
            source.copyTo(target, overwrite = false)
            true
        }
    } catch (_: Exception) {
        false
    }

    /** Áthelyezés egy célmappába. */
    fun moveTo(source: File, targetDir: File): Boolean = try {
        val target = uniqueTarget(targetDir, source.name)
        if (source.renameTo(target)) {
            true
        } else {
            // Másik köteten a rename nem megy: másol + töröl.
            if (copyTo(source, targetDir)) delete(source) else false
        }
    } catch (_: Exception) {
        false
    }

    /** Ütközés esetén "név (2).kiterjesztés" alakot ad. */
    private fun uniqueTarget(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 2
        while (candidate.exists() && i < 1000) {
            val newName = if (ext.isBlank()) "$base ($i)" else "$base ($i).$ext"
            candidate = File(dir, newName)
            i++
        }
        return candidate
    }

    /** A fájlnévből kiszedi a tiltott karaktereket. */
    private fun sanitizeName(name: String): String =
        name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "").take(120)

    /** Egy mappa teljes mérete (rekurzívan) – felolvasáshoz. */
    fun folderSize(dir: File): Long = try {
        dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    } catch (_: Exception) {
        0L
    }

    /** Szabad hely a tárhelyen. */
    fun freeSpaceText(context: Context): String = try {
        val free = rootDir().freeSpace
        val total = rootDir().totalSpace
        "Szabad hely: ${Formatter.formatShortFileSize(context, free)} a " +
            "${Formatter.formatShortFileSize(context, total)}-ból."
    } catch (_: Exception) {
        ""
    }
}
