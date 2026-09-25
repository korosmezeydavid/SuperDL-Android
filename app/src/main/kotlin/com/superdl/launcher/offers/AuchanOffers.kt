package com.superdl.launcher.offers

import org.json.JSONArray
import java.time.LocalDate

/**
 * Auchan — a heti (és tematikus) katalógusok iPaper-szövegrétegéből.
 *
 * A Windows-oldali kutatás `auchan_offers.py` gyűjtőjének átirata.
 *   Felfedezés: auchan.hu/api/v2/cache/catalog/list?availability=current&storeType=hyper|super (JSON)
 *   Szöveg:     a katalógus `flipbookUrl` néző-oldala → `"pageTexts":[…]`
 *   Párosítás:  ár = kiszerelés × egységár (Ft/kg, Ft/l …), az oldal ár-tokenjeivel ellenőrizve.
 *
 * A név-sorok CSUPA NAGYBETŰSEK („CSIRKEMELLFILÉ"), utánuk a leírás a
 * kiszereléssel és az egységárral; a nagy árcímkék szövege máshol áll.
 *
 * SZÁNDÉKOS SZIGOR: csak az a termék marad, amelynek az ára igazolt (a
 * számolt ár ott van az oldalon, vagy a „2 db: N Ft" kiírás). A kártyás és az
 * eredeti ár is csak igazolva marad meg — egy vak felhasználónak inkább
 * kevesebbet mondjunk, mint rosszat.
 */
object AuchanOffers {

    const val STORE = "Auchan"
    private const val LIST = "https://auchan.hu/api/v2/cache/catalog/list?availability=current&storeType="
    const val CARD_NAME = "Auchan Bizalomkártyával"

    private val S = IPaper.S
    private val NS = IPaper.NS
    private val WA = IPaper.W_AFTER

    private val LOW = Regex("[a-záéíóöőúüű]")
    private val LET = Regex("[A-Za-zÁÉÍÓÖŐÚÜŰáéíóöőúüű]")
    private val CODE = Regex("\\d+_[A-Z]{2}")
    private val NOISE = listOf("ÚJDONSÁG", "NORMÁL ELADÁSI", "NYEREMÉNY", "MATRICA", "SMEG", "TÉNYLEG", "ELINDULT", "AUCHAN.HU", "WWW.")
    private val SPACES = Regex("[" + Char(0x2009) + Char(0x200A) + Char(0x202F) + Char(0xA0) + "]")
    private val SOFT_HYPHEN = Char(0xAD).toString()
    private val TOKEN = Regex("$NS+")
    private val NUMTOK = Regex("[\\d+.,x%/-]+")
    private const val NUM = "\\d{1,3}(?: \\d{1,3})*(?:,\\d+)?"
    private val PRICE_TOK = Regex("(Bizalomkártyával:$S*|Tényleg ennyi:$S*)?($NUM) ?Ft(?: ?/(10 dkg|db|kg|l))?")
    private val UNIT_RX = Regex("((?:$NUM)(?:/$NUM)*) ?Ft/(kg|l|db|tekercs|lap|mosás|m|csomag|pár)$WA")
    private val PACK_RX = Regex("(?<![\\d/])(\\d+(?:,\\d+)?(?:/\\d+(?:,\\d+)?)*)$S?(?:x$S?(\\d+(?:,\\d+)?)$S?)?(kg|dkg|g|ml|cl|l)$WA")
    private val MULTI_RX = Regex("(\\d+(?:\\+\\d+)?) db: ($NUM) ?Ft")
    private val CUT = Regex("$S\\d+_[A-Z]{2}$WA|$S-\\d+ %|Tényleg ennyi:")

    private fun num(s: String): Double =
        s.replace(" ", "").replace(Char(0xA0).toString(), "").replace(",", ".").toDouble()

    /** Egy termék, ahogy a Python is látja; `verified` = az ár igazolt. */
    data class Product(
        val name: String,
        val page: Int,
        val pack: String?,
        val price: Double?,
        val oldPrice: Double?,
        val cardPrice: Double?,
        val unitPrice: String?,
        val multi: String?,
        val condition: String?,
        val verified: Boolean,
        val oldVerified: Boolean,
        val cardVerified: Boolean?
    )

