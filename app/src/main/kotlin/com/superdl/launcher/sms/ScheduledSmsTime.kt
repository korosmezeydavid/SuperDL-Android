package com.superdl.launcher.sms

import java.util.Calendar

/**
 * MIKOR MENJEN AZ ÜZENET — bediktálva, emberi módon.
 *
 * KÉTFÉLE MEGADÁS KELL, mert két különböző helyzet van:
 *
 *   „másfél óra múlva"   — ha veszélyes környéken jársz, nem akarod fejben
 *                          kiszámolni, hogy az öt óra negyven plusz másfél
 *                          óra mennyi;
 *   „délután ötkor"      — ha azt akarod, hogy a feleséged hétkor
 *                          felébresszen, az egy időpont.
 *
 * MIÉRT SAJÁT ÉRTELMEZŐ: a program számbillentyűs időbevitele négy számjegyet
 * vár (óra-óra-perc-perc). Az „egy óra múlva" ebbe nem fér bele, és egy vak
 * felhasználótól nem várható el, hogy fejben átszámolja.
 *
 * HA NEM ÉRTJÜK, NEM TIPPELÜNK. Egy félreértett időpont csendben rossz
 * órában küldene el egy üzenetet — ez rosszabb, mint megkérdezni még egyszer.
 */
object ScheduledSmsTime {

    /**
     * @return a küldés időpontja ezredmásodpercben, vagy null, ha nem értjük
     */
    fun parse(spoken: String, now: Long = System.currentTimeMillis()): Long? {
        val text = normalize(spoken)
        if (text.isBlank()) return null
        return parseRelative(text, now) ?: parseAbsolute(text, now)
    }

    /** Amit visszamondunk a felhasználónak, hogy ellenőrizhesse. */
    fun speakWhen(triggerAt: Long, now: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = triggerAt }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val time = "${h.toString().padStart(2, '0')} óra ${m.toString().padStart(2, '0')} perc"
        val left = triggerAt - now
        val holnap = !sameDay(triggerAt, now)
        val napText = if (holnap) "holnap " else ""
        val leftText = when {
            left < 60_000 -> "kevesebb mint egy perc múlva"
            left < 60 * 60_000 -> "${left / 60_000} perc múlva"
            else -> {
                val hh = left / (60 * 60_000)
                val mm = (left % (60 * 60_000)) / 60_000
                if (mm == 0L) "$hh óra múlva" else "$hh óra $mm perc múlva"
            }
        }
        return "$napText$time, $leftText"
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    // ── „… múlva" ───────────────────────────────────────────────────────────

    private fun parseRelative(text: String, now: Long): Long? {
        if (!text.contains("mulva")) return null

        // „másfél óra múlva" — külön, mert nem szám alakban hangzik el.
        if (text.contains("masfel ora")) return now + 90 * 60_000L
        if (text.contains("fel ora")) return now + 30 * 60_000L
        if (text.contains("negyed ora")) return now + 15 * 60_000L
        if (text.contains("haromnegyed ora")) return now + 45 * 60_000L

        var total = 0L
        var found = false

        Regex("(\\d+)\\s*(orat|ora|oraval)").find(text)?.let {
            total += (it.groupValues[1].toLongOrNull() ?: 0L) * 60 * 60_000L
            found = true
        }
        Regex("(\\d+)\\s*(percet|perc|perccel)").find(text)?.let {
            total += (it.groupValues[1].toLongOrNull() ?: 0L) * 60_000L
            found = true
        }
        // „egy óra múlva" — a kimondott számnév is előfordul
        if (!found) {
            val words = mapOf(
                "egy" to 1L, "ket" to 2L, "ketto" to 2L, "harom" to 3L,
                "negy" to 4L, "ot" to 5L, "hat" to 6L, "het" to 7L,
                "nyolc" to 8L, "kilenc" to 9L, "tiz" to 10L
            )
            for ((w, n) in words) {
                if (Regex("\\b$w\\s+ora").containsMatchIn(text)) {
                    total += n * 60 * 60_000L
                    found = true
                    break
                }
            }
        }
        if (!found || total <= 0L) return null
        return now + total
    }

    // ── „délután ötkor", „hét óra harminc" ──────────────────────────────────

    private fun parseAbsolute(text: String, now: Long): Long? {
        val pm = text.contains("delutan") || text.contains("este")
        val am = text.contains("reggel") || text.contains("delelott") || text.contains("hajnal")

        var hour: Int? = null
        var minute = 0

        Regex("(\\d{1,2})\\s*(?:ora|orakor)?\\s*(\\d{1,2})?\\s*(?:perc|perckor)?")
            .find(text)?.let { m ->
                hour = m.groupValues[1].toIntOrNull()
                m.groupValues[2].toIntOrNull()?.let { minute = it }
            }

        if (hour == null) {
            val words = mapOf(
                "egy" to 1, "ket" to 2, "ketto" to 2, "harom" to 3, "negy" to 4,
                "ot" to 5, "hat" to 6, "het" to 7, "nyolc" to 8, "kilenc" to 9,
                "tiz" to 10, "tizenegy" to 11, "tizenketto" to 12
            )
            for ((w, n) in words) {
                if (Regex("\\b$w(kor|)\\b").containsMatchIn(text)) {
                    hour = n
                    break
                }
            }
        }

        var h = hour ?: return null
        if (h !in 0..23) return null
        if (minute !in 0..59) return null
        if (pm && h in 1..11) h += 12
        if (am && h == 12) h = 0

        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // MÚLTBA NEM KÜLDÜNK. Ha a megadott idő ma már elmúlt, holnap lesz —
        // ez az, amit az ember is ért alatta.
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** Ékezetek le, kisbetű — így egy szabály elég mindkét írásmódra. */
    private fun normalize(raw: String): String {
        val lower = raw.lowercase().trim()
        val sb = StringBuilder()
        for (c in lower) {
            sb.append(
                when (c) {
                    'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'
                    'ó', 'ö', 'ő' -> 'o'
                    'ú', 'ü', 'ű' -> 'u'
                    else -> c
                }
            )
        }
        return sb.toString()
    }
}
