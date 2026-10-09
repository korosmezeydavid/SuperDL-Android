package com.superdl.launcher.email

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class SmtpPasswordInputTest {
    @Test fun groupedPasswordFromClipboard() {
        assertEquals("abcdefghijklmnop", SmtpPasswordInput.normalize("abcd efgh ijkl mnop"))
    }

    @Test fun firstNonEmptyLineFromFile() {
        assertEquals("abcdefghijklmnop", SmtpPasswordInput.normalize("\n abcd efgh ijkl mnop\n"))
    }

    @Test fun rejectsWrongLengthAndSentence() {
        assertNull(SmtpPasswordInput.normalize("rövid"))
        assertNull(SmtpPasswordInput.normalize("Jelszó: abcd efgh ijkl mnop"))
    }

    @Test fun rejectsOversizedFile() {
        assertNull(SmtpPasswordInput.readText(ByteArrayInputStream(ByteArray(1025) { 'a'.code.toByte() })))
    }
}