    private fun isCaps(tok: String): Boolean {
        val w = tok.trim { it in ",.*!:;()\"'" }
        if (CODE.matches(w)) return false
        return LET.findAll(w).count() >= 2 && !LOW.containsMatchIn(w)
    }

    private class Run(val s: Int, val e: Int, val name: String)

    /** A CSUPA NAGYBETŰS szó-sorozatok (számok is, ha nagybetűs szó követi őket). */
    private fun splitNames(text: String): List<Run> {
        val toks = TOKEN.findAll(text).map { Triple(it.range.first, it.range.last + 1, it.value) }.toList()
        fun numcap(k: Int) = k + 1 < toks.size && NUMTOK.matches(toks[k].third) && !CODE.matches(toks[k].third) &&
            isCaps(toks[k + 1].third)
        val runs = mutableListOf<Run>()
        var i = 0
        while (i < toks.size) {
            if (isCaps(toks[i].third) || numcap(i)) {
                var j = i
                while (j + 1 < toks.size && (isCaps(toks[j + 1].third) || numcap(j + 1))) j++
                runs += Run(toks[i].first, toks[j].second, text.substring(toks[i].first, toks[j].second))
                i = j + 1
            } else i++
        }
        return runs
    }

    private fun priceTokens(region: String): List<Pair<Double, String>> =
        PRICE_TOK.findAll(region).map { m ->
            val pre = m.groups[1]?.value
            var kind = if (pre != null && pre.startsWith("Bizalom")) "card" else if (pre != null) "fix" else "ft"
            val per = m.groups[3]?.value
            var v = num(m.groupValues[2])
            if (per == "10 dkg") { v *= 10; kind += "/kg" } else if (per != null) kind += "/$per"
            v to kind
        }.toList()

    /** (mennyiség kg-ban / l-ben, a kiszerelés szövege) az első változatból. */
    private fun packAmount(details: String): Pair<Double?, String?> {
        val m = PACK_RX.find(details) ?: return null to null
        val first = num(m.groupValues[1].split("/")[0])
        val g2 = m.groups[2]?.value
        val amt = if (g2 != null) first * num(g2) else first
        val f = when (m.groupValues[3]) { "g" -> 0.001; "dkg" -> 0.01; "ml" -> 0.001; "cl" -> 0.01; else -> 1.0 }
        return amt * f to m.value
    }

    private fun match(v: Double, toks: List<Pair<Double, String>>, kinds: Set<String>): Double? {
        var best: Double? = null
        for ((t, k) in toks) {
            if (k !in kinds) continue
            val d = Math.abs(t - v)
            if (d <= maxOf(3.0, 0.02 * v) && (best == null || d < Math.abs(best - v))) best = t
        }
        return best?.takeIf { it != 0.0 }      // Python: `if t` — a 0 hamis
    }

    private fun firstVals(part: String, unit: String?): Pair<String?, List<Double>> {
        var u0 = unit
        val vals = mutableListOf<Double>()
        for (m in UNIT_RX.findAll(part)) {
            val u = m.groupValues[2]
            if (u0 == null) u0 = u
            if (u == u0) vals += num(m.groupValues[1].split("/")[0])
        }
        return u0 to vals
    }

    private class Cand(val start: Int, val end: Int, val name: String, val det: String, val cond: String?)

    private val FT_KINDS = setOf("ft", "fix", "ft/db", "card")
    private val OLD_KINDS = setOf("ft", "fix")
    private val KG_KINDS = setOf("ft/kg", "ft", "fix")
    private val CARD_KINDS = setOf("card")
    private val COUNT_UNITS = setOf("tekercs", "lap", "mosás", "db", "pár")

