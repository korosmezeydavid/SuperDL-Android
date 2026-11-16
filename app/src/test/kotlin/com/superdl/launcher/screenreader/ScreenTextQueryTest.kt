package com.superdl.launcher.screenreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTextQueryTest {
    @Test
    fun matchesHungarianTextWithoutAccents() {
        val query = checkNotNull(ScreenTextQuery.parse("ARVIZTURO tukorfurogep"))
        assertTrue(query.matches("Árvíztűrő tükörfúrógép"))
        assertFalse(query.matches("Árvíztűrő szivattyú"))
    }

    @Test
    fun acceptsTermsInAnyOrderAndNormalizesWhitespace() {
        val query = checkNotNull(ScreenTextQuery.parse("  beallitasok\u00a0\thang "))
        assertTrue(query.matches("Hang és rezgés beállítások"))
        assertFalse(query.matches("A beállítások elérhetők"))
    }

    @Test
    fun rejectsEmptyQueries() {
        assertNull(ScreenTextQuery.parse("  \t\u00a0"))
    }
}