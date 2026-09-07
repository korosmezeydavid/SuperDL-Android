package com.superdl.launcher.textreader

import android.graphics.RectF

/** Egy felismert szövegsor és a helye a képen (0..1 arányokban). */
data class TextBox(val text: String, val box: RectF)

/**
 * A KERESETT SZÖVEG ILLESZTÉSE A FELISMERT SOROKHOZ.
 *
 * Két, egymástól független forrásból jön hiba, és mindkettő ugyanoda üt:
 * az ÉKEZETEKRE.
 *
 * 1. A beszédfelismerő azt írja le, amit hall — „váci utca", „vaci utca",
 *    „Váci utcát" egyaránt előfordul.
 * 2. Az OCR egy utcatáblán a hosszú ő-t gyakran ó-nak, az í-t l-nek olvassa,
 *    a csupa nagybetűs feliratról pedig sokszor teljesen leszedi az ékezetet.
 *
 * Ezért az összehasonlítás előtt MINDKÉT oldalról leszedjük az ékezeteket,
 * kisbetűsítünk, és kidobunk mindent, ami nem betű vagy szám. Ami marad, az
 * a szó „csontváza" — azon már megbízhatóan lehet egyezést keresni.
 *
 * Mit NEM csinálunk: nem próbálunk okosabbak lenni ennél (hasonlósági
 * távolság, szótövezés). Egy téves találat a kereséskor rosszabb, mint egy
 * kihagyott: aki elindul egy nem létező tábla felé, az kárt szenved.
 */
object TextMatcher {

    // Szándékosan TÉRKÉP és nem két egyforma hosszú karakterlánc: két
    // párhuzamos sztringnél egyetlen elgépelt betű minden ékezetet
    // elcsúsztatna, és ezt semmi nem jelezné — csak a keresés hibázna
    // csendben, ami itt a legrosszabb hibafajta.
    private val EKEZET_TERKEP: Map<Char, Char> = buildMap {
        "áàâäãå".forEach { put(it, 'a') }
        "éèêë".forEach { put(it, 'e') }
        "íìîï".forEach { put(it, 'i') }
        "óòôöõő".forEach { put(it, 'o') }
        "úùûüű".forEach { put(it, 'u') }
        put('ñ', 'n')
        put('ç', 'c')
    }

    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input.lowercase()) {
            val c = EKEZET_TERKEP[ch] ?: ch
            if (c.isLetterOrDigit()) sb.append(c)
        }
        return sb.toString()
    }

    /**
     * Illeszkedik-e a sor a keresett szövegre?
     *
     * A keresett kifejezést szavakra bontjuk, és MINDEGYIKNEK szerepelnie
     * kell a sorban. Így a „váci utca" nem talál rá a puszta „utca" szóra,
     * de rátalál a „VÁCI UTCA 14" táblára is.
     */
    fun matches(line: String, query: String): Boolean {
        val sor = normalize(line)
        if (sor.isEmpty()) return false
        val szavak = query.split(Regex("\\s+"))
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
        if (szavak.isEmpty()) return false
        return szavak.all { sor.contains(it) }
    }

    /** A keresett szövegre illeszkedő sorok. */
    fun find(boxes: List<TextBox>, query: String): List<TextBox> =
        boxes.filter { matches(it.text, query) }
}
