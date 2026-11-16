package com.superdl.launcher.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BirthdayStoreTest {
    @Test fun preservesHungarianNamesAndBirthYear() {
        val entry = BirthdayStore.Entry(123, "Ágnes, Éva", BirthdayHelper.BirthDate(1985, 3, 2))
        assertEquals(entry, BirthdayStore.decode(BirthdayStore.encode(entry)))
    }

    @Test fun ignoresMalformedRecords() {
        assertNull(BirthdayStore.decode("broken"))
        assertNull(BirthdayStore.decode("-1,1985,3,2,QQ"))
        assertNull(BirthdayStore.decode("1,1985,13,2,QQ"))
        assertNull(BirthdayStore.decode("1,1985,2,31,QQ"))
        assertNull(BirthdayStore.decode("1,1985,3,2,%%%"))
    }
}
