package com.superdl.launcher.book

/**
 * A hangoskönyv-lejátszó indításához átadott adatok (a MusicPlaylistHolder
 * mintájára – így nem kell nagy adatot az Intentbe tenni).
 */
object AudiobookHolder {
    var bookPath: String = ""            // a MAPPA (vagy fájl) útja = a könyv kulcsa
    var bookTitle: String = ""
    var tracks: List<String> = emptyList()   // a sávok TELJES fájl-útjai, sorrendben
    var startTrack: String = ""          // melyik sáv fájlnevén kezdjen ("" = folytatás/első)
    var startMs: Int = 0                 // hol kezdjen azon a sávon (ms)
}
