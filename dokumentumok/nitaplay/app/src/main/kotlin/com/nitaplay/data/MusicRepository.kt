package com.nitaplay.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {
    private val _phoneTracks = MutableStateFlow<List<Track>>(emptyList())
    val phoneTracks: StateFlow<List<Track>> = _phoneTracks.asStateFlow()

    private val _cloudTracks = MutableStateFlow<List<Track>>(emptyList())
    val cloudTracks: StateFlow<List<Track>> = _cloudTracks.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    suspend fun loadPhone() = withContext(Dispatchers.IO) {
        _loading.value = true
        try {
            _phoneTracks.value = queryMediaStore()
        } catch (e: Exception) {
            Log.w(TAG, "loadPhone failed", e)
            _phoneTracks.value = emptyList()
        } finally {
            _loading.value = false
        }
    }

    suspend fun loadCloud() = withContext(Dispatchers.IO) {
        _loading.value = true
        try {
            _cloudTracks.value = CloudMusicScanner.scan(context)
        } catch (e: Exception) {
            Log.w(TAG, "loadCloud failed", e)
            _cloudTracks.value = emptyList()
        } finally {
            _loading.value = false
        }
    }

    fun tracksFor(source: MusicSource): List<Track> = when (source) {
        MusicSource.PHONE -> _phoneTracks.value
        MusicSource.CLOUD -> _cloudTracks.value
    }

    fun findTrack(id: Long): Track? =
        _phoneTracks.value.find { it.id == id } ?: _cloudTracks.value.find { it.id == id }

    private fun queryMediaStore(): List<Track> {
        val out = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.IS_MUSIC
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 5000"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(collection, projection, selection, null, sort)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext() && out.size < 5000) {
                val id = cursor.getLong(idIdx)
                val title = cursor.getString(titleIdx)?.trim().orEmpty()
                if (title.isBlank()) continue
                val albumId = cursor.getLong(albumIdIdx)
                val artwork = if (albumId > 0) {
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )
                } else null
                out.add(
                    Track(
                        id = id,
                        title = title,
                        artist = cursor.getString(artistIdx)?.trim()
                            ?.takeIf { it != "<unknown>" }.orEmpty(),
                        album = cursor.getString(albumIdx)?.trim().orEmpty(),
                        durationMs = cursor.getLong(durationIdx),
                        artworkUri = artwork,
                        contentUri = ContentUris.withAppendedId(collection, id),
                        source = MusicSource.PHONE
                    )
                )
            }
        }
        return out
    }

    companion object {
        private const val TAG = "NitaPlay.MusicRepo"
    }
}
