package com.superdl.launcher.book

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.InputStream

object BookLibrary {

    fun scan(context: Context): List<BookEntry> {
        val found = linkedMapOf<String, BookEntry>()
        scanAppFolders(context, found)
        scanPublicFolders(found)
        scanCustomFolders(context, found)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanMediaStore(context, found)
        }
        // Hangoskönyvek: a bejárt gyökerekben minden hangfájlos MAPPA egy könyv.
        AudiobookLibrary.collectInto(scanRoots(context), found)
        return found.values.sortedBy { it.title.lowercase() }
    }

    /** A bejárt gyökér-mappák (a hangoskönyv-felismeréshez is ezeket használjuk). */
    private fun scanRoots(context: Context): List<File> {
        val dirs = mutableListOf<File>()
        listOfNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            context.getExternalFilesDir(null)?.let { File(it, "Books") }
        ).forEach { dirs.add(it) }
        @Suppress("DEPRECATION")
        listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory("Books")
            // A ZENE MAPPA SZÁNDÉKOSAN KIMARADT.
            // Korábban itt szerepelt, ezért a zenei fájlok "könyvként" is
            // megjelentek a könyvtárban, és bekerültek a felolvasásba.
            // Aki mégis onnan olvasna, a "Könyvmappa beállítása" ponttal
            // bármikor hozzáadhatja.
        ).forEach { dirs.add(it) }
        BookStore.getCustomFolders(context).forEach { dirs.add(File(it)) }
        return dirs.filter { it.exists() && it.isDirectory }
    }

    private fun scanCustomFolders(context: Context, found: LinkedHashMap<String, BookEntry>) {
        BookStore.getCustomFolders(context).forEach { path ->
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) walkDir(dir, found)
        }
    }

    fun resolveRecent(context: Context): List<BookEntry> {
        val all = scan(context).associateBy { it.path }
        return BookStore.getRecentPaths(context)
            .mapNotNull { all[it] }
    }

    private fun scanAppFolders(context: Context, found: LinkedHashMap<String, BookEntry>) {
        val dirs = listOfNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            context.getExternalFilesDir(null)?.let { File(it, "Books") }
        )
        dirs.forEach { dir ->
            if (dir.exists()) walkDir(dir, found)
        }
    }

    private fun scanPublicFolders(found: LinkedHashMap<String, BookEntry>) {
        @Suppress("DEPRECATION")
        val roots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory("Books")
        )
        roots.forEach { dir ->
            if (dir.exists()) walkDir(dir, found)
        }
    }

    private fun walkDir(dir: File, found: LinkedHashMap<String, BookEntry>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                walkDir(file, found)
            } else if (BookTextExtractor.isSupported(file)) {
                addEntry(file, found)
            }
        }
    }

    private fun scanMediaStore(context: Context, found: LinkedHashMap<String, BookEntry>) {
        val resolver = context.contentResolver
        @Suppress("DEPRECATION")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA
        )
        val selection = buildString {
            append("(")
            BookTextExtractor.SUPPORTED_EXTENSIONS.forEachIndexed { index, _ ->
                if (index > 0) append(" OR ")
                append("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?")
            }
            append(")")
        }
        val args = BookTextExtractor.SUPPORTED_EXTENSIONS.map { "%.$it" }.toTypedArray()
        resolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            args,
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            @Suppress("DEPRECATION")
            val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol)
                val uri = MediaStore.Files.getContentUri("external", id)
                val path = uri.toString()
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in BookTextExtractor.SUPPORTED_EXTENSIONS) continue
                val real = if (dataCol >= 0) cursor.getString(dataCol)?.takeIf { it.isNotBlank() } else null
                // KETTŐZŐDÉS ELLEN: ha ugyanezt a fájlt a mappa-bejárás már
                // megtalálta, NEM vesszük fel másodszor. Eddig minden ilyen
                // könyv KÉTSZER szerepelt a listában (egyszer fájlként,
                // egyszer médiatár-azonosítóként), és a törlés csak az egyik
                // példányt tüntette el — kívülről ez úgy látszott, mintha a
                // törlés nem működne.
                if (real != null && found.containsKey(real)) continue
                found[path] = BookEntry(
                    path = path,
                    title = name.substringBeforeLast('.'),
                    format = ext,
                    sizeBytes = size,
                    realPath = real
                )
            }
        }
    }

    private fun addEntry(file: File, found: LinkedHashMap<String, BookEntry>) {
        found[file.absolutePath] = BookEntry(
            path = file.absolutePath,
            title = file.nameWithoutExtension,
            format = file.extension.lowercase(),
            sizeBytes = file.length()
        )
    }

    /**
     * EGY KÖNYV VÉGLEGES TÖRLÉSE.
     *
     * A könyvtár háromféle bejegyzést tartalmaz, és mindhármat máshogy kell
     * törölni. Korábban a program mindegyiket egyszerű fájlként próbálta, és
     * ezért a törlés a könyveknél — és CSAK a könyveknél — elhasalt:
     *
     *  1. Sima fájl: `File.delete()`. Ez működött.
     *  2. HANGOSKÖNYV: a `path` egy MAPPA. A `File.delete()` nem üres mappán
     *     mindig hamisat ad — a program pedig fájlhozzáférés hiányára
     *     panaszkodott, holott az engedéllyel semmi baj nem volt.
     *  3. MÉDIATÁRAS bejegyzés (`content://media/...`): fájlként megnyitva ez
     *     nem létező útvonal. A régi kód a „nem is létezik, tehát rendben"
     *     ágra futott, KIMONDTA hogy törölve, és a könyv a következő
     *     listázásnál újra ott volt.
     *
     * A visszatérési érték csak akkor igaz, ha a törlés TÉNYLEG megtörtént.
     */
    fun deleteBook(context: Context, entry: BookEntry): Boolean {
        var deleted = false
        val fsPath = entry.realPath ?: entry.path.takeUnless { it.startsWith("content://") }

        if (fsPath != null) {
            val target = File(fsPath)
            deleted = try {
                when {
                    !target.exists() -> true          // már nincs meg: a cél teljesült
                    target.isDirectory -> target.deleteRecursively() && !target.exists()
                    else -> target.delete() && !target.exists()
                }
            } catch (_: Exception) {
                false
            }
        }

        if (entry.path.startsWith("content://")) {
            val removed = try {
                context.contentResolver.delete(android.net.Uri.parse(entry.path), null, null) > 0
            } catch (_: Exception) {
                false
            }
            deleted = deleted || removed
        }

        // A médiatár tudjon róla, hogy a fájl megszűnt — különben a könyv
        // kísértetként ott marad a listában.
        if (deleted && fsPath != null) {
            try {
                android.media.MediaScannerConnection.scanFile(context, arrayOf(fsPath), null, null)
            } catch (_: Exception) {
            }
        }
        return deleted
    }

    fun openInputStream(context: Context, entry: BookEntry): InputStream? {
        return if (entry.path.startsWith("content://")) {
            context.contentResolver.openInputStream(android.net.Uri.parse(entry.path))
        } else {
            val file = entry.file()
            if (file.exists()) file.inputStream() else null
        }
    }

    fun materializeToCache(context: Context, entry: BookEntry): File? {
        if (!entry.path.startsWith("content://")) {
            val file = entry.file()
            return if (file.exists()) file else null
        }
        val cacheDir = File(context.cacheDir, "books").apply { mkdirs() }
        val safeName = entry.path.hashCode().toUInt().toString(16)
        val out = File(cacheDir, "$safeName.${entry.format}")
        if (out.exists() && out.length() == entry.sizeBytes) return out
        val input = openInputStream(context, entry) ?: return null
        input.use { src ->
            out.outputStream().use { dst -> src.copyTo(dst) }
        }
        return out
    }
}