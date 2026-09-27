package com.superdl.launcher.offers

/**
 * Pepco — a heti „Újságaink" gyűjtemény (pepco.hu, a bolt saját oldala).
 *
 * A Windows-oldali `pepco.py` átirata. Nem lapozós kép: a pepco.hu
 * Shopify-áruház „Újságaink" gyűjteménye HTML-lista, minden termék egy
 * hivatkozás, aminek az akadálymentes címkéje (aria-label) így néz ki:
 *
 *     <a href="/products/halloween-plussfigura-637271" …
 *        aria-label="halloween plüssfigura - 1800.0 Ft">
 *
 * MIÉRT az aria-label: ugyanezt a szöveget olvassa fel a képernyőolvasó a
 * bolt oldalán is — amit a vak vásárló a böngészőben hallana, azt halljuk mi
 * is. Az újság csütörtökönként cserélődik; egy kérés hozza az egészet.
 */
object PepcoOffers {

    const val STORE = "Pepco"
    private const val BASE = "https://pepco.hu"
    const val PAGE = "$BASE/gyujtemeny/ujsagaink/"

    private val PRODUCT = Regex(
        "<a href=\"(/products/[^\"?#]+)\"[^>]*?aria-label=\"([^\"]+?) - " +
            "([0-9]+(?:\\.[0-9]+)?) Ft\""
    )
    private val DESCRIPTION = Regex("aria-label=\"Product description: ([^\"]*)\"")

    /** A bolt kisbetűvel kezdi a neveket („halloween plüssfigura") — az első betű nagy. */
    private fun nice(s: String?): String = PyText.upperFirst(PyText.strip(PyText.unescape(s.orEmpty())))

    fun parse(html: String): List<OfferItem> {
        val out = mutableListOf<OfferItem>()
        val seen = mutableSetOf<String>()
        for (m in PRODUCT.findAll(html)) {
            val path = m.groupValues[1]
            if (!seen.add(path)) continue
            // a termék rövid leírása (pl. „tökkel és macskával") közvetlenül
            // a kártyán belül, a következő termék előtt
            val end = m.range.last + 1
            val next = html.indexOf("<a href=\"/products/", end)
            val stop = if (next > 0) next else minOf(end + 4000, html.length)
            val desc = DESCRIPTION.find(html.substring(end, maxOf(end, stop)))
            val price = m.groupValues[3].toDoubleOrNull() ?: continue
            out += OfferItem(
                store = STORE, name = nice(m.groupValues[2]), price = PyText.round(price),
                category = "Heti újság",
                note = desc?.let { nice(it.groupValues[1]) }.orEmpty(),
                code = path, url = BASE + path
            )
        }
        return out
    }

    fun download(get: (String) -> String, progress: (String) -> Unit = {}): List<OfferItem> {
        progress("Pepco: a heti újság letöltése…")
        return parse(get(PAGE))
    }
}
