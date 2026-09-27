package com.superdl.launcher.offers

import java.text.Normalizer

/** Közös szövegkezelés a gyűjtőknek és a felolvasásnak. */
object OfferText {

    private val HTML_TAG = Regex("<[^>]+>")
    private val SPACES = Regex("\\s+")
    private val PRICE_NUM = Regex("(\\d{1,3}(?:[ .  ]\\d{3})+|\\d+)")

    /** HTML-darabból tiszta szöveg. */
    fun clean(s: String?): String {
        var t = HTML_TAG.replace(s.orEmpty(), " ")
        t = unescape(t).replace(' ', ' ').replace(' ', ' ')
        return SPACES.replace(t, " ").trim()
    }

    /**
     * HTML-entitások feloldása. SZÁNDÉKOSAN kézzel, nem az Android saját
     * Html-osztályával: így a gyűjtők sima Kotlin-kódok maradnak, és a gépen
     * is kipróbálhatók ugyanazon a letöltött oldalon, mint a Windows-modul.
     */
    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);")
    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "ndash" to "–", "mdash" to "—", "hellip" to "…",
        "bdquo" to "„", "rdquo" to "”", "ldquo" to "“", "euro" to "€", "shy" to ""
    )

    private fun unescape(s: String): String = ENTITY.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") ->
                e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            else -> NAMED[e.lowercase()] ?: m.value
        }
    }

    /** „1 169 Ft", „1169 Ft", „899.-" → 1169 / 899. Nincs szám: null. */
    fun priceNumber(s: String?): Int? {
        val m = PRICE_NUM.find(s.orEmpty().replace(' ', ' ')) ?: return null
        return m.groupValues[1].replace(Regex("[ .  ]"), "").toIntOrNull()
    }

    /** Kereséshez: kisbetű, ékezet nélkül („Rántott" → „rantott"). */
    fun plain(s: String?): String =
        Normalizer.normalize(s.orEmpty().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    /** Csupa nagybetű-e (Python isupper: a betűk mind nagyok, és van betű). */
    fun isUpper(s: String): Boolean {
        val letters = s.filter { it.isLetter() }
        return letters.isNotEmpty() && letters.all { it.isUpperCase() }
    }

    /** Python capitalize: az első betű nagy, a többi kicsi. */
    fun capitalize(s: String): String =
        s.lowercase().replaceFirstChar { it.uppercase() }

    /** „Ft" helyett „forint": a beszédmotor a rövidítést sokszor betűzi. */
    fun speakMoney(s: String): String = s.replace(Regex("\\bFt\\b"), "forint")

    private val MONTHS = listOf(
        "január", "február", "március", "április", "május", "június",
        "július", "augusztus", "szeptember", "október", "november", "december"
    )

    private fun month(mm: String): String =
        mm.toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: mm

    /**
     * „09.24-tól 09.30-ig" → „szeptember 24. és szeptember 30. között".
     * A pontos sorszám („24.") a beszédmotornak a legbiztosabb alak: úgy
     * mondja, ahogy magyarul kell — huszonnegyedike.
     */
    fun speakValidity(v: String): String {
        Regex("(\\d{2})\\.(\\d{2})-t[óő]l (\\d{2})\\.(\\d{2})-ig").find(v)?.let { m ->
            val (m1, d1, m2, d2) = m.destructured
            val second = if (m1 == m2) "${d2.toInt()}." else "${month(m2)} ${d2.toInt()}."
            return "${month(m1)} ${d1.toInt()}. és $second között"
        }
        Regex("(\\d{2})\\.(\\d{2})-t[óő]l").find(v)?.let { m ->
            val (m1, d1) = m.destructured
            // MIÉRT: a „24-tól" rosszul hangzik („huszonnégy tól"); a sorszámos
            // alakot a beszédmotor helyesen mondja: „szeptember 24. napjától".
            return "${month(m1)} ${d1.toInt()}. napjától"
        }
        Regex("(\\d{2})\\.(\\d{2})-ig").find(v)?.let { m ->
            val (m1, d1) = m.destructured
            return "${month(m1)} ${d1.toInt()}-ig"
        }
        return v
    }

    /** Minden szó szerepel-e a termékben (név, kategória, bolt, kiszerelés). */
    fun matches(item: OfferItem, query: String): Boolean {
        val words = plain(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        // A megjegyzésben is: ott áll az Illatoriumnál, melyik parfüm ihlette
        // („versace"), a Pepcónál a termék leírása (a Windows `illik` párja).
        val hay = plain(listOf(item.name, item.category, item.store, item.packSize, item.note).joinToString(" "))
        return words.all { hay.contains(it) }
    }
}
