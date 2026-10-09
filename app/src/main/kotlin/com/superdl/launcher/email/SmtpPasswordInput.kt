package com.superdl.launcher.email

import java.io.InputStream

/** A telefonról másolt vagy fájlba mentett Gmail alkalmazásjelszó ellenőrzése. */
object SmtpPasswordInput {
    fun normalize(raw: String?): String? {
        val firstLine = raw?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: return null
        val password = firstLine.filterNot(Char::isWhitespace)
        return password.takeIf { it.length == 16 && it.all(Char::isLetterOrDigit) }
    }

    /** Ne olvassunk korlátlan méretű vagy nem szöveges állományt jelszóként. */
    fun readText(stream: InputStream): String? {
        val buffer = ByteArray(1025)
        var count = 0
        while (count < buffer.size) {
            val n = stream.read(buffer, count, buffer.size - count)
            if (n < 0) break
            if (n == 0) continue
            count += n
        }
        return if (count in 1..1024) String(buffer, 0, count, Charsets.UTF_8) else null
    }
}
