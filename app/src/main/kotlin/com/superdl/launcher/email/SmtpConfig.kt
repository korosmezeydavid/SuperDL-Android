package com.superdl.launcher.email

data class SmtpConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val fromEmail: String,
    val fromName: String,
    val useTls: Boolean = true
) {
    fun isValid(): Boolean =
        host.isNotBlank() && port in 1..65535 &&
            username.isNotBlank() && password.isNotBlank() &&
            EmailHelper.isValidEmail(fromEmail)

    fun speakSummary(): String {
        val namePart = if (fromName.isNotBlank()) "$fromName, " else ""
        return "Küldő: $namePart${EmailHelper.speakAddress(fromEmail)}. " +
            "Szerver: $host, port $port. Felhasználó: ${EmailHelper.speakAddress(username)}."
    }
}