package com.superdl.launcher.offers

/**
 * Penny — a penny.hu saját, nyilvános ajánlat-oldalai.
 *
 * A Windows-oldali `penny.py` átirata. Az oldal kész HTML: minden termék egy
 * „csempe", benne a név, a kiszerelés, az érvényesség és az ár(ak) —
 * kártya nélkül és Penny Kártyával. Ugyanazt olvassuk, amit a böngésző
 * megmutat; semmit nem terjesztünk tovább.
 *
 * Felépítés (2026-09-25-i mérés, a telefonéhoz hasonló klienssel is 200):
 *   /ajanlatok                                   → a heti fő kategória linkje
 *   /category/ajanlatok-…-koezoett-<alkategória> → alkategóriák, lapozva
 */
object PennyOffers {

    const val STORE = "Penny"
    private const val BASE = "https://www.penny.hu"

    /** A gyűjtő alkategóriák ugyanazokat ismétlik — ezek csak a végén jönnek. */
    private val COLLECTORS = listOf("kiemelt", "penny-kartyaval", "online-extra", "hetvegi", "hetfotol")

    private val DOT = setOf(RegexOption.DOT_MATCHES_ALL)

    fun mainCategory(html: String): String =
        Regex("href=\"(/category/ajanlatok-[^\"?#]+)\"").find(html)?.groupValues?.get(1).orEmpty()

    fun subCategories(html: String, mainPath: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()
        val re = Regex("<a[^>]+href=\"(" + Regex.escape(mainPath) + "-[^\"?#]+)\"[^>]*>(.*?)</a>", DOT)
        for (m in re.findAll(html)) {
            val path = m.groupValues[1]
            if (!seen.add(path)) continue
            // a linkszöveg végén a termékszám áll („Italok 29") — az nem név
            val name = OfferText.clean(m.groupValues[2]).replace(Regex("\\s+\\d+$"), "")
                .ifBlank { fromSlug(path.substring(mainPath.length + 1)) }
            out += path to name
        }
        return out
    }

    private fun fromSlug(slug: String): String =
        OfferText.capitalize(slug.replace('-', ' ').trim())

    fun pageCount(html: String): Int =
        Regex("[?&]page=(\\d+)").findAll(html).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 1

    /** Egy kategória-oldal termékei (kategória nélkül). */
    fun tiles(html: String): List<OfferItem> {
        val parts = html.split(Regex("(?=<a href=\"/products/)"))
        val out = mutableListOf<OfferItem>()
        for (d in parts.drop(1)) {
            val link = Regex("^<a href=\"(/products/[^\"]+)\"").find(d) ?: continue
            val title = Regex("data-test=\"product-title\"[^>]*>(.*?)</", DOT).find(d) ?: continue
            val rawName = OfferText.clean(title.groupValues[1])
            val name = if (OfferText.isUpper(rawName)) OfferText.capitalize(rawName) else rawName

            val pack = Regex("data-test=\"product-information-piece-description\"[^>]*>(.*?)</ul>", DOT)
                .find(d)?.let { desc ->
                    Regex("<li>(.*?)</li>", DOT).findAll(desc.groupValues[1])
                        .map { OfferText.clean(it.groupValues[1]) }.filter { it.isNotBlank() }
                        .joinToString(", ")
                }.orEmpty()
            val validity = Regex("data-test=\"product-price-validity\"[^>]*>(.*?)</div></div>", DOT)
                .find(d)?.let { validityOf(OfferText.clean(it.groupValues[1])) }.orEmpty()
            val discount = Regex("discount-info[^>]*>(.*?)</div>", DOT).find(d)?.let { k ->
                Regex("-\\s?\\d+\\s?%").find(OfferText.clean(k.groupValues[1]))?.value?.replace(" ", "")
            }.orEmpty()

            var price: Int? = null
            var cardPrice: Int? = null
            var cardName = ""
            var unit = ""
            for (block in d.split("data-test=\"product-price-type\"").drop(1)) {
                val label = Regex("price-label\"[^>]*>(.*?)</div>", DOT).find(block)
                    ?.let { OfferText.clean(it.groupValues[1]) }.orEmpty()
                val main = Regex("ws-product-price-value__main[^>]*>(.*?)</span>", DOT).find(block)
                val unitRaw = Regex("data-test=\"product-price-type-label\"[^>]*>(.*?)</div>", DOT).find(block)
                val value = main?.let { OfferText.priceNumber(OfferText.clean(it.groupValues[1])) } ?: continue
                val lower = label.lowercase()
                if (lower.contains("kártyával") || lower.contains("kartyaval")) {
                    cardPrice = value
                    cardName = "Penny Kártyával"
                    if (unit.isBlank() && unitRaw != null) unit = unitOf(OfferText.clean(unitRaw.groupValues[1]))
                } else if (price == null) {
                    price = value
                    if (unitRaw != null) unit = unitOf(OfferText.clean(unitRaw.groupValues[1]))
                }
            }
            val old = Regex("text-decoration-line-through[^>]*>(.*?)</", DOT).find(d)
                ?.let { OfferText.priceNumber(OfferText.clean(it.groupValues[1])) }

            out += OfferItem(
                store = STORE, name = name, price = price, cardPrice = cardPrice, cardName = cardName,
                oldPrice = old, discount = discount, packSize = pack, unitPrice = unit,
                validity = validity, code = BASE + link.groupValues[1]
            )
        }
        return out
    }

