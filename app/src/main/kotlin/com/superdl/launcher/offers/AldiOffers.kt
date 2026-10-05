package com.superdl.launcher.offers

import org.json.JSONArray
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.roundToInt

/**
 * Aldi — az Aldi saját lapozós újságjának SZÖVEGRÉTEGE (szorolap.aldi.hu).
 *
 * A Windows-oldali `aldi.py` Publitas-részének átirata. Az aldi.hu
 * weboldala böngésző-ujjlenyomatot vár, és minden mást 403-mal elutasít
 * (2026-09-25-én mérve a telefonéhoz hasonló klienssel is) — ezért a
 * telefonon az újság szövegrétege az egyetlen út. Ez sima szöveg, ugyanaz,
 * ami az újságban le van nyomtatva.
 *
 * A szöveg a nyomdai elrendezést követi, ezért az ár gyakran nem a név
 * mellett áll. Ami viszont mindig egyben van, az a termék blokkja: név,
 * kiszerelés, EGYSÉGÁR és cikkszám:
 *
 *     VAJAS RÚD
 *     150 g/csomag
 *     3 660 Ft/kg
 *     156305
 *
 * Az árat ebből SZÁMOLJUK: 0,150 kg × 3 660 Ft/kg = 549 Ft — ami pontosan a
 * kiírt ár (a Windows-oldalon a teljes újságon ellenőrizve).
 */
object AldiOffers {

    const val STORE = "Aldi"
    private const val BASE = "https://szorolap.aldi.hu"

    private val CODE = Regex("^\\d{6}$")
    private val UNIT_PRICE = Regex(
        "^([\\d   .,]+?)(?:\\s*/\\s*[\\d   .,]+)?\\s*Ft\\s*/\\s*" +
            "(kg|l|db|darab|m|m2|tekercs|mosás|pár)\\b",
        RegexOption.IGNORE_CASE
    )
    private val SIZE = Regex(
        "(\\d+(?:[,.]\\d+)?)\\s*(?:x\\s*(\\d+(?:[,.]\\d+)?)\\s*)?(kg|g|dkg|ml|cl|l|db)\\b",
        RegexOption.IGNORE_CASE
    )
    private val JUNK = Regex(
        "(^a kép illusztráció|dekoráció|kiegészítők|^szuper$|csak ennyi|" +
            "^többféle$|^\\*|lásd a hátoldalon|árkedvezmény|érvényben volt|" +
            "állandó kínálatunk|mostantól még több|^ft$|^vegán$|ai által készült|" +
            "^\\d{2}\\.\\s*\\d{2}\\.|csütörtök|szerdáig|készlet erejéig)",
        RegexOption.IGNORE_CASE
    )

    /** A lehetséges újságnevek: ez a hét, a jövő hét, a múlt hét — mindkét fajta. */
    fun leafletNames(today: LocalDate = LocalDate.now()): List<Triple<String, String, Int>> {
        val out = mutableListOf<Triple<String, String, Int>>()
        for (shift in listOf(0L, 1L, -1L)) {
            val d = today.plusWeeks(shift)
            val year = d.get(IsoFields.WEEK_BASED_YEAR)
            val week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            for (kind in listOf("aldi_online_akcios_ujsag", "aldi_kozepso_sor")) {
                out += Triple("%s_%d_kw%02d".format(kind, year, week), kind, week)
            }
        }
        return out
    }

    private fun num(s: String?): Double? =
        s.orEmpty().replace(" ", "").replace(" ", "").replace(" ", "")
            .replace(",", ".").trim().toDoubleOrNull()

    /** „150 g/csomag" + „3 660 Ft/kg" → 549. Ha nem egyértelmű: null. */
    fun computedPrice(size: String, unitPrice: String): Int? {
        val e = UNIT_PRICE.find(unitPrice.trim()) ?: return null
        if (e.range.first != 0) return null
        val per = num(e.groupValues[1]) ?: return null
        val base = e.groupValues[2].lowercase()
        if (base in listOf("db", "darab", "tekercs", "pár", "mosás", "m")) {
            val m = Regex("(\\d+)\\s*(?:x\\s*\\d+\\s*\\w+\\s*)?(darab|db|tekercs|pár|mosás|m)\\b",
                RegexOption.IGNORE_CASE).find(size)
            val count = m?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val p = count * per
            return if (p > 0 && p < 1_000_000) p.roundToInt() else null
        }
        val m = SIZE.find(size) ?: return null
        var qty = num(m.groupValues[1]) ?: return null
        val mult = m.groupValues[2].takeIf { it.isNotBlank() }?.let { num(it) }
        val unit = m.groupValues[3].lowercase()
        if (mult != null) qty *= mult
        val factor = mapOf("g" to 0.001, "dkg" to 0.01, "kg" to 1.0, "ml" to 0.001, "cl" to 0.01, "l" to 1.0)[unit]
            ?: return null
        if (base == "kg" && unit in listOf("ml", "cl", "l")) return null
        if (base == "l" && unit in listOf("g", "dkg", "kg")) return null
        val p = qty * factor * per
        return if (p > 0 && p < 1_000_000) p.roundToInt() else null
    }

