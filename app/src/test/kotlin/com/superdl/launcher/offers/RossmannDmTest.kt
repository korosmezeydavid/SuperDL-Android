package com.superdl.launcher.offers

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Rossmann és dm: a felderítő Python-szkript lementett nyers válaszain a
 * Kotlin-átiratnak ugyanazt a nevet, árat, kártyás és régi árat kell adnia,
 * mint a Python-átalakítás. (A minták git-ignored mappában vannak.)
 */
class RossmannDmTest {

    private val dir = File("src/test/resources/akcio_minta")

    private fun intOrNull(o: JSONObject, k: String): Int? =
        if (o.isNull(k) || !o.has(k)) null else Math.round(o.getDouble(k)).toInt()

    @Test
    fun dmUgyanaz() {
        val raw = File(dir, "dm/dm_kiarusitas_raw.json")
        val parsed = File(dir, "dm/dm_kiarusitas_parsed.json")
        assumeTrue("nincs dm minta", raw.exists() && parsed.exists())
        val items = DmOffers.parse(raw.readText(Charsets.UTF_8))
        val exp = JSONArray(parsed.readText(Charsets.UTF_8))
        println("DM: ${items.size} (Python: ${exp.length()})")
        items.take(3).forEach { println("  ${it.name} | ${it.price} | ${it.oldPrice} | ${it.packSize} | ${it.unitPrice} | ${it.category}") }
        assertEquals(exp.length(), items.size)
        for (i in 0 until exp.length()) {
            val e = exp.getJSONObject(i)
            assertEquals(e.getString("name"), items[i].name)
            assertEquals(intOrNull(e, "price"), items[i].price)
            assertEquals(intOrNull(e, "old_price"), items[i].oldPrice)
        }
    }

    @Test
    fun rossmannUgyanaz() {
        val raw = File(dir, "rossmann/sample_gql_first100_page2.json")
        val parsed = File(dir, "rossmann/sample_rossmann_offers.json")
        assumeTrue("nincs rossmann minta", raw.exists() && parsed.exists())
        val data = JSONObject(raw.readText(Charsets.UTF_8)).getJSONObject("data")
            .getJSONObject("listProductsByCategory").getJSONArray("data")
        val byId = HashMap<String, JSONObject>()
        val exp = JSONArray(parsed.readText(Charsets.UTF_8))
        for (i in 0 until exp.length()) exp.getJSONObject(i).let { byId[it.get("id").toString()] = it }
        var compared = 0
        for (i in 0 until data.length()) {
            val item = RossmannOffers.parse(data.getJSONObject(i))
            val e = byId[item.code] ?: continue
            compared++
            assertEquals(e.getString("name"), item.name)
            assertEquals(intOrNull(e, "price"), item.price)
            assertEquals(intOrNull(e, "card_price"), item.cardPrice)
            assertEquals(intOrNull(e, "old_price"), item.oldPrice)
            if (!e.isNull("pack")) assertEquals(e.getString("pack"), item.packSize)
        }
        println("ROSSMANN: ${data.length()} nyers termékből $compared összevetve")
        RossmannOffers.parse(data.getJSONObject(0)).let {
            println("  ${it.name} | ${it.price} | ${it.cardPrice} | ${it.oldPrice} | ${it.packSize} | ${it.unitPrice} | ${it.category}")
        }
        assertTrue("legalább 50 termék összevethető", compared >= 50)
    }
}
