package com.superdl.launcher.music

import android.content.Context
import android.net.Uri
import android.provider.MediaStore

object MusicHelper {

    fun getTracks(context: Context, limit: Int = 50): List<MusicTrack> {
        val tracks = mutableListOf<MusicTrack>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_MUSIC
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(collection, projection, selection, null, sort)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val titleIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val durationIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext() && tracks.size < limit) {
                val id = cursor.getLong(idIdx)
                val title = cursor.getString(titleIdx)?.trim().orEmpty()
                if (title.isBlank()) continue
                val uri = Uri.withAppendedPath(collection, id.toString())
                tracks.add(
                    MusicTrack(
                        id = id,
                        title = title,
                        artist = cursor.getString(artistIdx)?.trim().orEmpty(),
                        durationMs = cursor.getLong(durationIdx),
                        contentUri = uri
                    )
                )
            }
        }
        return tracks
    }
}