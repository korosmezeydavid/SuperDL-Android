package com.superdl.launcher.email

data class ImapMail(
    val uid: Long,
    val from: String,
    val subject: String,
    val date: String,
    val body: String
) {
    fun speakHeader(index: Int, total: Int): String =
        "Levél $index a $total közül. Feladó: ${EmailHelper.speakAddress(from)}. " +
            "Tárgy: $subject. Dátum: $date."

    fun speakBodyPreview(maxChars: Int = 1200): String {
        val text = body.take(maxChars).trim()
        return if (text.isBlank()) speakHeader(1, 1) + " Üres levél."
        else speakHeader(1, 1) + " " + text
    }
}