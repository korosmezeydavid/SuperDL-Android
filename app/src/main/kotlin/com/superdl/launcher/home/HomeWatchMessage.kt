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

    fun build(
        name: String?,
        deadline: String,
        location: Location?,
        address: String? = null
    ): String = buildString {
        append(if (name.isNullOrBlank()) "Figyelem!" else "Figyelem! $name")
        append('\n')
        append("A telefon $deadline-kor ellenőrizte, és még nem értem haza.")
        append('\n')
        append(locationBlock(location, address))
        append('\n')
        append(SIGNATURE)
    }

    /**
     * A FRISSÍTŐ ÜZENET. Ha negyedóra múlva sem értem haza, a segítőnek egy
     * friss helyzet többet ér, mint az, amit negyedórája kapott: egy mozgó
     * ember egyetlen pontja hamar elavul.
     */
    fun buildUpdate(location: Location?, address: String? = null): String = buildString {
        append("Frissítés: még mindig nem értem haza.")
        append('\n')
        append(locationBlock(location, address))
        append('\n')
        append(SIGNATURE)
    }

    /**
     * A HELYZET RÉSZ. Itt dől el, hogy a segítő meg tud-e találni.
     *
     * A KOR MINDIG KIÍRÓDIK, ha a mérés nem friss. Régi helyzetet kiadni
     * frissként rosszabb a semminél: rossz helyre viszi a keresőt, és ott
     * elvész az idő, amikor a legtöbbet érne.
     */
    private fun locationBlock(location: Location?, address: String?): String {
        if (location == null) return "A helyzetemet nem sikerült megállapítani."
        val lat = String.format(Locale.US, "%.6f", location.latitude)
        val lon = String.format(Locale.US, "%.6f", location.longitude)
        return buildString {
            val age = HomeLocationResolver.ageMinutes(location)
            if (age == null) {
                append("Itt vagyok: $lat, $lon")
            } else {
                append("Itt voltam $age perce: $lat, $lon")
            }
            if (!address.isNullOrBlank()) {
                append('\n')
                append(address)
            }
            append('\n')
            append("https://maps.google.com/?q=$lat,$lon")
            append('\n')
            append(accuracyLine(location))
        }
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
