package com.superdl.launcher.offers

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Pepco, Libri, Illatorium: a valódi oldalakról lementett nyers bájtokon a
 * Kotlin-átiratnak TÉTELRE és MEZŐRE PONTOSAN ugyanazt kell adnia, mint a
 * Windows-modulnak (`elvart.json`). A letöltést hamis `get` játssza el az
 * `index.json` (cím → fájl) alapján. (A minták git-ignored mappában vannak.)
 */
class NewStoresParityTest {

    private val dir = File("src/test/resources/akcio_minta")

    private fun index(store: String): Map<String, File>? {
        val f = File(dir, "$store/index.json")
        if (!f.exists() || !File(dir, "$store/elvart.json").exists()) return null
        val o = JSONObject(f.readText(Charsets.UTF_8))
        return o.keys().asSequence().associateWith { File(dir, "$store/" + o.getString(it)) }
    }

    private fun bytes(idx: Map<String, File>, url: String): ByteArray =
        (idx[url] ?: throw java.io.IOException("nincs mintában: $url")).readBytes()

    private fun str(o: JSONObject, k: String): String = if (o.isNull(k)) "" else o.getString(k)
    private fun int(o: JSONObject, k: String): Int? = if (o.isNull(k)) null else o.getInt(k)

    private fun compare(label: String, store: String, items: List<OfferItem>) {
        val exp = JSONArray(File(dir, "$store/elvart.json").readText(Charsets.UTF_8))
        println("$label: ${items.size} (Python: ${exp.length()})")
        items.take(2).forEach { println("  ${it.name} | ${it.price} | ${it.oldPrice} | ${it.discount} | ${it.packSize} | ${it.category} | ${it.note}") }
        assertEquals("$label darabszám", exp.length(), items.size)
        for (i in 0 until exp.length()) {
            val e = exp.getJSONObject(i)
            val a = items[i]
            val at = "$label #$i (${str(e, "kod")})"
            assertEquals("$at bolt", str(e, "bolt"), a.store)
            assertEquals("$at név", str(e, "nev"), a.name)
            assertEquals("$at ár", int(e, "ar"), a.price)
            assertEquals("$at kártyás ár", int(e, "kartyas_ar"), a.cardPrice)
            assertEquals("$at régi ár", int(e, "regi_ar"), a.oldPrice)
            assertEquals("$at kedvezmény", str(e, "kedvezmeny"), a.discount)
            assertEquals("$at kiszerelés", str(e, "kiszereles"), a.packSize)
            assertEquals("$at egységár", str(e, "egysegar"), a.unitPrice)
            assertEquals("$at érvényes", str(e, "ervenyes"), a.validity)
            assertEquals("$at kategória", str(e, "kategoria"), a.category)
            assertEquals("$at kód", str(e, "kod"), a.code)
            assertEquals("$at megjegyzés", str(e, "megjegyzes"), a.note)
            assertEquals("$at csoport", str(e, "csoport"), a.group)
            assertEquals("$at url", str(e, "url"), a.url)
        }
    }

    @Test
    fun pepcoUgyanaz() {
        val idx = index("pepco")
        assumeTrue("nincs pepco minta", idx != null)
        val items = PepcoOffers.download({ String(bytes(idx!!, it), Charsets.UTF_8) }, {})
        compare("PEPCO", "pepco", items)
    }

    @Test
    fun libriUgyanaz() {
        val idx = index("libri")
        assumeTrue("nincs libri minta", idx != null)
        val msgs = mutableListOf<String>()
        val items = LibriOffers.download({ bytes(idx!!, it) }, { msgs += it }, pause = 0)
        println("LIBRI jelzések: $msgs")
        compare("LIBRI", "libri", items)
    }

    @Test
    fun illatoriumUgyanaz() {
        val idx = index("illatorium")
        assumeTrue("nincs illatorium minta", idx != null)
        val items = IllatoriumOffers.download({ String(bytes(idx!!, it), Charsets.UTF_8) }, {})
        compare("ILLATORIUM", "illatorium", items)
    }
}
