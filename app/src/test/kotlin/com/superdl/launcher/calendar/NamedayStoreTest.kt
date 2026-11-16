package com.superdl.launcher.calendar

import org.junit.Assert.*
import org.junit.Test

class NamedayStoreTest {
    @Test fun roundTripAndRejectMalformedDates() {
        val item = NamedayStore.Entry(42, "Ágnes, Éva", 2, 29)
        assertEquals(item, NamedayStore.decode(NamedayStore.encode(item)))
        assertNull(NamedayStore.decode("42,2,30,QWduZXM"))
        assertNull(NamedayStore.decode("0,2,29,QWduZXM"))
        assertNull(NamedayStore.decode("42,2,29,***"))
    }
}
