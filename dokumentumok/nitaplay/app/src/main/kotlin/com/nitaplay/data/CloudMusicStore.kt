package com.nitaplay.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

object CloudMusicStore {
    private const val TAG = "NitaPlay.Cloud"
    private const val PREFS = "nitaplay_cloud"
    private const val KEY_TREE_URI = "tree_uri"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getFolder(context: Context): Uri? =
        prefs(context).getString(KEY_TREE_URI, null)?.let(Uri::parse)

    fun hasFolder(context: Context): Boolean = getFolder(context) != null

    fun saveFolder(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        prefs(context).edit().putString(KEY_TREE_URI, uri.toString()).apply()
        Log.i(TAG, "cloud folder saved: $uri")
        true
    } catch (e: Exception) {
        Log.w(TAG, "save folder failed: ${e.message}")
        false
    }

    fun clearFolder(context: Context) {
        getFolder(context)?.let { uri ->
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
        }
        prefs(context).edit().remove(KEY_TREE_URI).apply()
    }

    fun folderName(context: Context): String {
        val uri = getFolder(context) ?: return "nincs kiválasztva"
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "kiválasztott mappa" }
        } catch (_: Exception) {
            "kiválasztott mappa"
        }
    }
}
