package com.nitaplay.data

import android.net.Uri

/**
 * Egy zeneszám. A lejátszó CSAK Uri-t használ — ugyanaz kezeli a helyi
 * és a felhős fájlokat.
 */
data class Track(
    val id: Long,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUri: Uri? = null,
    val contentUri: Uri,
    val source: MusicSource = MusicSource.PHONE
) {
    fun displayArtist(): String = artist.ifBlank { "Ismeretlen előadó" }

    fun formatDuration(): String {
        if (durationMs <= 0) return "--:--"
        val totalSec = (durationMs / 1000).toInt()
        val mins = totalSec / 60
        val secs = totalSec % 60
        return "%d:%02d".format(mins, secs)
    }
}

enum class MusicSource { PHONE, CLOUD }

enum class PlayMode {
    SEQUENTIAL,
    REPEAT_ONE,
    REPEAT_ALL,
    SHUFFLE;

    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]

    fun labelHu(): String = when (this) {
        SEQUENTIAL -> "Sorban"
        REPEAT_ONE -> "Egy szám ismétlése"
        REPEAT_ALL -> "Lista ismétlése"
        SHUFFLE -> "Véletlen"
    }
}

enum class LibraryTab { TRACKS, ARTISTS, ALBUMS, PLAYLISTS, FAVORITES }

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<Long> = emptyList()
)
