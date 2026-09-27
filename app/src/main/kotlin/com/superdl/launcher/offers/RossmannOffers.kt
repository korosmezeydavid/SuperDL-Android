package com.superdl.launcher.offers

import org.json.JSONArray
import org.json.JSONObject

/**
 * Rossmann — a webshop SAJÁT adatszolgáltatása (api.rossmann.hu/graphql).
 *
 * Ugyanezt hívja a shop.rossmann.hu „Általános akciók" oldala, amikor a
 * „További termékek" gombot nyomod. Nincs kulcs, nincs belépés, nincs
 * süti; sima kliens is 200-at kap (2026-09-25-én a gépről mérve, a
 * telefon kliensének megfelelő fejléccel is).
 *
 * KÉT FOLYAM kell, mert a kettő nem fedi egymást:
 *  - „általános": mindenkinek szóló árcsökkentés, áthúzott árral;
 *  - „kártyás": Rossmann+ kártya, Babakártya, Joker kupon, mennyiségi
 *    kedvezmény, ajándék termék.
 * Oldalanként legfeljebb 100 termék (a szerver többet nem enged).
 */
object RossmannOffers {

    const val STORE = "Rossmann"
    const val API = "https://api.rossmann.hu/graphql"
    private const val PER_PAGE = 100

    private val FEEDS = listOf(
        "általános" to JSONArray().put(JSONObject().put("field", "IS_DISCOUNTED").put("value", JSONArray().put(""))),
        "kártyás" to JSONArray().put(
            JSONObject().put("field", "PROMOTION_TYPE").put(
                "value", JSONArray(listOf("rossmann_plus", "rossmann_plus_baby", "qty_discount", "joker_coupon", "gratis_product"))
            )
        )
    )

    private const val QUERY = """query listProductsByCategory(${'$'}filters: [ProductFilter!], ${'$'}first: Int!, ${'$'}page: Int) {
  listProductsByCategory(product_category_path: null, filters: ${'$'}filters, first: ${'$'}first, page: ${'$'}page) {
    paginatorInfo { currentPage lastPage total hasMorePages }
    data {
      id slug name
      category_path_main { name }
      badges_featured { info image_title }
      price price_original price_unit unit_base
      price_rplus deposit_fee
      price_discount_expiration
      promotion { active_from active_to }
    }
  }
}"""

    /** A kiszerelés a név végén: „… - 100 g", „…-150 ml", „… 20ml". */
    private val PACK = Regex(
        "(\\d+(?:[.,]\\d+)?\\s*(?:x\\s*\\d+(?:[.,]\\d+)?\\s*)?(?:db|g|kg|mg|ml|cl|dl|l|m|cm|mm|lap|pár|" +
            "tekercs|adag|kapszula|tabletta|darab|mosás))\\s*$",
        RegexOption.IGNORE_CASE
    )

    fun body(filters: JSONArray, page: Int): String = JSONObject()
        .put("operationName", "listProductsByCategory")
        .put("query", QUERY)
        .put("variables", JSONObject().put("filters", filters).put("first", PER_PAGE).put("page", page))
        .toString()

    private fun num(o: JSONObject, key: String): Int? =
        if (o.isNull(key)) null else o.optDouble(key, Double.NaN).takeIf { !it.isNaN() && it > 0 }
            ?.let { Math.round(it).toInt() }

    /** „2026-10-02 23:59:59" → „10.02". */
    private fun md(iso: String?): String? {
        val m = Regex("^\\d{4}-(\\d{2})-(\\d{2})").find(iso.orEmpty()) ?: return null
        return "${m.groupValues[1]}.${m.groupValues[2]}"
    }

    fun parse(p: JSONObject): OfferItem {
        val name = p.optString("name").replace(Regex("\\s+"), " ").trim()
        val price = num(p, "price")
        val old = num(p, "price_original")
        val card = num(p, "price_rplus")
        val promo = p.optJSONObject("promotion")
        val from = md(promo?.optString("active_from"))
        val until = md(p.optString("price_discount_expiration").takeIf { it.isNotBlank() && it != "null" }
            ?: promo?.optString("active_to"))
        val validity = when {
            from != null && until != null -> "$from-tól $until-ig"
            until != null -> "$until-ig"
            else -> ""
        }
        val unit = num(p, "price_unit")
        val base = p.optString("unit_base").takeIf { it.isNotBlank() && it != "null" }
        val badges = p.optJSONArray("badges_featured")
        val offerText = (0 until (badges?.length() ?: 0)).mapNotNull { i ->
            val b = badges!!.optJSONObject(i) ?: return@mapNotNull null
            b.optString("info").takeIf { it.isNotBlank() && it != "null" }
                ?: b.optString("image_title").takeIf { it.isNotBlank() && it != "null" }
        }
        val deposit = num(p, "deposit_fee")
        val cats = p.optJSONArray("category_path_main")
        val category = cats?.optJSONObject(0)?.optString("name").orEmpty()
        val discount = if (old != null && price != null && old > price)
            "-${Math.round(100.0 * (1 - price.toDouble() / old))}%" else ""
        val note = (offerText + listOfNotNull(deposit?.let { "+ $it Ft betétdíj" })).joinToString(", ")
        return OfferItem(
            store = STORE, name = name, price = price, cardPrice = card,
            cardName = if (card != null) "Rossmann plusz kártyával" else "",
            oldPrice = old, discount = discount,
            packSize = PACK.find(name)?.groupValues?.get(1)?.trim().orEmpty(),
            unitPrice = if (unit != null && base != null) "1 $base = $unit Ft" else "",
            validity = validity, category = category,
            code = p.optString("id"), note = note,
            url = p.optString("slug").takeIf { it.isNotBlank() && it != "null" }
                ?.let { "https://shop.rossmann.hu/termek/$it" }.orEmpty()
        )
    }

    /** Az összes aktuális akció, azonosító szerint egyszer. */
    fun download(post: (String, String) -> String, progress: (String) -> Unit = {}): List<OfferItem> {
        val out = LinkedHashMap<String, OfferItem>()
        for ((feed, filters) in FEEDS) {
            var page = 1
            while (true) {
                progress("$feed, $page. oldal")
                val root = JSONObject(post(API, body(filters, page)))
                val errors = root.optJSONArray("errors")
                if (errors != null && errors.length() > 0) {
                    throw java.io.IOException("Rossmann: ${errors.optJSONObject(0)?.optString("message")}")
                }
                val res = root.getJSONObject("data").getJSONObject("listProductsByCategory")
                val data = res.optJSONArray("data") ?: JSONArray()
                for (i in 0 until data.length()) {
                    val item = parse(data.getJSONObject(i))
                    val prev = out[item.code]
                    out[item.code] = if (prev == null) item else prev.copy(
                        cardPrice = prev.cardPrice ?: item.cardPrice,
                        cardName = prev.cardName.ifBlank { item.cardName },
                        validity = prev.validity.ifBlank { item.validity }
                    )
                }
                val info = res.optJSONObject("paginatorInfo")
                if (info == null || !info.optBoolean("hasMorePages")) break
                page++
                if (page > 60) break    // vészfék: ennyi oldal nem lehet
            }
        }
        return out.values.toList()
    }
}
