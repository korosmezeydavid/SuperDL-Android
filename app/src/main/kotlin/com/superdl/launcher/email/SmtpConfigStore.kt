package com.superdl.launcher.email

import android.content.Context

object SmtpConfigStore {

    private const val PREFS = "smtp_prefs"

    fun isConfigured(context: Context): Boolean = get(context)?.isValid() == true

    fun get(context: Context): SmtpConfig? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val host = prefs.getString("host", null)?.trim().orEmpty()
        val port = prefs.getInt("port", 587)
        val username = prefs.getString("username", null)?.trim().orEmpty()
        val password = prefs.getString("password", null).orEmpty()
        val fromEmail = prefs.getString("from_email", null)?.trim().orEmpty()
        val fromName = prefs.getString("from_name", null)?.trim().orEmpty()
        val useTls = prefs.getBoolean("use_tls", true)
        if (host.isBlank()) return null
        return SmtpConfig(host, port, username, password, fromEmail, fromName, useTls)
    }

    fun save(context: Context, config: SmtpConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("host", config.host)
            .putInt("port", config.port)
            .putString("username", config.username)
            .putString("password", config.password)
            .putString("from_email", config.fromEmail)
            .putString("from_name", config.fromName)
            .putBoolean("use_tls", config.useTls)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun gmailPreset(username: String, password: String, fromName: String = ""): SmtpConfig =
        SmtpConfig(
            host = "smtp.gmail.com",
            port = 587,
            username = username.trim(),
            password = password,
            fromEmail = username.trim(),
            fromName = fromName.trim(),
            useTls = true
        )
}