    /** Egy oldal szövege → MINDEN jelölt termék (igazolt és igazolatlan is), a Python sorrendjében. */
    fun parsePageAll(raw: String, pageNo: Int): List<Product> {
        val text = SPACES.replace(raw, " ")     // az iPaper hajszál/keskeny szóközt tesz az ezresek közé
        val runs = splitNames(text)
        val prods = mutableListOf<Cand>()
        for ((k, run) in runs.withIndex()) {
            val nxt = if (k + 1 < runs.size) runs[k + 1].s else text.length
            val tail = text.substring(run.e, nxt)
            var det = tail
            CUT.find(det)?.let { det = det.substring(0, it.range.first) }   // az árblokk az utolsó név után is jöhet
            det = det.trim { it == ' ' || it == ',' }
            var name = run.name
            var cond: String? = null
            if (name.contains("ESETÉN")) {
                val a = name.substringBefore("ESETÉN")
                val b2 = name.substringAfter("ESETÉN")
                cond = (a + "ESETÉN").trim { it == '*' || it == ' ' }
                name = b2.trim()
            }
            if (name.startsWith("$pageNo ")) name = name.substring("$pageNo ".length)   // az oldalszám a névhez tapadt
            if (name.isEmpty() || NOISE.any { name.contains(it) } || name.length < 4) continue
            if (!(det.contains("Ft/") || PACK_RX.containsMatchIn(det) || det.contains(" db:"))) continue
            val lead = tail.length - tail.trimStart { it == ' ' || it == ',' }.length
            prods += Cand(run.s, run.e + lead + det.length,
                name.replace("$SOFT_HYPHEN ", "").replace(SOFT_HYPHEN, ""), det, cond)
        }
        if (prods.isEmpty()) return emptyList()
        // ár-tokenek = az oldal szövege a termékek név+leírás szakaszai nélkül
        val region = mutableListOf<String>()
        var last = 0
        for (p in prods) {
            region += if (p.start >= last) text.substring(last, p.start) else ""
            last = p.end
        }
        region += if (last <= text.length) text.substring(last) else ""
        val toks = priceTokens(region.joinToString(" | "))
        val out = mutableListOf<Product>()
        for (p in prods) {
            val det = p.det
            val main = det.substringBefore("Bizalomkártyával:")
            val card = if (det.contains("Bizalomkártyával:")) det.substringAfter("Bizalomkártyával:") else ""
            var (amt, pack) = packAmount(main)
            val (u, vals) = firstVals(main, null)
            if (u != null && u in COUNT_UNITS && amt == null) {
                Regex("(\\d+)(?:/\\d+)* " + u).find(main)?.let { amt = it.groupValues[1].toDouble(); pack = it.value }
            }
            val multi = MULTI_RX.findAll(det).map { it.groupValues[1] to it.groupValues[2] }.toList()
            val hasAmt = amt != null && amt != 0.0
            var price: Double? = null
            var old: Double? = null
            var oldOk = false
            var verified = false
            var unit: String? = null
            var cardPrice: Double? = null
            var cardOk: Boolean? = null
            if (vals.isNotEmpty()) {
                unit = IPaper.pyG(vals.last()) + " Ft/" + u
                if (hasAmt) {
                    val new = amt!! * vals.last()
                    val t = match(new, toks, FT_KINDS)
                    if (t != null) { price = t; verified = true } else price = new   // (a Python kerekít; úgyis eldobjuk)
                    if (vals.size > 1) {
                        val o = amt!! * vals[0]
                        val to = match(o, toks, OLD_KINDS)
                        old = to ?: o
                        oldOk = to != null
                    }
                } else if (u == "kg") {
                    price = vals.last(); pack = "1 kg"
                    verified = match(vals.last(), toks, KG_KINDS) != null
                    if (vals.size > 1) { old = vals[0]; oldOk = true }
                }
            }
            val (cu, cvals) = firstVals(card, u)
            if (cvals.isNotEmpty()) {
                if (hasAmt) {
                    // a szöveg sorrendje nem tökéletes: minden kártyás egységárat kipróbálunk
                    val hits = cvals.mapNotNull { cv -> match(amt!! * cv, toks, CARD_KINDS) }
                    cardPrice = hits.firstOrNull() ?: (amt!! * cvals[0])
                    cardOk = hits.isNotEmpty()
                } else if (cu == "kg") cardPrice = cvals[0]
            }
            if (multi.isNotEmpty() && price == null) { price = num(multi[0].second); verified = true }
            out += Product(
                name = p.name, page = pageNo, pack = pack, price = price, oldPrice = old, cardPrice = cardPrice,
                unitPrice = unit, multi = multi.joinToString("; ") { "${it.first} db: ${it.second} Ft" }.ifEmpty { null },
                condition = p.cond, verified = verified, oldVerified = oldOk, cardVerified = cardOk
            )
        }
        return out
    }

