package com.superdl.launcher.files

import android.content.Context
import android.provider.MediaStore
import java.io.File

/**
 * Összegyűjti a telefonon a SuperDL által készített MÉDIÁT a WiFi portál
 * "Fotók és hangok" oldalához: hangfelvételeket (diktafon, hangjegyzetek,
 * rádió-felvételek) az app saját mappáiból, és a kamerával készült fotókat a
 * MediaStore-ból (a rendszer galériájából).
 *
 * A letöltéshez minden fájlt egy TOKEN azonosít (a teljes elérési út base64-ben),
 * hogy a portál biztonságosan, csak az engedélyezett fájlokat szolgálja ki.
 */
object PortalMediaHelper {

    data class MediaEntry(
        val displayName: String,
        val category: String,     // "Hangfelvétel" / "Fotó"
        val sizeBytes: Long,
        val token: String,        // base64(elérési út) VAGY "ms:" + MediaStore id
        val mimeType: String
    )

    /** Az engedélyezett hang-mappák (csak ezekből tölthető le). */
    private fun audioDirs(context: Context): List<Pair<String, File>> = listOfNotNull(
        // Az ÚJ, nyilvános helyek (/Recordings/...) — ide kerülnek a felvételek.
        "Diktafon" to RecordingsDirs.dictaphone(context),
        "Rádió-felvétel" to RecordingsDirs.radio(context),
        // A RÉGI helyek is maradnak a listában: ha valakinél a költöztetés
        // még nem futott le (nincs teljes fájlhozzáférés), a portál akkor is
        // lássa a régi felvételeit. Üres mappát a listAudio úgyis kihagy.
        "Diktafon" to RecordingsDirs.legacyDictaphone(context),
        "Rádió-felvétel" to RecordingsDirs.legacyRadio(context),
        "Hangjegyzet" to File(context.filesDir, "voice_notes"),
        // Ha a nyilvános mappa nem hozható létre, az új és a régi hely
        // UGYANAZ — ilyenkor minden fájl kétszer szerepelne a listában.
    ).distinctBy { it.second.absolutePath }

    fun listAudio(context: Context): List<MediaEntry> {
        val out = mutableListOf<MediaEntry>()
        for ((label, dir) in audioDirs(context)) {
            if (!dir.exists() || !dir.isDirectory) continue
            dir.listFiles()?.filter { it.isFile }?.forEach { f ->
                out.add(
                    MediaEntry(
                        displayName = "${f.name}  ($label)",
                        category = "Hangfelvétel",
                        sizeBytes = f.length(),
                        token = "f:" + android.util.Base64.encodeToString(
                            f.absolutePath.toByteArray(), android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
                        ),
                        mimeType = guessAudioMime(f.name)
                    )
                )
            }
        }
        return out.sortedByDescending { it.displayName }
    }

    fun listPhotos(context: Context, limit: Int = 200): List<MediaEntry> {
        val out = mutableListOf<MediaEntry>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )
        // Csak a SuperDL által készített fotók (a fájlnév "SuperDL_" előtaggal).
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("SuperDL_%")
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, args, sort
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                var count = 0
                while (c.moveToNext() && count < limit) {
                    val id = c.getLong(idCol)
                    out.add(
                        MediaEntry(
                            displayName = c.getString(nameCol) ?: "kép_$id",
                            category = "Fotó",
                            sizeBytes = c.getLong(sizeCol),
                            token = "ms:$id",
                            mimeType = c.getString(mimeCol) ?: "image/jpeg"
                        )
                    )
                    count++
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /**
     * Egy token feloldása letölthető bájtokká. Visszaadja a (bájtok, név, mime)
     * hármast, vagy null ha nem található / nem engedélyezett.
     */
    fun resolveToken(context: Context, token: String): Triple<ByteArray, String, String>? {
        return try {
            when {
                token.startsWith("f:") -> {
                    val path = String(
                        android.util.Base64.decode(
                            token.removePrefix("f:"),
                            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
                        )
                    )
                    // Biztonság: csak az engedélyezett mappákból.
                    // MIÉRT kanonikus út: a nyers startsWith átengedte a
                    // "…/voice_notes/../../databases/x" alakú utat, és a
                    // "voice_notes2" nevű testvérmappát is.
                    val f = File(path).canonicalFile
                    val allowed = audioDirs(context).any { (_, dir) ->
                        val base = try { dir.canonicalPath } catch (_: Exception) { return@any false }
                        f.path.startsWith(base + File.separator)
                    }
                    if (!allowed) return null
                    if (!f.exists() || !f.isFile) return null
                    Triple(f.readBytes(), f.name, guessAudioMime(f.name))
                }
                token.startsWith("ms:") -> {
                    val id = token.removePrefix("ms:").toLongOrNull() ?: return null
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    // MIÉRT: az "ms:" azonosító kitalálható sorszám — csak a
                    // SuperDL saját fotóit adjuk ki, nem a teljes galériát.
                    val name = context.contentResolver.query(
                        uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null
                    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    if (name == null || !name.startsWith("SuperDL", ignoreCase = true)) return null
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return null
                    Triple(bytes, "SuperDL_foto_$id.jpg", "image/jpeg")
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun guessAudioMime(name: String): String = when {
        name.endsWith(".mp3", true) -> "audio/mpeg"
        name.endsWith(".m4a", true) -> "audio/mp4"
        name.endsWith(".aac", true) -> "audio/aac"
        name.endsWith(".wav", true) -> "audio/wav"
        name.endsWith(".ogg", true) -> "audio/ogg"
        else -> "application/octet-stream"
    }
}
