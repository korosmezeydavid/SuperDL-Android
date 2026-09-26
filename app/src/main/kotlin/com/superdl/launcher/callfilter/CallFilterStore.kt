package com.superdl.launcher.callfilter

import android.content.Context
import android.telephony.PhoneNumberUtils
import org.json.JSONArray

object CallFilterStore {

    private const val PREFS = "superdl"
    private const val KEY_BLACKLIST = "call_filter_blacklist"
    private const val KEY_WHITELIST = "call_filter_whitelist"
    private const val KEY_BLOCK_PRIVATE = "call_filter_block_private"
    private const val KEY_MODE = "call_filter_mode"

    /**
     * REJTETT SZÁMOK — KÜLÖN KAPCSOLÓ, A MÓDTÓL FÜGGETLENÜL.
     *
     * A HIBA, AMIT EZ JAVÍT (Alph, 2026-09-17): a rejtett számok tiltása
     * hozzá volt forrasztva a módhoz. „Mindent Fogad" módban is tiltva
     * voltak — a mód saját felolvasott szövege így hangzott: „Mindent
     * fogad. Rejtett és ismeretlen számok tiltva." Ez önmagával
     * vitatkozott, és NEM VOLT olyan állapot, amiben egy rejtett hívás
     * átjött volna. Akit nem zavar, annak sem volt választása.
     *
     * A kettő két külön kérdés: a MÓD arról szól, kit engedünk át; a
     * rejtett szám arról, hogy elfogadjuk-e azt, aki nem mutatja magát.
     *
     * ALAPBÓL TILTVA marad, mert eddig is így működött — egy frissítés ne
     * változtassa meg csendben, hogy kit enged be a telefon.
     */
    private const val KEY_HIDDEN_BLOCKED = "call_filter_hidden_blocked"

    /** Szóljon-e a program, ha kiszűrt egy hívást. */
    private const val KEY_ANNOUNCE = "call_filter_announce"

    /**
     * MIT CSINÁLJON A PROGRAM, HA KISZŰRT EGY HÍVÁST.
     *
     * A néma mellett szól, hogy éjjel ne ébresszen; ellene, hogy nem
     * tudsz róla, ki keresett. Ezért választható, és az alapértelmezés a
     * középút: néma marad, de a helyzetjelentés megmondja.
     */
    enum class AnnounceMode(val id: String, val label: String, val speakLabel: String) {
        SUMMARY(
            id = "summary",
            label = "Csak a helyzetjelentésben",
            speakLabel = "Szűrt hívásról csak a helyzetjelentés szól. A telefon néma marad."
        ),
        ALWAYS(
            id = "always",
            label = "Mindig szóljon",
            speakLabel = "Szűrt hívásnál a program azonnal szól, hogy ki keresett."
        ),
        NEVER(
            id = "never",
            label = "Soha ne szóljon",
            speakLabel = "Szűrt hívásról nem szólok. A Szűrt hívások listában így is megnézheted."
        );

        companion object {
            fun fromId(id: String?): AnnounceMode =
                entries.firstOrNull { it.id == id } ?: SUMMARY
        }
    }

