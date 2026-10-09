package com.superdl.launcher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperSurfHelperTest {
    @Test fun dictatedAmountAndCurrencies() {
        assertEquals(SuperSurfHelper.Conversion(100.0, "EUR", "HUF"),
            SuperSurfHelper.conversionFromSpeech("100 euró forintba"))
        assertEquals(SuperSurfHelper.Conversion(1.0, "HUF", "EUR"),
            SuperSurfHelper.conversionFromSpeech("forint euró"))
        assertEquals(SuperSurfHelper.Conversion(12.5, "USD", "HUF"),
            SuperSurfHelper.conversionFromSpeech("12,5 dollár forint"))
        assertEquals(SuperSurfHelper.Conversion(100.0, "HUF", "USD"),
            SuperSurfHelper.conversionFromSpeech("100 forint dollárba"))
    }

    @Test fun siteResultsNeverEscapeChosenDomain() {
        assertTrue(SuperSurfHelper.belongsToSite("https://www.mvgyosz.hu/hirek/pelda", "mvgyosz.hu"))
        assertFalse(SuperSurfHelper.belongsToSite("https://mvgyosz.hu.evil.example/hirek", "mvgyosz.hu"))
        assertFalse(SuperSurfHelper.belongsToSite("javascript:alert(1)", "mvgyosz.hu"))
    }
}
