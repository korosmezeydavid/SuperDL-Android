package com.superdl.launcher.home

import android.location.Location
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A „MÉG NEM ÉRTEM HAZA" ÜZENET SZÖVEGE.
 *
 * MIÉRT NEM AZ S.O.S. SZÖVEGE MEGY KI: az S.O.S. mondata („eltévedtem, vagy
 * bajban vagyok!") a felhasználó szó szerinti kérése, és a kódban külön
 * védett. Egy „még nem értem haza" viszont NEM ugyanaz az üzenet — lehet,
 * hogy csak elhúzódott a vacsora. Ha ugyanaz a mondat menne ki, két
 * különböző súlyú helyzet mosódna össze, és a valódi vészjelzés veszítené el
 * a súlyát.
 *
 * MIÉRT VAN BENNE, HOGY AUTOMATIKUS: aki megkapja, tudja meg, hogy ezt nem a
 * felhasználó gépelte be. Egy magától küldött üzenetet megtévesztés nélkül
 * csak így lehet kiküldeni.
 */
object HomeWatchMessage {

    private const val SIGNATURE = "Super DL otthon-figyelés (automatikus üzenet)"

    fun build(name: String?, deadline: String, location: Location?): String = buildString {
        append(if (name.isNullOrBlank()) "Figyelem!" else "Figyelem! $name")
        append('\n')
        append("A telefon $deadline-kor ellenőrizte, és még nem értem haza.")
        append('\n')
        if (location == null) {
            append("A helyzetemet nem sikerült megállapítani.")
        } else {
            val lat = String.format(Locale.US, "%.6f", location.latitude)
            val lon = String.format(Locale.US, "%.6f", location.longitude)
            append("Itt vagyok: $lat, $lon")
            append('\n')
            append("https://maps.google.com/?q=$lat,$lon")
            append('\n')
            append(accuracyLine(location))
        }
        append('\n')
        append(SIGNATURE)
    }

    private fun accuracyLine(location: Location): String {
        val accuracy = if (location.hasAccuracy()) {
            "kb. ${location.accuracy.toInt()} méter pontossággal"
        } else {
            "ismeretlen pontossággal"
        }
        val time = try {
            SimpleDateFormat("HH:mm", Locale("hu")).format(Date(location.time))
        } catch (_: Exception) {
            ""
        }
        return if (time.isBlank()) accuracy else "$accuracy, $time-kor"
    }
}
