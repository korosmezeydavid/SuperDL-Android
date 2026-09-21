package com.superdl.launcher.gps

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * „ITT VAGYOK" — ELKÜLDVE, DE NEM VÉSZJELZÉSKÉNT.
 *
 * ALPH KÉRÉSE (2026-09-21): „lehet olyan helyzet, amikor el akarom küldeni,
 * hogy pontosan hol vagyok, de ezért nem akarok egy S.O.S. hívást indítani,
 * mert nem olyan személynek akarom küldeni."
 *
 * Ez a legfontosabb tudnivaló erről a fájlról: **ez NEM az S.O.S. üzenet.**
 * Az `SosMessage` azzal kezdődik, hogy „eltévedtem, vagy bajban vagyok!", és
 * riasztást indít a másik oldalon. Ide szándékosan egyetlen ijesztő szó sem
 * kerül: aki megkapja, tájékoztatást kap, nem vészhívást. Aki valaha
 * összevonná a kettőt, pont ezt a különbséget törölné el.
 *
 * Ami viszont KÖZÖS a kettővel, és úgy is kell maradnia:
 *  - **térkép-link**, mert koordinátát senki nem gépel be kézzel;
 *  - **pontosság és időpont**, mert egy húsz perces, ötven méter pontos
 *    helyzet más, mint egy mostani, öt méteres — és ezt a címzettnek kell
 *    tudnia, nem nekünk eldöntenünk helyette.
 */
object LocationShareMessage {

    private const val SIGNATURE = "Super DL"

    /**
     * A „cím" mező a helymeghatározásból jön, és lehet benne helykitöltő
     * szöveg is („ismeretlen cím", „a cím internet nélkül nem érhető el").
     * Azokat NEM írjuk bele címként — egy SMS-ben az olyan mondat zavaró,
     * a koordináta és a link viszont akkor is pontos.
     */
    private fun usableAddress(address: String?): String? {
        val trimmed = address?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        val lower = trimmed.lowercase(Locale("hu"))
        if (lower.contains("ismeretlen")) return null
        if (lower.contains("nem érhető el")) return null
        return trimmed
    }

    fun build(
        address: String?,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Int,
        atMillis: Long,
        stale: Boolean = false
    ): String = buildString {
        append(if (stale) "Legutóbb itt voltam" else "Itt vagyok most")
        usableAddress(address)?.let {
            append(": ")
            append(it)
        }
        append('.')

        val lat = format(latitude)
        val lon = format(longitude)
        append('\n')
        append(lat)
        append(", ")
        append(lon)
        append('\n')
        append("https://maps.google.com/?q=$lat,$lon")
        append('\n')
        append(accuracyLine(accuracyMeters, atMillis))
        append('\n')
        append(SIGNATURE)
    }

    private fun accuracyLine(accuracyMeters: Int, atMillis: Long): String {
        val accuracy = if (accuracyMeters > 0) {
            "kb. $accuracyMeters méter pontossággal"
        } else {
            "ismeretlen pontossággal"
        }
        val time = try {
            if (atMillis <= 0L) "" else
                SimpleDateFormat("HH:mm", Locale("hu")).format(Date(atMillis))
        } catch (_: Exception) {
            ""
        }
        return if (time.isBlank()) accuracy else "$accuracy, $time-kor"
    }

    private fun format(value: Double): String =
        String.format(Locale.US, "%.6f", value)
}
