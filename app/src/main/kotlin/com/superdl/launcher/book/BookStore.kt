package com.superdl.launcher.book

import android.content.Context
import com.superdl.launcher.storage.JsonPrefsHelper
import org.json.JSONArray
import org.json.JSONObject

object BookStore {

    private const val PREFS = "superdl"
    private const val KEY_POSITIONS = "book_positions"
    private const val KEY_RECENT = "book_recent"
    private const val KEY_BOOKMARKS = "book_bookmarks"
    private const val KEY_CUSTOM_FOLDERS = "book_custom_folders"
    private const val KEY_POSITIONS_SCHEMA = "book_positions_schema"
    private const val KEY_RECENT_SCHEMA = "book_recent_schema"
    private const val KEY_BOOKMARKS_SCHEMA = "book_bookmarks_schema"
    private const val KEY_CUSTOM_FOLDERS_SCHEMA = "book_custom_folders_schema"
    // Hangoskönyv-folytatás: mappa-út -> {track, ms} (a szöveges char-pozíciótól
    // külön, mert a hang ezredmásodperc-alapú)
    private const val KEY_AUDIO_POS = "audiobook_positions"
    private const val KEY_AUDIO_POS_SCHEMA = "audiobook_positions_schema"
    private const val SCHEMA_VERSION = 1
    private const val MAX_RECENT = 20
    private const val MAX_BOOKMARKS = 100
    private const val MAX_CUSTOM_FOLDERS = 5

    fun getPosition(context: Context, bookPath: String): Int {
        val obj = readPositions(context)
        return obj.optInt(bookPath, 0)
    }

    fun savePosition(context: Context, bookPath: String, charOffset: Int) {
        val obj = readPositions(context)
        obj.put(bookPath, charOffset.coerceAtLeast(0))
        JsonPrefsHelper.saveJsonObject(
            context, PREFS, KEY_POSITIONS, KEY_POSITIONS_SCHEMA, SCHEMA_VERSION, obj
        )
        touchRecent(context, bookPath)
    }

    fun getRecentPaths(context: Context): List<String> {
        val list = mutableListOf<String>()
        val array = JsonPrefsHelper.readJsonArray(
            context, PREFS, KEY_RECENT, KEY_RECENT_SCHEMA, SCHEMA_VERSION
        )
        for (i in 0 until array.length()) {
            val path = array.optString(i, "")
            if (path.isNotBlank()) list.add(path)
        }
        return list
    }

    fun touchRecent(context: Context, bookPath: String) {
        val current = getRecentPaths(context).toMutableList()
        current.removeAll { it == bookPath }
        current.add(0, bookPath)
        while (current.size > MAX_RECENT) current.removeLast()
        val array = JSONArray()
        current.forEach { array.put(it) }
        JsonPrefsHelper.saveJsonArray(
            context, PREFS, KEY_RECENT, KEY_RECENT_SCHEMA, SCHEMA_VERSION, array
        )
    }

