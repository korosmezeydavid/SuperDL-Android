package com.superdl.launcher.book

data class BookBookmark(
    val id: Int,
    val bookPath: String,
    val bookTitle: String,
    val charOffset: Int,
    val preview: String,
    val createdAt: Long,
    // Hangoskönyv-mezők (a PC-vel közös szinkronhoz; szöveges könyvnél alap):
    val kind: String = "text",   // "text" vagy "audio"
    val posMs: Int = 0,          // audio: pozíció a sávon belül, ezredmp
    val track: String = ""       // audio: a sáv fájlneve a mappán belül
) {
    fun speakPreview(): String {
        if (kind == "audio") {
            val mp = posMs / 1000
            val ido = "%d:%02d".format(mp / 60, mp % 60)
            val sav = if (track.isNotBlank()) " $track" else ""
            return "$bookTitle. Hang-könyvjelző:$sav $ido."
        }
        val pct = if (charOffset > 0) "pozíció $charOffset" else "elején"
        val snippet = preview.take(80).trim()
        return if (snippet.isNotBlank()) {
            "$bookTitle. Könyvjelző: $pct. $snippet"
        } else {
            "$bookTitle. Könyvjelző: $pct."
        }
    }
}