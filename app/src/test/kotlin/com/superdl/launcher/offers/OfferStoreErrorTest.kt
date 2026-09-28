package com.superdl.launcher.offers

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 2026-09-28, Mezei Géza: „a Tesco-t nem hozza be". A tesco.hu 403-mal
 * (Access Denied) utasítja el a kérést. A program ne azt mondja, hogy a bolt
 * oldala „nem válaszol", hanem hogy nem engedi a letöltést.
 */
class OfferStoreErrorTest {

    @Test
    fun rendbenLevoValaszNemHiba() {
        assertNull(OfferStore.errorFor(200))
        assertNull(OfferStore.errorFor(204))
    }

    @Test
    fun elutasitasKulonHiba() {
        for (code in listOf(401, 403, 429)) {
            val e = OfferStore.errorFor(code)
            assertTrue("$code", e is OfferStore.Refused)
            assertTrue("$code", (e as OfferStore.Refused).code == code)
        }
    }

    @Test
    fun egyebHibaNemElutasitas() {
        for (code in listOf(404, 500, 502, 503)) {
            val e = OfferStore.errorFor(code)
            assertTrue("$code", e != null)
            assertFalse("$code", e is OfferStore.Refused)
        }
    }
}
