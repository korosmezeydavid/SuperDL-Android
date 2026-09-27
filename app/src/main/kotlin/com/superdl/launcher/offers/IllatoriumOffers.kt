package com.superdl.launcher.offers

/**
 * Illatorium — Kőrösmezey Dávid SAJÁT parfümboltja (illatorium.hu).
 *
 * ⚠️ NEM akciós újság, hanem a bolt TELJES saját kínálata (Dávid döntése,
 * 2026-09-27: „remek lehetőség arra, hogy megjelenítsem a saját
 * kínálatomat"). A felület ezt ki is mondja; itt nincs „régi ár" és
 * kedvezmény, mert nincs mihez képest.
 *
 * A Windows-oldali `illatorium.py` átirata. A forrás a bolt SAJÁT weboldala.
 * Az oldal egyoldalas alkalmazás, a termékek a JavaScript-csomagjában vannak
 * (`/assets/index-<hash>.js`). A csomag neve minden frissítéskor változik,
 * ezért mindig a HTML-ből keressük ki ([bundlePath]) — így a program magától
 * követi, ha a kínálat frissül.
 *
 * Három alak van a csomagban:
 *   {id:"AM-PINK",name:"America Pink EDT 50 ml",category:"america",
 *    inspiredBy:"Playboy – Pink",scentNote:"…",price50:3000}
 *   Vae=[["LF1","Név","Ihlette"],…]           (Lion Francesco, fix áras)
 *   gg("sorgenta-x","…",[{no:"12",name:"…",brand:"…",price30:…},…])
 */
object IllatoriumOffers {

    const val STORE = "Illatorium"
    private const val BASE = "https://illatorium.hu"
    const val PAGE = "$BASE/illatinspiraciok"

    private val BUNDLE = Regex("src=\"(/assets/index-[^\"]+\\.js)\"")

    /** A süti-beállítások objektumai is {id:…,name:…} alakúak — azok nem parfümök. */
    private val SKIP = setOf("necessary", "functional", "analytics", "marketing")

    // MIÉRT [^\n] és nem „.": a Python „."-ja csak az újsort hagyja ki, a
    // Javáé a \r-t és a \u2028-at is — a két program másképp vágna.
    // A Python (?:[^"\\]|\\.)* kifejtett, visszalépés nélküli alakja: a Java a
    // váltakozó ismétlést rekurzióval futtatja, hosszú szövegen elfogyna a verem.
    private const val JS_STR = "([^\"\\\\]*(?:\\\\[^\\n][^\"\\\\]*)*)"
    private val LIT = Regex("\\{id:\"([^\"]+)\",name:\"$JS_STR\"")
    private val FIELD = listOf("name", "category", "inspiredBy", "scentNote", "volume")
        .associateWith { Regex(it + ":\"$JS_STR\"") }
    private val PRICE = Regex("price(Single|[0-9]+):([0-9.e+]+)")
    private val SORG = Regex("gg\\(\"([a-z-]+)\",\"([^\"]*)\",\\[")
    private val SORG_ITEM = Regex("\\{no:\"([^\"]*)\",name:\"([^\"]*)\"([^{}]*)\\}")
    private val LF_ROW = Regex("\\[\"([^\"]*)\",\"([^\"]*)\",\"([^\"]*)\"\\]")
    private val BRAND = Regex("brand:\"([^\"]*)\"")

    /** Lion Francesco: (változónév, kategória, ár, ml) — fix áras sorok. */
    private val LF = listOf(
        LfList("Vae", "lf-ferfi", 5000, 50), LfList("Uae", "lf-noi", 5000, 50),
        LfList("Hae", "lf-unisex", 7000, 60)
    )

    private class LfList(val name: String, val category: String, val price: Int, val ml: Int)

    private val COLLECTIONS = listOf(
        "^fp-" to "Francesco Petroni", "^feromon-" to "Feromon",
        "^iyaly-" to "Iyaly", "^america$" to "America / US Prestige",
        "^bies$" to "Bi-es / Fabio Verso", "^chatler-" to "Chatler",
        "^cl-" to "Creation Lamis", "^cuba-" to "Cuba", "^jf-" to "J.Fenzi",
        "^lx-|^luxure-" to "Luxure", "^nb-" to "New Brand",
        "^ly-" to "Luxury", "^vv-" to "VV Love", "^es-" to "Essens",
        "^one-avenue$" to "One Avenue", "^niche-olcso$" to "Olcsó niche ihletésű",
        "^lf-" to "Lion Francesco", "^sorgenta-" to "Sorgenta",
        "^mylance$" to "My Lance", "^testpermet$" to "Bea's testpermet",
        "^(ferfi|noi|unisex)$" to "Bea's"
    ).map { (re, name) -> Regex(re) to name }

