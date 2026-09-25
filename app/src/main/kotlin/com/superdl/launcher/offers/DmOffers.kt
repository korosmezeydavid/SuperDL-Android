package com.superdl.launcher.offers

import org.json.JSONObject

/**
 * dm — a dm SAJÁT termékkereső szolgáltatása, a „Kiárusítás" szűrővel.
 *
 * FONTOS, ÉS KI IS MONDJUK: a dm Magyarországon NEM tart heti akciót. A
 * saját oldala írja: „rövid akciós időszakok vagy korlátozott ajánlatok
 * nélkül" — az árai legalább négy hónapig nem emelkednek. Az egyetlen
 * valódi árcsökkentés a Kiárusítás: amíg a készlet tart, dátum nélkül.
 * Ezért a menüben is így hívjuk: „dm kiárusítás".
 *
 * Egyetlen kérés hozza az egészet (2026-09-25: 232 termék). A szolgáltatás
 * sűrű kérésekre 429-cel válaszol — ezért egy letöltés egy kérés, és hat
 * óráig nem kérdezzük újra (az OfferStore gyorsítótára).
 */
object DmOffers {

    const val STORE = "dm"
    const val URL = "https://product-search.services.dmtech.com/hu/search/static" +
        "?query=&pageSize=500&currentPage=0&filters=isSellout%3Atrue"

    private val TILE = Regex("^(.*?)\\s*\\(([^()]*)\\)\\s*$")

    /** „1 499 Ft" (a szóköz nem törő szóköz) → 1499. */
    private fun huf(s: String?): Int? {
        val t = s.orEmpty().replace(Regex("[^\\d,]"), "").replace(",", ".")
        return t.toDoubleOrNull()?.let { Math.round(it).toInt() }
    }

    fun parse(json: String): List<OfferItem> {
        val root = JSONObject(json)
        val products = root.optJSONArray("products") ?: return emptyList()
        val out = mutableListOf<OfferItem>()
        val seen = mutableSetOf<String>()
        for (i in 0 until products.length()) {
            val p = products.optJSONObject(i) ?: continue
            val code = p.optString("dan")
            if (!seen.add(code)) continue
            val td = p.optJSONObject("tileData") ?: JSONObject()
            val priceObj = td.optJSONObject("price")
            val pr = priceObj?.optJSONObject("price")
            val cur = huf(pr?.optJSONObject("current")?.optString("value"))
                ?: td.optJSONObject("trackingData")?.optDouble("price")?.takeIf { !it.isNaN() }
                    ?.let { Math.round(it).toInt() }
            val old = huf(pr?.optJSONObject("previous")?.optString("value"))
            val info = priceObj?.optJSONArray("tileInfos")?.optString(0).orEmpty().replace(' ', ' ')
            val m = TILE.find(info)
            val pack = m?.groupValues?.get(1)?.trim() ?: info.trim()
            val unit = m?.groupValues?.get(2)?.trim().orEmpty()
            val cat = td.optJSONObject("trackingData")?.optJSONArray("categories")?.optString(0).orEmpty()
            val name = listOf(p.optString("brandName"), p.optString("title"))
                .filter { it.isNotBlank() && it != "null" }.joinToString(" ").trim()
            val discount = if (old != null && cur != null && old > cur)
                "-${Math.round(100.0 * (1 - cur.toDouble() / old))}%" else ""
            out += OfferItem(
                store = STORE, name = name, price = cur, oldPrice = old, discount = discount,
                packSize = pack, unitPrice = unit, validity = "",
                category = cat, code = code, note = "Kiárusítás, amíg a készlet tart"
            )
        }
        return out
    }

    fun download(get: (String) -> String, progress: (String) -> Unit = {}): List<OfferItem> {
        progress("kiárusítás")
        return parse(get(URL))
    }
}
