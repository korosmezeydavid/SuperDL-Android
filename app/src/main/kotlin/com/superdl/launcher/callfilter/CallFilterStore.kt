package com.superdl.launcher.callfilter

import android.content.Context
import org.json.JSONArray

object CallFilterStore {

    private const val PREFS = "superdl"
    private const val KEY_BLACKLIST = "call_filter_blacklist"
    private const val KEY_WHITELIST = "call_filter_whitelist"
    private const val KEY_BLOCK_PRIVATE = "call_filter_block_private"

    fun isBlockPrivateEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BLOCK_PRIVATE, false)

    fun setBlockPrivateEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BLOCK_PRIVATE, enabled)
            .apply()
    }

    fun toggleBlockPrivate(context: Context): Boolean {
        val next = !isBlockPrivateEnabled(context)
        setBlockPrivateEnabled(context, next)
        return next
    }

    fun getBlacklist(context: Context): List<String> = readList(context, KEY_BLACKLIST)

    fun getWhitelist(context: Context): List<String> = readList(context, KEY_WHITELIST)

    fun isBlacklisted(context: Context, phone: String): Boolean {
        val normalized = normalizePhone(phone)
        if (normalized.isBlank()) return false
        return getBlacklist(context).any { normalizePhone(it) == normalized }
    }

    fun isWhitelisted(context: Context, phone: String): Boolean {
        val normalized = normalizePhone(phone)
        if (normalized.isBlank()) return false
        return getWhitelist(context).any { normalizePhone(it) == normalized }
    }

    fun addToBlacklist(context: Context, phone: String): Boolean {
        val normalized = normalizePhone(phone)
        if (normalized.isBlank()) return false
        removeFromWhitelist(context, normalized)
        val current = getBlacklist(context).toMutableList()
        if (current.any { normalizePhone(it) == normalized }) return false
        current.add(normalized)
        writeList(context, KEY_BLACKLIST, current)
        return true
    }

    fun removeFromBlacklist(context: Context, phone: String): Boolean {
        val normalized = normalizePhone(phone)
        val current = getBlacklist(context)
        val updated = current.filterNot { normalizePhone(it) == normalized }
        if (updated.size == current.size) return false
        writeList(context, KEY_BLACKLIST, updated)
        return true
    }

    fun addToWhitelist(context: Context, phone: String): Boolean {
        val normalized = normalizePhone(phone)
        if (normalized.isBlank()) return false
        val current = getWhitelist(context).toMutableList()
        if (current.any { normalizePhone(it) == normalized }) return false
        current.add(normalized)
        writeList(context, KEY_WHITELIST, current)
        return true
    }

    fun removeFromWhitelist(context: Context, phone: String): Boolean {
        val normalized = normalizePhone(phone)
        val current = getWhitelist(context)
        val updated = current.filterNot { normalizePhone(it) == normalized }
        if (updated.size == current.size) return false
        writeList(context, KEY_WHITELIST, updated)
        return true
    }

    fun normalizePhone(phone: String): String =
        phone.replace(" ", "").replace("-", "").trim()

    private fun readList(context: Context, key: String): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
            ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeList(context: Context, key: String, values: List<String>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, array.toString())
            .apply()
    }
}