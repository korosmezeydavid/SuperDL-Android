package com.superdl.launcher.sos

import android.location.Location
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A VÉSZ-SMS SZÖVEGE.
 *
 * MIÉRT MEGY KI AKKOR IS, HA FELVETTÉK A HÍVÁST: a hívás azt intézi el, hogy
 * valaki TUDJON rólad; az SMS azt, hogy MEG IS TALÁLJON. Ha felveszik, az első
 * kérdés úgyis az lesz, hogy „hol vagy?" — és pánikban, vakon, egy ismeretlen
 * utcasarkon erre pont nem tudsz válaszolni. Az SMS akkor már ott van a
 * segítő telefonján. A kettő nem helyettesíti egymást.
 *
 * MIÉRT VAN BENNE TÉRKÉP-LINK: koordinátát senki nem fog kézzel begépelni,
 * főleg nem ijedten. Egy koppintás a linken, és megy a navigáció.
 *
 * MIÉRT MEGY EL A HELYZET NÉLKÜL IS: a néma telefon rosszabb, mint a
 * pontatlan üzenet. Ha nincs helyzet, azt KIMONDJUK az üzenetben, hogy a
 * segítő tudja: nem elfelejtettük, hanem nem sikerült.
 */
object SosMessage {

    /** A felhasználó által kért, szó szerinti szöveg. Ne írd át. */
    const val HEADLINE = "eltévedtem, vagy bajban vagyok!"

    private const val SIGNATURE = "Super DL S.O.S."

    fun build(location: Location?): String = buildString {
        append(HEADLINE)
        append('\n')
        if (location == null) {
            append("A helyzetemet nem sikerült megállapítani. Kérlek hívj vissza!")
        } else {
            val lat = format(location.latitude)
            val lon = format(location.longitude)
            append("Itt vagyok: ")
            append(lat)
            append(", ")
            append(lon)
            append('\n')
            append(mapLink(lat, lon))
            append('\n')
            append(accuracyLine(location))
        }
        append('\n')
        append(SIGNATURE)
    }

    /**
     * A PONTOSÍTÓ ÜZENET. Csak akkor megy ki, ha az első SMS-ben nem volt
     * helyzet, vagy a friss mérés sokkal pontosabb — nem küldünk nyolc SMS-t
     * ott, ahol négy is elég.
     */
    fun buildUpdate(location: Location): String {
        val lat = format(location.latitude)
        val lon = format(location.longitude)
        return "Pontosított helyzet: $lat, $lon\n" +
            mapLink(lat, lon) + "\n" +
            accuracyLine(location) + "\n" + SIGNATURE
    }

    private fun mapLink(lat: String, lon: String): String =
        "https://maps.google.com/?q=$lat,$lon"

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

    private fun format(value: Double): String =
        String.format(Locale.US, "%.6f", value)

    /**
     * Megéri-e kiküldeni a pontosítást? Csak akkor, ha az első üzenetben nem
     * volt helyzet, vagy az új mérés LÉNYEGESEN jobb. A „lényegesen" itt
     * háromszoros pontosság vagy száz méternél nagyobb elmozdulás — ennél
     * kisebb különbségért nem érdemes újabb négy SMS-t küldeni.
     */
    fun worthUpdating(first: Location?, fresh: Location): Boolean {
        if (first == null) return true
        if (!fresh.hasAccuracy()) return false
        if (!first.hasAccuracy()) return true
        if (fresh.accuracy * 3 < first.accuracy) return true
        return first.distanceTo(fresh) > 100f
    }
}
