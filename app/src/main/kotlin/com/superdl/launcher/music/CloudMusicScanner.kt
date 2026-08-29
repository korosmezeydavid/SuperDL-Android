package com.superdl.launcher.music

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * A választott mappa (és almappái) végigjárása zenefájlokért.
 *
 * A bejárás REKURZÍV: ha a Zenék mappában előadónkénti almappák vannak, azokat
 * is végignézzük — pont ahogy a felhasználó várja.
 */
object CloudMusicScanner {

    private const val TAG = "SDL_MUSIC"

    /** Ennél mélyebbre nem megyünk (védelem a végtelen szerkezet ellen). */
    private const val MAX_DEPTH = 6

    /** Ennél több számot nem gyűjtünk (a felolvasás így is kezelhető marad). */
    private const val MAX_TRACKS = 2000

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "wma", "mp4", "3gp"
    )

    /**
     * A mappa zenefájljai. HÁTTÉRSZÁLRÓL hívandó — felhős mappánál ez
     * hálózati műveletet is jelenthet.
     */
    fun scan(context: Context): List<MusicTrack> {
        val tree = CloudMusicStore.getFolder(context) ?: return emptyList()
        val out = mutableListOf<MusicTrack>()
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(tree)
            walk(context, tree, rootDocId, out, 0)
        } catch (e: Exception) {
            Log.w(TAG, "felhos mappa olvasas hiba: ${e.message}")
        }
        Log.i(TAG, "felhos zenek: ${out.size} szam")
        // Névsorba rendezve, hogy a lépkedés kiszámítható legyen.
        return out.sortedBy { it.title.lowercase() }
    }

    private fun walk(
        context: Context,
        tree: Uri,
        documentId: String,
        out: MutableList<MusicTrack>,
        depth: Int
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_TRACKS) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < MAX_TRACKS) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        // ALMAPPA: bemegyünk.
                        walk(context, tree, id, out, depth + 1)
                        continue
                    }
                    if (!isAudio(name, mime)) continue

                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    out.add(
                        MusicTrack(
                            // A felhős fájloknak nincs számszerű azonosítójuk,
                            // ezért a saját azonosítójukból képzünk egyet.
                            id = id.hashCode().toLong(),
                            title = name.substringBeforeLast('.'),
                            artist = "",
                            durationMs = 0L,
                            contentUri = fileUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "mappa bejaras hiba ($documentId): ${e.message}")
        }
    }

    private fun isAudio(name: String, mime: String): Boolean {
        if (mime.startsWith("audio/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }
}
