package com.nitaplay.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

object CloudMusicScanner {
    private const val TAG = "NitaPlay.CloudScan"
    private const val MAX_DEPTH = 6
    private const val MAX_TRACKS = 2000

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "wma", "mp4", "3gp"
    )

    fun scan(context: Context): List<Track> {
        val tree = CloudMusicStore.getFolder(context) ?: return emptyList()
        val out = mutableListOf<Track>()
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(tree)
            walk(context, tree, rootDocId, out, 0)
        } catch (e: Exception) {
            Log.w(TAG, "scan error: ${e.message}")
        }
        Log.i(TAG, "cloud tracks: ${out.size}")
        return out.sortedBy { it.title.lowercase() }
    }

    private fun walk(
        context: Context,
        tree: Uri,
        documentId: String,
        out: MutableList<Track>,
        depth: Int
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_TRACKS) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < MAX_TRACKS) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(context, tree, id, out, depth + 1)
                        continue
                    }
                    if (!isAudio(name, mime)) continue

                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    out.add(
                        Track(
                            id = id.hashCode().toLong() and 0x7FFFFFFFFFFFFFFFL,
                            title = name.substringBeforeLast('.'),
                            artist = "",
                            album = "",
                            durationMs = 0L,
                            artworkUri = null,
                            contentUri = fileUri,
                            source = MusicSource.CLOUD
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "walk error ($documentId): ${e.message}")
        }
    }

    private fun isAudio(name: String, mime: String): Boolean {
        if (mime.startsWith("audio/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }
}
