package com.superdl.launcher.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

/**
 * ZENÉK EGY VÁLASZTOTT MAPPÁBÓL — akár felhőből is.
 *
 * MŰKÖDÉS: a felhasználó EGYSZER kiválaszt egy mappát a telefon fájlválasztójában
 * (ott jelenik meg a Google Drive, a OneDrive, a Dropbox és a telefon saját
 * tárhelye is). A SuperDL ezután TARTÓSAN hozzáfér ehhez a mappához, és minden
 * indításkor ott keresi a zenéket — az almappákkal együtt.
 *
 * MIÉRT ÍGY, ÉS NEM KÖZVETLENÜL A GOOGLE DRIVE-BÓL:
 * A közvetlen Drive-kapcsolat külön fejlesztői hitelesítést és a Google
 * jóváhagyását igényelné, és CSAK a Drive-val működne. Ez a megoldás viszont
 * MINDEN olyan felhővel és tárhellyel működik, amit a telefon fájlválasztója
 * ismer — és nem kell hozzá jelszót megadni az alkalmazásban.
 *
 * ŐSZINTE KORLÁT: a felhős fájlok LETÖLTŐDNEK lejátszáskor, tehát internet
 * kell hozzá, és mobilneten adatforgalmat használ. Ezt a felhasználónak tudnia
 * kell, ezért meg is mondjuk.
 */
object CloudMusicStore {

    private const val TAG = "SDL_MUSIC"
    private const val PREFS = "superdl_cloud_music"
    private const val KEY_TREE_URI = "tree_uri"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A kiválasztott mappa, vagy null ha még nincs. */
    fun getFolder(context: Context): Uri? =
        prefs(context).getString(KEY_TREE_URI, null)?.let(Uri::parse)

    fun hasFolder(context: Context): Boolean = getFolder(context) != null

    /**
     * A kiválasztott mappa megjegyzése, TARTÓS hozzáféréssel.
     * A tartós engedély nélkül az alkalmazás újraindítása után elveszne.
     */
    fun saveFolder(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        prefs(context).edit().putString(KEY_TREE_URI, uri.toString()).apply()
        Log.i(TAG, "felhos zenemappa mentve: $uri")
        true
    } catch (e: Exception) {
        Log.w(TAG, "mappa mentes hiba: ${e.message}")
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

    /** A mappa emberi neve a felolvasáshoz. */
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
