package com.superdl.launcher.offers

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * EGYEZÉS A WINDOWS-MODULLAL.
 *
 * A `tools/akcio_minta.py` lementi a boltok oldalait, és a Windows-modul
 * (penny.py, aldi.py) lefuttatja rajtuk a saját gyűjtőjét. Ez a próba
 * UGYANAZOKAT a fájlokat adja a Kotlin-átiratnak — és ugyanannyi terméket,
 * ugyanazokkal az árakkal kell kapnia. Net nem kell hozzá.
 */
class OffersParityTest {

    private val dir = File("src/test/resources/akcio_minta")

    private fun loader(): ((String) -> String)? {
        val idx = File(dir, "index.json")
        if (!idx.exists()) return null
        val map = JSONObject(idx.readText(Charsets.UTF_8))
        return { url ->
            val name = map.optString(url, "")
            if (name.isEmpty()) throw java.io.IOException("nincs lementve: $url")
            File(dir, name).readText(Charsets.UTF_8)
        }
    }

    private fun expected(): JSONObject = JSONObject(File(dir, "elvart.json").readText(Charsets.UTF_8))

    @Test
    fun pennyUgyanannyi() {
        val get = loader()
        assumeTrue("nincs minta", get != null)
        val items = PennyOffers.download(get!!)
        val exp = expected()
        println("PENNY: ${items.size} (Windows: ${exp.getInt("penny")}), árral: ${items.count { it.price != null }}")
        items.take(5).forEach { println("  ${it.name} | ${it.price} | ${it.cardPrice} | ${it.category} | ${it.validity}") }
        assertEquals(exp.getInt("penny"), items.size)
        assertEquals(exp.getInt("penny_arral"), items.count { it.price != null })
        val first = exp.getJSONArray("penny_elso")
        for (i in 0 until first.length()) {
            val row = first.getJSONArray(i)
            assertEquals(row.getString(0), items[i].name)
            assertEquals(row.optInt(1), items[i].price ?: 0)
            assertEquals(row.getString(3), items[i].category)
        }
    }

    @Test
    fun aldiUgyanannyi() {
        val get = loader()
        assumeTrue("nincs minta", get != null)
        // A lementett minták 2026. 39. hetéből valók: a teszt dátuma
        // nem függhet attól, mikor futtatjuk a kiadási ellenőrzést.
        val items = AldiOffers.download(get!!, today = java.time.LocalDate.of(2026, 9, 24))
        val exp = expected()
        println("ALDI: ${items.size} (Windows: ${exp.getInt("aldi")}), árral: ${items.count { it.price != null }}")
        items.take(5).forEach { println("  ${it.name} | ${it.price} | ${it.packSize} | ${it.category} | ${it.validity}") }
        assertEquals(exp.getInt("aldi"), items.size)
        assertEquals(exp.getInt("aldi_arral"), items.count { it.price != null })
        val first = exp.getJSONArray("aldi_elso")
        for (i in 0 until first.length()) {
            val row = first.getJSONArray(i)
            assertEquals(row.getString(0), items[i].name)
            assertEquals(row.optInt(1), items[i].price ?: 0)
            assertEquals(row.getString(2), items[i].packSize)
        }
    }

    @Test
    fun ervenyessegKimondva() {
        assertEquals("szeptember 24. és 30. között", OfferText.speakValidity("09.24-tól 09.30-ig"))
        assertEquals("szeptember 29. és október 5. között", OfferText.speakValidity("09.29-tól 10.05-ig"))
        assertEquals("1169", OfferText.priceNumber("1 169 Ft").toString())
        assertEquals("rantott petrella", OfferText.plain("Rántott Petrella"))
    }
}
