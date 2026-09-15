package com.superdl.launcher.music

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.superdl.launcher.book.BookLibrary
import com.superdl.launcher.book.BookStore

object MusicHelper {

    /** Útvonal-részletek, amik egyértelműen könyvet jelentenek. */
    private val BOOK_HINTS = listOf(
        "/books/", "/book/", "/audiobook", "/hangoskonyv", "/hangoskönyv", "/konyvek/"
    )

    /**
     * A telefon ÉS a memóriakártya zenéit összegyűjti a MediaStore-ból
     * (az EXTERNAL_CONTENT_URI mindkét tárhelyet lefedi).
     *
     * Ha vannak zenék a "music" nevű mappákban (pl. /storage/.../Music vagy a
     * kártyán), azok kerülnek előre – a felhasználó a saját gyűjteményét
     * várja legelöl. A cím szerint, ékezet-érzéketlenül rendezve.
     *
     * NINCS DARABSZÁM-KORLÁT. Korábban 300 volt az alapérték, és a vágás a
     * RENDEZETT lekérdezés elején történt: akinek nyolcszáz száma volt, annak
     * a lejátszó némán csak az ábécé első háromszázát mutatta. A hiányzó
     * számokról semmilyen visszajelzés nem volt — a felhasználó azt hitte,
     * a telefon nem találja a zenéit. A paraméter megmaradt (hívható kisebb
     * értékkel), de az alapérték mostantól korlátlan.
     *
     * A HANGOSKÖNYVEK KIMARADNAK. Korábban minden hangfájl bekerült, így a
     * hangoskönyvek fejezetei a zenék közé keveredtek, és a zenelejátszó el is
     * indította őket. Mostantól kimarad, ami könyv-mappában van, amit a
     * rendszer hangoskönyvnek vagy podcastnak jelölt, és amit a könyvtár
     * hangoskönyv-mappaként ismer fel. A Zene mappa tartalmához nem nyúlunk:
     * ami ott van, az zene marad.
     */
    fun getTracks(context: Context, limit: Int = Int.MAX_VALUE): List<MusicTrack> {
        val bookFolders = bookFolderPrefixes(context)
        val musicRoot = try {
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                ?.absolutePath?.lowercase()?.trimEnd('/')?.plus("/").orEmpty()
        } catch (_: Throwable) {
            ""
        }
        val all = mutableListOf<Pair<MusicTrack, String>>() // track + relatív útvonal (rendezéshez)
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.IS_MUSIC
        )
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            append(" AND ${MediaStore.Audio.Media.DURATION} > 30000")
            // Amit a rendszer maga jelölt meg: podcast mindig, hangoskönyv
            // Android 10-től (a régebbi adatbázisban nincs ilyen oszlop).
            //
            // AZ "IS NULL" RÉSZ NEM DÍSZ. Ahol az oszlop üres (márpedig
            // előfordul), ott a puszta "= 0" HAMIS lenne, és a szám némán
            // kimaradna a listából — pont az a hiba, amit egyszer már
            // orvosoltunk a darabszám-korláttal.
            append(" AND (${MediaStore.Audio.Media.IS_PODCAST} IS NULL")
            append(" OR ${MediaStore.Audio.Media.IS_PODCAST} = 0)")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                append(" AND (${MediaStore.Audio.Media.IS_AUDIOBOOK} IS NULL")
                append(" OR ${MediaStore.Audio.Media.IS_AUDIOBOOK} = 0)")
            }
        }
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        queryTracks(context, collection, projection, selection, sort)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val titleIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val durationIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext() && all.size < limit) {
                val id = cursor.getLong(idIdx)
                val title = cursor.getString(titleIdx)?.trim().orEmpty()
                if (title.isBlank()) continue
                val path = if (dataIdx >= 0) cursor.getString(dataIdx)?.lowercase().orEmpty() else ""
                if (isBookMaterial(path, bookFolders, musicRoot)) continue
                val uri = Uri.withAppendedPath(collection, id.toString())
                val track = MusicTrack(
                    id = id,
                    title = title,
                    artist = cursor.getString(artistIdx)?.trim().orEmpty(),
                    durationMs = cursor.getLong(durationIdx),
                    contentUri = uri
                )
                all.add(track to path)
            }
        }

        // A "music" mappában lévő zenék előre; azon belül cím szerint.
        return all
            .sortedWith(
                compareByDescending<Pair<MusicTrack, String>> { it.second.contains("/music/") }
                    .thenBy { it.first.title.lowercase() }
            )
            .map { it.first }
    }

    /**
     * A lekérdezés úgy, hogy egy ismeretlen oszlop se tudja NÉMÁN kiüríteni a
     * zenelistát: ha a szűkített feltétel valamiért nem megy, jön a régi,
     * egyszerű feltétel — inkább legyen több a listában, mint semmi.
     */
    private fun queryTracks(
        context: Context,
        collection: Uri,
        projection: Array<String>,
        selection: String,
        sort: String
    ): android.database.Cursor? = try {
        context.contentResolver.query(collection, projection, selection, null, sort)
    } catch (_: Throwable) {
        try {
            val fallback =
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
            context.contentResolver.query(collection, projection, fallback, null, sort)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * A könyvnek számító mappák (kisbetűs, perjellel záruló előtagok):
     * a felhasználó saját könyvmappái, a nyilvános Könyvek mappa, és minden
     * mappa, amit a könyvtár hangoskönyvként ismer fel.
     */
    private fun bookFolderPrefixes(context: Context): List<String> {
        // A mappa-felismerés fájlrendszert jár be, a zenelista pedig a fő
        // szálon épül: egy percig megjegyezzük az eredményt, hogy a lista
        // megnyitása és a folytatás ne járja be kétszer ugyanazt.
        val now = System.currentTimeMillis()
        val cached = bookFolderCache
        if (cached != null && now - bookFolderCachedAt < BOOK_FOLDER_CACHE_MS) return cached
        val fresh = computeBookFolderPrefixes(context)
        bookFolderCache = fresh
        bookFolderCachedAt = now
        return fresh
    }

    @Volatile
    private var bookFolderCache: List<String>? = null

    @Volatile
    private var bookFolderCachedAt: Long = 0L

    private const val BOOK_FOLDER_CACHE_MS = 60_000L

    private fun computeBookFolderPrefixes(context: Context): List<String> {
        val out = mutableListOf<String>()
        try {
            BookStore.getCustomFolders(context).forEach { out.add(it) }
        } catch (_: Throwable) {
        }
        try {
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory("Books")?.let { out.add(it.absolutePath) }
        } catch (_: Throwable) {
        }
        try {
            BookLibrary.audiobookFolderPaths(context).forEach { out.add(it) }
        } catch (_: Throwable) {
        }
        return out
            .map { it.lowercase().trimEnd('/') + "/" }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Könyv-anyag-e a fájl. A Zene mappa MINDIG erősebb: ha valaki a Zene
     * mappát vette fel könyvmappának is, a zenéi akkor sem tűnhetnek el.
     */
    private fun isBookMaterial(
        path: String,
        bookFolders: List<String>,
        musicRoot: String
    ): Boolean {
        if (path.isBlank()) return false
        if (musicRoot.isNotEmpty() && path.startsWith(musicRoot)) return false
        if (bookFolders.any { path.startsWith(it) }) return true
        return BOOK_HINTS.any { path.contains(it) }
    }
}
