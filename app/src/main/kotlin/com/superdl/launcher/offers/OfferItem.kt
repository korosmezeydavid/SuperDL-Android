package com.superdl.launcher.offers

import org.json.JSONObject

/**
 * Egy akciós termék — boltfüggetlen alakban.
 *
 * A Windows-oldali `termek.py` tükörképe (ugyanazok a mezők, ugyanaz a
 * jelentésük), hogy a két program ugyanúgy beszéljen ugyanarról a termékről.
 * A felület CSAK ezt látja: hogy a Penny weboldaláról vagy az Aldi
 * újságjából jött, az a gyűjtők dolga.
 */
data class OfferItem(
    val store: String,              // "Penny", "Aldi"
    val name: String,
    val price: Int? = null,         // forint, kártya nélkül
    val cardPrice: Int? = null,     // hűségkártyás ár, ha van
    val cardName: String = "",      // "Penny Kártyával"
    val oldPrice: Int? = null,      // áthúzott, eredeti ár
    val discount: String = "",      // "-30%"
    val packSize: String = "",      // "300 g"
    val unitPrice: String = "",     // "1 kg = 3897 Ft"
    val validity: String = "",      // "09.24-tól 09.30-ig"
    val category: String = "",
    val code: String = "",          // a bolt azonosítója (URL vagy cikkszám)
    val note: String = "",
    val group: String = "",         // közös termékcsoport (OfferGroups), ha a gyűjtő tudja
    val url: String = ""            // a termék oldala a bolt honlapján, ha van
) {
    /**
     * A termék saját oldala a bolt honlapján — ha a bolt ad ilyet (a régebbi
     * gyűjtőknél, pl. Penny, a `code` maga a cím). A Windows `hivatkozas()` párja.
     */
    fun link(): String = when {
        url.isNotBlank() -> url
        code.startsWith("http") -> code
        else -> ""
    }

    /** Amit a legolcsóbban fizetsz érte (kártyával vagy anélkül). */
    fun bestPrice(): Int? = listOfNotNull(price, cardPrice).minOrNull()

    /**
     * A LISTASOR. A név és az ár ELÖL: léptetéskor ez hangzik el először,
     * és ha továbblépsz, a többit már nem kell végighallgatni.
     */
    fun speakLine(withStore: Boolean = false): String {
        val parts = mutableListOf(name)
        if (withStore) parts += store
        price?.let { parts += "$it forint" }
        cardPrice?.let { parts += "${cardName.ifBlank { "kártyával" }} $it forint" }
        if (discount.isNotBlank()) parts += discount
        if (packSize.isNotBlank() && !name.contains(packSize, ignoreCase = true)) parts += packSize
        return parts.joinToString(", ")
    }

    /** A teljes leírás, mondatokban. */
    fun details(): String {
        val s = mutableListOf("$name. Bolt: $store")
        if (group.isNotBlank()) s += "Termékcsoport: $group"
        price?.let { s += "Ár: $it forint" }
        cardPrice?.let { s += "${cardName.ifBlank { "Kártyával" }}: $it forint" }
        oldPrice?.let { s += "Eredeti ár: $it forint" }
        if (discount.isNotBlank()) s += "Kedvezmény: $discount"
        if (packSize.isNotBlank()) s += "Kiszerelés: $packSize"
        if (unitPrice.isNotBlank()) s += "Egységár: ${OfferText.speakMoney(unitPrice)}"
        if (validity.isNotBlank()) s += "Érvényes: ${OfferText.speakValidity(validity)}"
        if (category.isNotBlank()) s += "Kategória: $category"
        if (note.isNotBlank()) s += "Megjegyzés: ${OfferText.speakMoney(note)}"
        return s.joinToString(". ") + "."
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("store", store); put("name", name)
        price?.let { put("price", it) }
        cardPrice?.let { put("cardPrice", it) }
        put("cardName", cardName)
        oldPrice?.let { put("oldPrice", it) }
        put("discount", discount); put("packSize", packSize); put("unitPrice", unitPrice)
        put("validity", validity); put("category", category); put("code", code); put("note", note)
        put("group", group); put("url", url)
    }

    companion object {
        fun fromJson(o: JSONObject): OfferItem = OfferItem(
            store = o.optString("store"),
            name = o.optString("name"),
            price = if (o.has("price")) o.optInt("price") else null,
            cardPrice = if (o.has("cardPrice")) o.optInt("cardPrice") else null,
            cardName = o.optString("cardName"),
            oldPrice = if (o.has("oldPrice")) o.optInt("oldPrice") else null,
            discount = o.optString("discount"),
            packSize = o.optString("packSize"),
            unitPrice = o.optString("unitPrice"),
            validity = o.optString("validity"),
            category = o.optString("category"),
            code = o.optString("code"),
            note = o.optString("note"),
            group = o.optString("group"),
            url = o.optString("url")
        )
    }
}