    /** „Cs 2026.09.24-tól Sze 2026.09.30-ig" → „09.24-tól 09.30-ig". */
    private fun validityOf(s: String): String {
        val dates = Regex("\\d{4}\\.(\\d{2})\\.(\\d{2})").findAll(s).map { it.groupValues[1] to it.groupValues[2] }.toList()
        return when {
            dates.size >= 2 -> "${dates.first().first}.${dates.first().second}-tól ${dates.last().first}.${dates.last().second}-ig"
            dates.size == 1 -> if (s.contains("tól") || s.contains("től"))
                "${dates[0].first}.${dates[0].second}-tól" else "${dates[0].first}.${dates[0].second}-ig"
            else -> s
        }
    }

    /** „1 KG 3897 Ft" → „1 kg = 3897 Ft". */
    private fun unitOf(s: String): String {
        val m = Regex("(1\\s*\\w+)\\s+(\\d[\\d ]*)\\s*Ft", RegexOption.IGNORE_CASE).find(s) ?: return s
        return "${m.groupValues[1].lowercase()} = ${m.groupValues[2].trim()} Ft"
    }

    /**
     * Az összes heti Penny-ajánlat. Egy termék csak egyszer szerepel; a
     * kategóriája az első NEM gyűjtő alkategória, ahol előfordul.
     */
    fun download(get: (String) -> String, progress: (String) -> Unit = {}): List<OfferItem> {
        val mainPath = mainCategory(get("$BASE/ajanlatok"))
        if (mainPath.isBlank()) throw IllegalStateException("A Penny oldalán nem találom a heti ajánlatokat.")
        val mainHtml = get(BASE + mainPath)
        val subs = subCategories(mainHtml, mainPath)
        val normal = subs.filter { s -> COLLECTORS.none { s.first.contains(it) } }
        val collect = subs.filter { s -> COLLECTORS.any { s.first.contains(it) } }
        val out = mutableListOf<OfferItem>()
        val seen = mutableSetOf<String>()
        for ((path, name) in normal + collect) {
            progress(name)
            val first = get(BASE + path)
            val pages = listOf(first) + (2..pageCount(first)).map { get("$BASE$path?page=$it") }
            for (p in pages) for (t in tiles(p)) {
                if (seen.add(t.code)) out += t.copy(category = name)
            }
        }
        if (subs.isEmpty()) {
            for (n in 1..pageCount(mainHtml)) {
                val p = if (n == 1) mainHtml else get("$BASE$mainPath?page=$n")
                for (t in tiles(p)) if (seen.add(t.code)) out += t
            }
        }
        return out
    }
}
