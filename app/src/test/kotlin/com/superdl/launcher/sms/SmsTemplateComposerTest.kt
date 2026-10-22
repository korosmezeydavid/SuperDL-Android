package com.superdl.launcher.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsTemplateComposerTest {
    @Test fun addsTemplateAfterDictatedPrefix() {
        assertEquals("Küldöm a számlaszámomat 12345678-12345678",
            SmsTemplateComposer.append(" Küldöm a számlaszámomat ", " 12345678-12345678 "))
    }

    @Test fun templateWorksWithoutDictatedPrefix() {
        assertEquals("Késni fogok.", SmsTemplateComposer.append("", "Késni fogok."))
    }
}
