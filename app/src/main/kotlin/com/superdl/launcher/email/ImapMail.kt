package com.superdl.launcher.email

data class ImapMail(
    val uid: Long,
    val from: String,
    val subject: String,
    val date: String,
    val body: String
) {
    /**
     * A feladó EMBERI neve, ha van. A fejlécben általában
     * `Géza <mezeig79@gmail.com>` alakban érkezik — a nevet felolvasni sokkal
     * barátságosabb, mint a "kukac ... pont ..." formában betűzött címet.
     * Ha csak cím van, azt mondjuk ki.
     */
    fun speakFrom(): String {
        val raw = from.trim()
        if (raw.isBlank()) return "Ismeretlen feladó"
        val name = raw.substringBefore("<").trim().trim('"').trim()
        return if (name.isNotBlank()) name
        else EmailHelper.speakAddress(raw.substringAfter("<").substringBefore(">").trim())
    }

    fun speakHeader(index: Int, total: Int): String =
        "Levél $index ${hungarianArticle(total)} $total közül. Feladó: ${speakFrom()}. " +
            "Tárgy: $subject. Dátum: $date."

    fun speakBodyPreview(maxChars: Int = 1200): String {
        // A levél megnyitásakor a FELADÓ és a TÁRGY is hangozzon el, utána a
        // tartalom — enélkül a felolvasás csonka volt.
        val head = "Feladó: ${speakFrom()}. Tárgy: $subject."
        val text = body.take(maxChars).trim()
        return if (text.isBlank()) "$head A levél tartalma üres."
        else "$head $text"
    }
}

/**
 * MIÉRT: „a 5 közül" helyett „az 5 közül" — a névelő a KIMONDOTT számtól függ
 * (egy, öt, ötven, ötszáz, ezer, egymillió… magánhangzóval kezdődik).
 */
private fun hungarianArticle(n: Int): String {
    if (n < 0) return "a"
    if (n in 1000..1999) return "az"
    var x = n
    while (x >= 1000) x /= 1000
    val lead = when {
        x >= 100 -> if (x / 100 == 5) 5 else -1
        x >= 10 -> if (x / 10 == 5) 5 else -1
        else -> x
    }
    return if (lead == 1 || lead == 5) "az" else "a"
}