    /** Egy kiszerelés: ml (null = „Single", egyetlen méret) és ár forintban. */
    private data class Price(val ml: Int?, val price: Int)

    private class Raw(
        val id: String, val name: String?, val category: String?, val inspiredBy: String?,
        val notes: String?, val volume: String?, val prices: List<Price>
    )

    /** A `start`-nál nyíló zárójel párjának vége (a karakterláncokon át). */
    private fun end(t: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        var quote: Char? = null
        var esc = false
        var i = start
        while (i < t.length) {
            val c = t[i]
            if (quote != null) {
                if (esc) esc = false
                else if (c == '\\') esc = true
                else if (c == quote) quote = null
            } else if (c == '"' || c == '\'' || c == '`') {
                quote = c
            } else if (c == open) {
                depth++
            } else if (c == close) {
                depth--
                if (depth == 0) return i + 1
            }
            i++
        }
        return i
    }

    /**
     * A JS-szöveg kiszabadítása: a Python
     * `s.encode("latin-1", "backslashreplace").decode("unicode_escape")`
     * lépésről lépésre. Hiba esetén (csonka \x, \u…) a szöveg változatlan,
     * mint a Pythonban.
     */
    private fun js(s: String?): String? {
        if (s == null || s.indexOf('\\') < 0) return s
        // 1. latin-1 + backslashreplace: 0xFF fölött \uXXXX / \UXXXXXXXX
        val b = StringBuilder()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            when {
                cp <= 0xFF -> b.append(cp.toChar())
                cp <= 0xFFFF -> b.append("\\u").append(hex(cp, 4))
                else -> b.append("\\U").append(hex(cp, 8))
            }
            i += Character.charCount(cp)
        }
        return unicodeEscape(b.toString()) ?: s
    }

    private fun hex(v: Int, width: Int): String = Integer.toHexString(v).padStart(width, '0')

    private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    /** A Python `unicode_escape` dekódoló (3.11); hibánál null. */
    private fun unicodeEscape(e: String): String? {
        val out = StringBuilder(e.length)
        var i = 0
        while (i < e.length) {
            val c = e[i]
            if (c != '\\') { out.append(c); i++; continue }
            if (i + 1 >= e.length) return null           // „\ at end of string"
            val n = e[i + 1]
            i += 2
            when (n) {
                '\n' -> {}
                '\\', '\'', '"' -> out.append(n)
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                't' -> out.append('\t')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                'v' -> out.append('\u000B')
                'a' -> out.append('\u0007')
                in '0'..'7' -> {
                    var v = n - '0'
                    var k = 0
                    while (k < 2 && i < e.length && e[i] in '0'..'7') { v = v * 8 + (e[i] - '0'); i++; k++ }
                    out.append(v.toChar())
                }
                'x', 'u', 'U' -> {
                    val w = when (n) { 'x' -> 2; 'u' -> 4; else -> 8 }
                    if (i + w > e.length || !(i until i + w).all { isHex(e[it]) }) return null
                    val v = e.substring(i, i + w).toLong(16)
                    if (v > 0x10FFFF) return null                // „illegal Unicode character"
                    out.appendCodePoint(v.toInt())
                    i += w
                }
                // \N{…}: a Unicode-névtábla a telefonon nem biztos, hogy elérhető — a
                // Python itt dekódolna; a csomagban nem fordul elő
                'N' -> return null
                else -> out.append('\\').append(n)           // ismeretlen: marad
            }
        }
        return out.toString()
    }

    private fun prices(text: String): List<Price> {
        val out = mutableListOf<Price>()
        val seen = mutableSetOf<Price>()
        for (m in PRICE.findAll(text)) {
            val ml = if (m.groupValues[1] == "Single") null else m.groupValues[1].toIntOrNull() ?: continue
            val price = m.groupValues[2].toDoubleOrNull()?.toInt() ?: continue
            if (price <= 100 || !seen.add(Price(ml, price))) continue
            out += Price(ml, price)
        }
        return out.sortedWith(compareBy<Price>({ it.ml ?: 1_000_000_000 }, { it.price }))
    }

    private fun rawItems(t: String): List<Raw> {
        val out = mutableListOf<Raw>()
        for (m in LIT.findAll(t)) {
            if (m.groupValues[1] in SKIP) continue
            val start = m.range.first
            val lit = t.substring(start, end(t, start, '{', '}'))
            fun g(k: String): String? = FIELD.getValue(k).find(lit)?.let { js(it.groupValues[1]) }
            out += Raw(
                m.groupValues[1], g("name"), g("category"), g("inspiredBy"),
                g("scentNote"), g("volume"), prices(lit)
            )
        }
        for (lf in LF) {
            val i = t.indexOf(lf.name + "=[[")
            if (i < 0) continue
            val start = i + lf.name.length + 1
            val lit = t.substring(start, end(t, start, '[', ']'))
            for (mm in LF_ROW.findAll(lit)) {
                out += Raw(
                    mm.groupValues[1], "Scent of " + mm.groupValues[2], lf.category,
                    mm.groupValues[3], null, null, listOf(Price(lf.ml, lf.price))
                )
            }
        }
        for (g in SORG.findAll(t)) {
            val start = g.range.last
            val lit = t.substring(start, end(t, start, '[', ']'))
            for (om in SORG_ITEM.findAll(lit)) {
                val brand = BRAND.find(om.groupValues[3])
                out += Raw(
                    "SORG-" + om.groupValues[1], om.groupValues[2], g.groupValues[1],
                    brand?.let { "${it.groupValues[1]} – ${om.groupValues[2]}" },
                    null, null, prices(om.groupValues[3])
                )
            }
        }
        return out
    }

    /** A bolt kategória-kódjából („fp-ferfi") a kollekció neve. */
    fun collection(category: String?): String =
        COLLECTIONS.firstOrNull { it.first.containsMatchIn(category.orEmpty()) }?.second ?: "Egyéb"

    // Python: (\d{1,3})\s*ml\b re.I — Unicode-számjegy, -szóköz és -szóhatár
    private val ML = Regex(
        "(\\p{Nd}{1,3})${PyText.SPACE_CLASS}*[mM][lL](?![\\p{L}\\p{N}_])"
    )

    private fun mlFromName(name: String?, volume: String?): Int? =
        ML.find("${volume.orEmpty()} ${name.orEmpty()}")?.groupValues?.get(1)?.toInt()

    /** A `<script src="/assets/index-….js">` útvonala, ha van. */
    fun bundlePath(html: String): String? = BUNDLE.find(html)?.groupValues?.get(1)

    fun parse(bundleJs: String): List<OfferItem> {
        val out = mutableListOf<OfferItem>()
        val seen = mutableSetOf<String>()
        for (r in rawItems(bundleJs)) {
            if (r.id.isEmpty() || r.id in seen || r.name.isNullOrEmpty()) continue
            seen += r.id
            val pot = mlFromName(r.name, r.volume)
            val prices = r.prices.map { Price(it.ml ?: pot, it.price) }
            if (prices.isEmpty()) continue
            // az első legolcsóbb (a Python min() is az elsőt adja)
            val firstIdx = prices.indices.minByOrNull { prices[it].price }!!
            val first = prices[firstIdx]
            val others = prices.filterIndexed { i, _ -> i != firstIdx }
            val note = mutableListOf<String>()
            if (!r.inspiredBy.isNullOrEmpty()) note += "Ihlette: ${r.inspiredBy}"
            if (!r.notes.isNullOrEmpty() && r.notes != r.inspiredBy) note += "Illatjegyek: ${r.notes}"
            if (others.isNotEmpty()) {
                note += "Más kiszerelés: " + others.joinToString(", ") { p ->
                    (if (p.ml != null && p.ml != 0) "${p.ml} ml " else "") + "${p.price} forint"
                }
            }
            val pack = if (first.ml != null && first.ml != 0) "${first.ml} ml" else r.volume.orEmpty()
            out += OfferItem(
                store = STORE, name = r.name, price = first.price, packSize = pack,
                category = collection(r.category), note = note.joinToString(". "),
                code = r.id, group = "Parfüm és illat"
            )
        }
        // Python: sort(key=(kategoria, nev.lower())) — stabil, kódpont-sorrend
        return out.sortedWith(Comparator { a, b ->
            PyText.CODEPOINT_ORDER.compare(a.category, b.category).takeIf { it != 0 }
                ?: PyText.CODEPOINT_ORDER.compare(a.name.lowercase(), b.name.lowercase())
        })
    }

    fun download(get: (String) -> String, progress: (String) -> Unit = {}): List<OfferItem> {
        progress("Illatorium: a kínálat betöltése…")
        val path = bundlePath(get(PAGE))
            ?: throw IllegalStateException("Az illatorium.hu oldalán nem találom a termékadatokat.")
        return parse(get(BASE + path))
    }
}
