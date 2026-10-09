package com.superdl.launcher.sound

import android.content.Context
import android.content.ContentUris
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Az Android beépített (gyári) csengő-, értesítési- és ébresztőhangjait
 * listázza, hogy a vak felhasználó ezek közül választhasson – és minden
 * hangba bele tudjon hallgatni választás előtt.
 *
 * A telefon saját hangkészletét adja vissza (nem kell külső fájl), ezért
 * bármelyik eszközön a gyári hangokból lehet válogatni.
 */
object SystemRingtoneHelper {

    data class RingtoneItem(
        val title: String,
        val uri: Uri
    )

    /** Az ébresztőhöz használt gyári ébresztőhangok. */
    fun alarmTones(context: Context): List<RingtoneItem> =
        listTones(context, RingtoneManager.TYPE_ALARM)

    /** A híváshoz használt gyári csengőhangok. */
    fun ringtones(context: Context): List<RingtoneItem> =
        listTones(context, RingtoneManager.TYPE_RINGTONE)

    /** Az értesítésekhez használt gyári hangok. */
    fun notificationTones(context: Context): List<RingtoneItem> =
        (listOfNotNull(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let {
            RingtoneItem("Telefon alapértelmezett értesítési hangja", it)
        }) + listTones(context, RingtoneManager.TYPE_NOTIFICATION) + notificationFolderTones(context))
            .distinctBy { it.uri.toString() }

    /** Saját fájlok akkor is, ha a MediaStore még nem jelölte őket értesítési hangnak. */
    private fun notificationFolderTones(context: Context): List<RingtoneItem> {
        val pathColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DISPLAY_NAME, pathColumn)
        val selection = "$pathColumn LIKE ?"
        val pattern = if (Build.VERSION.SDK_INT >= 29) "%Notifications/%" else "%/Notifications/%"
        val items = mutableListOf<RingtoneItem>()
        try {
            context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                selection, arrayOf(pattern), "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathIndex = cursor.getColumnIndexOrThrow(pathColumn)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathIndex).orEmpty().replace('\\', '/')
                    if (!isNotificationPath(path)) continue
                    val title = cursor.getString(titleIndex)?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(nameIndex)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                        ?: continue
                    items += RingtoneItem(title, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idIndex)))
                }
            }
        } catch (_: Exception) {
            // Engedély hiányában a rendszer hanglistája továbbra is elérhető.
        }
        return items
    }

    /** Programhangoknál mindhárom rendszerkategória és a Notifications mappa látható. */
    fun allAlertTones(context: Context): List<RingtoneItem> =
        (notificationTones(context) + listOfNotNull(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let {
                RingtoneItem("Telefon alapértelmezett ébresztőhangja", it)
            }) + alarmTones(context) + ringtones(context))
            .distinctBy { it.uri.toString() }

    internal fun isNotificationPath(value: String): Boolean {
        val path = value.replace('\\', '/')
        return path.contains("/Notifications/", ignoreCase = true) ||
            path.startsWith("Notifications/", ignoreCase = true)
    }

    private fun listTones(context: Context, type: Int): List<RingtoneItem> {
        val manager = RingtoneManager(context).apply { setType(type) }
        val items = mutableListOf<RingtoneItem>()
        try {
            val cursor = manager.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)?.trim().orEmpty()
                if (title.isBlank()) continue
                val uri = manager.getRingtoneUri(cursor.position) ?: continue
                items.add(RingtoneItem(title, uri))
            }
        } catch (_: Exception) {
            // Ha a katalógus nem olvasható, üres listát adunk (a hívó kezeli).
        }
        return items
    }

    /** Az alapértelmezett hang az adott típushoz (ha a felhasználó nem választott). */
    fun defaultUri(type: Int): Uri? =
        RingtoneManager.getDefaultUri(type)

    /** Egy mentett URI-hoz megkeresi a hozzá tartozó nevet (visszaolvasáshoz). */
    fun titleForUri(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        return try {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        } catch (_: Exception) {
            null
        }
    }
}