    /** „VAJAS RÚD" → „Vajas Rúd"; a rövidítések („A.D.") maradnak. */
    private fun nice(s: String): String =
        s.replace("*", "").replace(Regex("\\s+"), " ").trim().split(" ").joinToString(" ") { w ->
            val letters = w.replace("-", "").replace("’", "")
            if (w.length >= 2 && OfferText.isUpper(w) && letters.isNotEmpty() && letters.all { it.isLetter() })
                OfferText.capitalize(w) else w
        }

    fun products(pages: List<String>, leaflet: String, validity: String): List<OfferItem> {
        val out = mutableListOf<OfferItem>()
        for (text in pages) {
            val paragraphs = text.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
            var brand = ""
            for (p in paragraphs) {
                val lines = p.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (!(lines.size >= 3 && CODE.matches(lines.last()))) {
                    if (lines.size == 1 && OfferText.isUpper(lines[0]) && lines[0].length < 30 &&
                        !JUNK.containsMatchIn(lines[0])
                    ) brand = lines[0]
                    continue
                }
                val upIdx = lines.indexOfFirst { UNIT_PRICE.find(it)?.range?.first == 0 }
                if (upIdx < 0) continue
                val unitPrice = lines[upIdx]
                val sizeIdx = (upIdx - 1 downTo 0).firstOrNull { i -> SIZE.containsMatchIn(lines[i]) || lines[i].contains("/") }
                val size = sizeIdx?.let { lines[it] }.orEmpty()
                val end = sizeIdx ?: upIdx
                val names = lines.subList(0, end).filter { !JUNK.containsMatchIn(it) }
                if (names.isEmpty()) continue
                val big = names.filter { OfferText.isUpper(it) || it.endsWith("*") }
                val small = names.filter { it !in big }
                var name = nice((big.ifEmpty { names.take(1) }).joinToString(" "))
                if (brand.isNotBlank() && !name.contains(brand, ignoreCase = true)) name = nice(brand) + " " + name
                var price = computedPrice(size, unitPrice)
                if (price == null && unitPrice.replace(" ", "").lowercase().contains("/db")) {
                    price = OfferText.priceNumber(unitPrice)
                }
                out += OfferItem(
                    store = STORE, name = name, price = price,
                    packSize = size.replace(Regex("/.*$"), "").trim().ifBlank { size },
                    unitPrice = unitPrice.replace("  ", " "), validity = validity,
                    category = leaflet, code = lines.last(), note = small.joinToString(", ")
                )
                brand = ""
            }
        }
        return out
    }

    private fun validityOf(pages: List<String>): String {
        for (s in pages.take(3)) {
            Regex("(\\d{2})\\.(\\d{2})\\.\\s*C\\s*S\\s*Ü").find(s)?.let {
                return "${it.groupValues[1]}.${it.groupValues[2]}-tól"
            }
            Regex("(\\d{2})\\.(\\d{2})\\.\\s*[-–]\\s*(\\d{2})\\.(\\d{2})\\.").find(s)?.let {
                val g = it.groupValues
                return "${g[1]}.${g[2]}-tól ${g[3]}.${g[4]}-ig"
            }
        }
        return ""
    }

    /** Az aktuális Aldi-újságok termékei. Fajtánként a legfrissebb egy elég. */
    fun download(get: (String) -> String, progress: (String) -> Unit = {},
                 today: LocalDate = LocalDate.now()): List<OfferItem> {
        val out = mutableListOf<OfferItem>()
        val seen = mutableSetOf<String>()
        val kindsDone = mutableSetOf<String>()
        for ((name, kind, week) in leafletNames(today)) {
            if (kind in kindsDone) continue
            val raw = try { get("$BASE/$name/spreads.json") } catch (_: Exception) { continue }
            kindsDone += kind
            val title = if (kind.contains("akcios")) "Akciós újság, $week. hét"
            else "Középső sor (nem élelmiszer), $week. hét"
            progress(title)
            val pages = mutableListOf<String>()
            val spreads = JSONArray(raw)
            for (i in 0 until spreads.length()) {
                val pg = spreads.optJSONObject(i)?.optJSONArray("pages") ?: continue
                for (j in 0 until pg.length()) pages += pg.optJSONObject(j)?.optString("text").orEmpty()
            }
            val validity = validityOf(pages)
            for (t in products(pages, title, validity)) if (seen.add(t.code)) out += t
        }
        return out
    }
}
