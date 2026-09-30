package com.superdl.launcher.callid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A szám leírása pontosan ezekkel a mondatokkal hangzik el.
 *
 * MIÉRT PONTOS SZÖVEG: vak felhasználónak ez az egyetlen visszajelzés. Ha a
 * libphonenumber egy frissítése mást ad vissza (pl. körzetnév angolul), azt
 * itt kell észrevenni, nem a telefonon.
 */
class NumberDescriberTest {

    private val portability = "a szám azóta átvihető másik szolgáltatóhoz"

    @Test
    fun telekom_mobile() {
        val info = NumberDescriber.describe("+36301234567")
        assertEquals(NumberDescriber.Kind.DOMESTIC, info.kind)
        assertEquals("+36301234567", info.e164)
        assertEquals("06301234567", info.dialable)
        assertEquals(
            "Magyar mobilszám, eredetileg a Telekom számtartományából — $portability.",
            info.description
        )
    }

    @Test
    fun yettel_mobile_in_national_format() {
        val info = NumberDescriber.describe("06 20 123 4567")
        assertEquals("+36201234567", info.e164)
        assertEquals(
            "Magyar mobilszám, eredetileg a Yettel számtartományából — $portability.",
            info.description
        )
    }

    @Test
    fun one_mobile() {
        assertEquals(
            "Magyar mobilszám, eredetileg a One számtartományából — $portability.",
            NumberDescriber.describe("+36701234567").description
        )
    }

    @Test
    fun budapest_landline() {
        val info = NumberDescriber.describe("+3612345678")
        assertEquals("+3612345678", info.e164)
        assertEquals("Magyar vezetékes szám, körzet: Budapest.", info.description)
    }

    @Test
    fun szeged_landline() {
        assertEquals(
            "Magyar vezetékes szám, körzet: Szeged.",
            NumberDescriber.describe("+3662123456").description
        )
    }

    @Test
    fun premium_rate_warns() {
        val expected = "Magyar emelt díjas szám — figyelem, a visszahívása drága lehet."
        assertEquals(expected, NumberDescriber.describe("+3690123456").description)
        assertEquals(expected, NumberDescriber.describe("0690123456").description)
        assertEquals("+3690123456", NumberDescriber.describe("0690123456").e164)
    }

    @Test
    fun toll_free() {
        assertEquals(
            "Magyar ingyenesen hívható zöld szám.",
            NumberDescriber.describe("+3680123456").description
        )
    }

    @Test
    fun austria_mobile_and_landline() {
        val mobile = NumberDescriber.describe("+43664123456")
        assertEquals(NumberDescriber.Kind.FOREIGN, mobile.kind)
        assertEquals("+43664123456", mobile.dialable)
        assertEquals(
            "Külföldi mobilszám, ország: Ausztria, eredetileg az A1 TA számtartományából — $portability.",
            mobile.description
        )
        assertEquals(
            "Külföldi vezetékes szám, ország: Ausztria.",
            NumberDescriber.describe("+4312345678").description
        )
    }

    @Test
    fun invalid_number() {
        val info = NumberDescriber.describe("+36123")
        assertEquals(NumberDescriber.Kind.INVALID, info.kind)
        assertNull(info.e164)
        assertEquals(
            "Ez nem érvényes telefonszám: hiányos, elírt, vagy nem illik egyik ismert számtervbe sem.",
            info.description
        )
    }

    @Test
    fun hidden_numbers() {
        for (raw in listOf("", "   ", "-2", "-1", "Private", "Unknown")) {
            val info = NumberDescriber.describe(raw)
            assertEquals(raw, NumberDescriber.Kind.HIDDEN, info.kind)
            assertEquals("Rejtett számot nem lehet azonosítani.", info.description)
        }
    }

    @Test
    fun short_numbers() {
        assertEquals("A 112 az egységes segélyhívó szám.", NumberDescriber.describe("112").description)
        assertEquals("A 104 a mentők hívószáma.", NumberDescriber.describe("104").description)
        assertEquals("A 105 a tűzoltók hívószáma.", NumberDescriber.describe("105").description)
        assertEquals("A 107 a rendőrség hívószáma.", NumberDescriber.describe("107").description)
        assertEquals("Az 1818 a Kormányzati Ügyfélvonal.", NumberDescriber.describe("1818").description)
        assertEquals("Az 1777 rövid szolgáltatói szám.", NumberDescriber.describe("1777").description)
        assertEquals("A 3600 rövid szolgáltatói szám.", NumberDescriber.describe("3600").description)
        assertEquals(NumberDescriber.Kind.SHORT, NumberDescriber.describe("112").kind)
    }

    @Test
    fun articles() {
        assertEquals("az", NumberDescriber.article("A1 TA"))
        assertEquals("a", NumberDescriber.article("One"))
        assertEquals("a", NumberDescriber.article("Telekom"))
        assertEquals("az", NumberDescriber.article("Orange"))
        assertEquals("a", NumberDescriber.numberArticle("112"))
        assertEquals("az", NumberDescriber.numberArticle("1818"))
        assertEquals("az", NumberDescriber.numberArticle("5500"))
        assertTrue(NumberDescriber.countryName("AT") == "Ausztria")
    }
}