    fun isHiddenBlocked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDDEN_BLOCKED, true)

    fun setHiddenBlocked(context: Context, blocked: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HIDDEN_BLOCKED, blocked).apply()
    }

    fun announceMode(context: Context): AnnounceMode =
        AnnounceMode.fromId(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ANNOUNCE, null)
        )

    fun setAnnounceMode(context: Context, mode: AnnounceMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ANNOUNCE, mode.id).apply()
    }

    /** A hívásszűrő teljes állapota egyetlen felolvasható mondatban. */
    fun speakStatus(context: Context): String = buildString {
        append(getMode(context).speakLabel)
        append(" Rejtett számok: ")
        append(if (isHiddenBlocked(context)) "tiltva." else "átengedve.")
        val white = getWhitelist(context).size
        val black = getBlacklist(context).size
        if (white > 0) append(" Fehérlista: $white szám.")
        if (black > 0) append(" Feketelista: $black szám.")
    }

    fun getMode(context: Context): CallFilterMode {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_MODE)) {
            val migrated = if (prefs.getBoolean(KEY_BLOCK_PRIVATE, false)) {
                CallFilterMode.ACCEPT_ALL
            } else {
                CallFilterMode.ACCEPT_ALL
            }
            setMode(context, migrated)
            return migrated
        }
        return CallFilterMode.fromId(prefs.getString(KEY_MODE, null))
    }

    fun setMode(context: Context, mode: CallFilterMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.id)
            .putBoolean(KEY_BLOCK_PRIVATE, mode == CallFilterMode.ACCEPT_ALL)
            .apply()
    }

    fun cycleMode(context: Context): CallFilterMode {
        val next = getMode(context).next()
        setMode(context, next)
        return next
    }

    fun speakMode(context: Context): String = getMode(context).speakLabel

    @Deprecated("Use getMode()")
    fun isBlockPrivateEnabled(context: Context): Boolean =
        getMode(context) == CallFilterMode.ACCEPT_ALL

    @Deprecated("Use setMode()")
    fun setBlockPrivateEnabled(context: Context, enabled: Boolean) {
        if (enabled) setMode(context, CallFilterMode.ACCEPT_ALL)
    }

    @Deprecated("Use cycleMode()")
    fun toggleBlockPrivate(context: Context): Boolean {
        val next = if (getMode(context) == CallFilterMode.ACCEPT_ALL) {
            CallFilterMode.CONTACTS_ONLY
        } else {
            CallFilterMode.ACCEPT_ALL
        }
        setMode(context, next)
        return next == CallFilterMode.ACCEPT_ALL
    }

    fun getBlacklist(context: Context): List<String> = readList(context, KEY_BLACKLIST)

    fun getWhitelist(context: Context): List<String> = readList(context, KEY_WHITELIST)

    fun isBlacklisted(context: Context, phone: String): Boolean {
        val normalized = normalizePhone(phone)
        if (normalized.isBlank()) return false
        return getBlacklist(context).any { samePhone(context, it, normalized) }
    }

    fun isWhitelisted(context: Context, phone: String): Boolean {
        val normalized = normalizePhone(phone)
        if (normalized.isBlank()) return false
        return getWhitelist(context).any { samePhone(context, it, normalized) }
    }

    fun addToBlacklist(context: Context, phone: String): Boolean {
        val normalized = normalizePhone(phone)
        if (normalized.isBlank()) return false
        removeFromWhitelist(context, normalized)
        val current = getBlacklist(context).toMutableList()
        if (current.any { samePhone(context, it, normalized) }) return false
        current.add(normalized)
        writeList(context, KEY_BLACKLIST, current)
        return true
    }

    fun removeFromBlacklist(context: Context, phone: String): Boolean {
        val normalized = normalizePhone(phone)
        val current = getBlacklist(context)
        val updated = current.filterNot { samePhone(context, it, normalized) }
        if (updated.size == current.size) return false
        writeList(context, KEY_BLACKLIST, updated)
        return true
    }

    fun addToWhitelist(context: Context, phone: String): Boolean {
        val normalized = normalizePhone(phone)
        if (normalized.isBlank()) return false
        val current = getWhitelist(context).toMutableList()
        if (current.any { samePhone(context, it, normalized) }) return false
        current.add(normalized)
        writeList(context, KEY_WHITELIST, current)
        return true
    }

    fun removeFromWhitelist(context: Context, phone: String): Boolean {
        val normalized = normalizePhone(phone)
        val current = getWhitelist(context)
        val updated = current.filterNot { samePhone(context, it, normalized) }
        if (updated.size == current.size) return false
        writeList(context, KEY_WHITELIST, updated)
        return true
    }

    // MIÉRT: a pontos szöveg-egyezés miatt a "06 30…" alakban mentett szám nem
    // egyezett a "+36 30…" alakban érkező hívással (és zárójel, pont is
    // elrontotta) — a tiltás/engedélyezés csendben nem hatott. Csak a
    // számjegyeket és a vezető '+' jelet tartjuk meg, az egyezést pedig a
    // rendszer telefonszám-összevetése dönti el.
    fun normalizePhone(phone: String): String {
        val trimmed = phone.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+") && digits.isNotEmpty()) "+$digits" else digits
    }

    private fun samePhone(context: Context, stored: String, phone: String): Boolean {
        val a = normalizePhone(stored)
        val b = normalizePhone(phone)
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        return try {
            PhoneNumberUtils.compare(context, a, b)
        } catch (_: Exception) {
            false
        }
    }

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