    /**
     * Egy oldal MEGTARTOTT termékei: csak igazolt árral. Az igazolatlan kártyás
     * és eredeti árat is elhagyjuk (a /kg-os kártyás ár nyomtatva áll, az marad).
     */
    fun parsePage(raw: String, pageNo: Int): List<Product> =
        parsePageAll(raw, pageNo).filter { it.verified && it.price != null }.map {
            it.copy(
                cardPrice = if (it.cardVerified != false) it.cardPrice else null,
                oldPrice = if (it.oldVerified) it.oldPrice else null
            )
        }

    /** Egy katalógus a listából. */
    data class Catalog(val id: Int, val title: String, val flipbookUrl: String, val from: String, val to: String)

    /** A catalog/list JSON-tömbje → katalógusok (legfeljebb 10, mint a Pythonban). */
    fun catalogs(json: String): List<Catalog> {
        val arr = JSONArray(json)
        return (0 until minOf(arr.length(), 10)).mapNotNull { arr.optJSONObject(it) }.map { c ->
            Catalog(
                c.optInt("id"), c.optString("title"), c.optString("flipbookUrl"),
                c.optString("availabilityFromDate").take(10), c.optString("availabilityToDate").take(10)
            )
        }
    }

    private fun validToday(c: Catalog, today: LocalDate): Boolean = try {
        !today.isBefore(LocalDate.parse(c.from)) && !today.isAfter(LocalDate.parse(c.to))
    } catch (e: Exception) {
        true                                    // nincs értelmes dátum: a lista „current"-nek mondta
    }

    fun toOfferItem(p: Product, c: Catalog, code: String): OfferItem {
        val name = if (OfferText.isUpper(p.name)) OfferText.capitalize(p.name) else p.name
        val notes = listOfNotNull(p.condition?.let { OfferText.capitalize(it) }, p.multi)
        val validity = if (c.from.isNotEmpty() && c.to.isNotEmpty())
            "${IPaper.mmdd(c.from)}-tól ${IPaper.mmdd(c.to)}-ig" else ""
        return OfferItem(
            store = STORE, name = name,
            price = p.price?.let { Math.round(it).toInt() },
            cardPrice = p.cardPrice?.let { Math.round(it).toInt() },
            cardName = if (p.cardPrice != null) CARD_NAME else "",
            oldPrice = p.oldPrice?.let { Math.round(it).toInt() },
            packSize = p.pack.orEmpty(),
            unitPrice = p.unitPrice.orEmpty().replace('.', ','),
            validity = validity, category = c.title, code = code, note = notes.joinToString("; ")
        )
    }

    /**
     * Az összes MA érvényes Auchan-katalógus (hipermarket + szupermarket, a
     * közösek egyszer) igazolt árú termékei.
     * Hálózat: 2 lista + katalógusonként 1 néző-oldal (most 7 katalógus ≈ 0,6 MB).
     */
    fun download(
        get: (String) -> String,
        progress: (String) -> Unit = {},
        today: LocalDate = LocalDate.now()
    ): List<OfferItem> {
        val cats = mutableListOf<Catalog>()
        val seen = mutableSetOf<Int>()
        var listOk = false
        for (type in listOf("hyper", "super")) {
            val json = try { get(LIST + type) } catch (e: Exception) { null } ?: continue
            val list = try { catalogs(json) } catch (e: Exception) { null } ?: continue
            listOk = true
            for (c in list) if (seen.add(c.id) && validToday(c, today)) cats += c
        }
        if (!listOk) throw IllegalStateException("Az Auchan katalógus-listája nem érhető el.")
        val out = mutableListOf<OfferItem>()
        for (c in cats) {
            progress(c.title)
            val html = try { get(c.flipbookUrl) } catch (e: Exception) { null } ?: continue
            val pages = IPaper.pageTexts(html) ?: continue
            pages.forEachIndexed { i, t ->
                parsePage(t, i + 1).forEachIndexed { n, p -> out += toOfferItem(p, c, "auchan:${c.id}:${i + 1}:${n + 1}") }
            }
        }
        return out
    }
}