    fun getBookmarks(context: Context, bookPath: String? = null): List<BookBookmark> {
        val list = mutableListOf<BookBookmark>()
        val array = JsonPrefsHelper.readJsonArray(
            context, PREFS, KEY_BOOKMARKS, KEY_BOOKMARKS_SCHEMA, SCHEMA_VERSION
        )
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val path = obj.getString("bookPath")
            if (bookPath != null && path != bookPath) continue
            list.add(
                BookBookmark(
                    id = obj.getInt("id"),
                    bookPath = path,
                    bookTitle = obj.optString("bookTitle", ""),
                    charOffset = obj.getInt("charOffset"),
                    preview = obj.optString("preview", ""),
                    createdAt = obj.optLong("createdAt", 0L),
                    kind = obj.optString("kind", "text"),
                    posMs = obj.optInt("posMs", 0),
                    track = obj.optString("track", "")
                )
            )
        }
        return list.sortedByDescending { it.createdAt }
    }

    fun addBookmark(
        context: Context,
        bookPath: String,
        bookTitle: String,
        charOffset: Int,
        preview: String
    ): BookBookmark? {
        val bookmarks = getBookmarks(context).toMutableList()
        if (bookmarks.size >= MAX_BOOKMARKS) return null
        val nextId = (bookmarks.maxOfOrNull { it.id } ?: 0) + 1
        val entry = BookBookmark(
            id = nextId,
            bookPath = bookPath,
            bookTitle = bookTitle,
            charOffset = charOffset.coerceAtLeast(0),
            preview = preview.trim(),
            createdAt = System.currentTimeMillis()
        )
        bookmarks.add(entry)
        saveBookmarks(context, bookmarks)
        return entry
    }

    // ---- hangoskönyv: folytatási pozíció (ms) mappánként ----

    private fun readAudioPositions(context: Context): JSONObject =
        JsonPrefsHelper.readJsonObject(
            context, PREFS, KEY_AUDIO_POS, KEY_AUDIO_POS_SCHEMA, SCHEMA_VERSION
        )

    /** A hangoskönyv (mappa) hol tartok pozíciója: (sáv fájlneve, ms) vagy null. */
    fun getAudioResume(context: Context, bookPath: String): Pair<String, Int>? {
        val obj = readAudioPositions(context)
        val o = obj.optJSONObject(bookPath) ?: return null
        return o.optString("track", "") to o.optInt("ms", 0)
    }

    fun saveAudioResume(context: Context, bookPath: String, track: String, ms: Int) {
        val obj = readAudioPositions(context)
        obj.put(
            bookPath,
            JSONObject().put("track", track).put("ms", ms.coerceAtLeast(0))
        )
        JsonPrefsHelper.saveJsonObject(
            context, PREFS, KEY_AUDIO_POS, KEY_AUDIO_POS_SCHEMA, SCHEMA_VERSION, obj
        )
        touchRecent(context, bookPath)
    }

    /** Hang-könyvjelző (idő-alapú) létrehozása – a PC-vel közös szinkronhoz. */
    fun addAudioBookmark(
        context: Context,
        bookPath: String,
        bookTitle: String,
        track: String,
        posMs: Int,
        preview: String
    ): BookBookmark? {
        val bookmarks = getBookmarks(context).toMutableList()
        if (bookmarks.size >= MAX_BOOKMARKS) return null
        val nextId = (bookmarks.maxOfOrNull { it.id } ?: 0) + 1
        val entry = BookBookmark(
            id = nextId,
            bookPath = bookPath,
            bookTitle = bookTitle,
            charOffset = 0,
            preview = preview.trim(),
            createdAt = System.currentTimeMillis(),
            kind = "audio",
            posMs = posMs.coerceAtLeast(0),
            track = track
        )
        bookmarks.add(entry)
        saveBookmarks(context, bookmarks)
        return entry
    }

    fun deleteBookmark(context: Context, id: Int): BookBookmark? {
        val bookmarks = getBookmarks(context).toMutableList()
        val removed = bookmarks.firstOrNull { it.id == id } ?: return null
        bookmarks.removeAll { it.id == id }
        saveBookmarks(context, bookmarks)
        return removed
    }

    /**
     * Minden nyom eltüntetése egy könyvről: olvasási pozíció, "nem rég
     * olvasott" bejegyzés és az összes hozzá tartozó könyvjelző.
     *
     * A TÖRÖLT KÖNYV FÁJLJÁT NEM ez törli — azt a hívó teszi. Ez csak arra
     * való, hogy a program ne kínáljon fel többé egy már nem létező könyvet.
     */
    fun forgetBook(context: Context, bookPath: String) {
        // pozíció
        val positions = readPositions(context)
        if (positions.has(bookPath)) {
            positions.remove(bookPath)
            JsonPrefsHelper.saveJsonObject(
                context, PREFS, KEY_POSITIONS, KEY_POSITIONS_SCHEMA, SCHEMA_VERSION, positions
            )
        }
        // hangoskönyv-pozíció
        val audio = readAudioPositions(context)
        if (audio.has(bookPath)) {
            audio.remove(bookPath)
            JsonPrefsHelper.saveJsonObject(
                context, PREFS, KEY_AUDIO_POS, KEY_AUDIO_POS_SCHEMA, SCHEMA_VERSION, audio
            )
        }
        // nem rég olvasott
        val recent = getRecentPaths(context).filter { it != bookPath }
        val array = JSONArray()
        recent.forEach { array.put(it) }
        JsonPrefsHelper.saveJsonArray(
            context, PREFS, KEY_RECENT, KEY_RECENT_SCHEMA, SCHEMA_VERSION, array
        )
        // könyvjelzők
        val bookmarks = getBookmarks(context).filter { it.bookPath != bookPath }
        saveBookmarks(context, bookmarks)
    }

    /** A könyvjelzők nyers JSON-tömbje – a WiFi-portál GET /sync/bookmarks-hez. */
    fun bookmarksJsonArray(context: Context): JSONArray =
        JsonPrefsHelper.readJsonArray(
            context, PREFS, KEY_BOOKMARKS, KEY_BOOKMARKS_SCHEMA, SCHEMA_VERSION
        )

    /** Eszközfüggetlen könyv-kulcs: a fájlnév kisbetűsítve. */
    private fun baseName(path: String): String =
        path.substringAfterLast('/').substringAfterLast('\\').trim().lowercase()

    /**
     * Egy másik eszközről (PC) érkező könyvjelzők beolvasztása – a POST
     * /sync/bookmarks-hoz. Dedup a (fájlnév, createdAt) páron, mert az `id`
     * eszközönként más, és az abszolút út is. Ha a bejövő könyv fájlneve megvan
     * a telefonon (pozíció / recent / meglévő könyvjelző alapján), a bookPath a
     * VALÓDI telefon-útra igazul, így a jelző a helyes könyvhöz kerül. Az id-ket
     * újraszámozza, tiszteletben tartja a felső korlátot. Visszaad: hány ÚJ.
     */
    fun mergeBookmarks(context: Context, incoming: JSONArray): Int {
        val existing = getBookmarks(context).toMutableList()
        val have = existing
            .map { baseName(it.bookPath) to it.createdAt }.toMutableSet()

        // fájlnév -> valódi telefon-út (pozíciók, recent, meglévő könyvjelzők)
        val byName = HashMap<String, String>()
        val positions = readPositions(context)
        positions.keys().forEach { p -> byName.putIfAbsent(baseName(p), p) }
        getRecentPaths(context).forEach { p -> byName.putIfAbsent(baseName(p), p) }
        existing.forEach { b -> byName.putIfAbsent(baseName(b.bookPath), b.bookPath) }

        var nextId = (existing.maxOfOrNull { it.id } ?: 0) + 1
        var added = 0
        for (i in 0 until incoming.length()) {
            val obj = incoming.optJSONObject(i) ?: continue
            val inPath = obj.optString("bookPath", "")
            val base = baseName(inPath)
            if (base.isBlank()) continue
            val created = obj.optLong("createdAt", 0L)
            if (have.contains(base to created)) continue
            if (existing.size >= MAX_BOOKMARKS) break
            val realPath = byName[base] ?: inPath
            existing.add(
                BookBookmark(
                    id = nextId++,
                    bookPath = realPath,
                    bookTitle = obj.optString("bookTitle", ""),
                    charOffset = obj.optInt("charOffset", 0),
                    preview = obj.optString("preview", ""),
                    createdAt = if (created > 0) created else System.currentTimeMillis(),
                    kind = obj.optString("kind", "text"),
                    posMs = obj.optInt("posMs", 0),
                    track = obj.optString("track", "")
                )
            )
            have.add(base to created)
            added++
        }
        if (added > 0) saveBookmarks(context, existing)
        return added
    }

    fun getCustomFolders(context: Context): List<String> {
        val list = mutableListOf<String>()
        val array = JsonPrefsHelper.readJsonArray(
            context, PREFS, KEY_CUSTOM_FOLDERS, KEY_CUSTOM_FOLDERS_SCHEMA, SCHEMA_VERSION
        )
        for (i in 0 until array.length()) {
            val path = array.optString(i, "")
            if (path.isNotBlank()) list.add(path)
        }
        return list
    }

    fun addCustomFolder(context: Context, path: String): Boolean {
        val folders = getCustomFolders(context).toMutableList()
        if (path in folders) return true
        if (folders.size >= MAX_CUSTOM_FOLDERS) return false
        folders.add(path)
        saveCustomFolders(context, folders)
        return true
    }

    fun clearCustomFolders(context: Context) {
        saveCustomFolders(context, emptyList())
    }

    private fun readPositions(context: Context): JSONObject =
        JsonPrefsHelper.readJsonObject(
            context, PREFS, KEY_POSITIONS, KEY_POSITIONS_SCHEMA, SCHEMA_VERSION
        )

    private fun saveCustomFolders(context: Context, folders: List<String>) {
        val array = JSONArray()
        folders.forEach { array.put(it) }
        JsonPrefsHelper.saveJsonArray(
            context, PREFS, KEY_CUSTOM_FOLDERS, KEY_CUSTOM_FOLDERS_SCHEMA, SCHEMA_VERSION, array
        )
    }

    private fun saveBookmarks(context: Context, bookmarks: List<BookBookmark>) {
        val array = JSONArray()
        bookmarks.forEach { b ->
            array.put(JSONObject().apply {
                put("id", b.id)
                put("bookPath", b.bookPath)
                put("bookTitle", b.bookTitle)
                put("charOffset", b.charOffset)
                put("preview", b.preview)
                put("createdAt", b.createdAt)
                put("kind", b.kind)
                put("posMs", b.posMs)
                put("track", b.track)
            })
        }
        JsonPrefsHelper.saveJsonArray(
            context, PREFS, KEY_BOOKMARKS, KEY_BOOKMARKS_SCHEMA, SCHEMA_VERSION, array
        )
    }
}