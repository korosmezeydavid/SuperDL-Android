package com.superdl.launcher.sms

import android.content.Context
import com.superdl.launcher.storage.JsonPrefsHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EGY IDŐZÍTETT ÜZENET.
 *
 * @param id          egyedi azonosító (ez az ébresztő kódja is)
 * @param phones      a címzettek száma
 * @param labels      a címzettek neve — a felolvasáshoz
 * @param message     a szöveg
 * @param triggerAt   mikor menjen
 * @param outcome     mi lett belőle, ha már lejárt
 */
data class ScheduledSms(
    val id: Int,
    val phones: List<String>,
    val labels: List<String>,
    val message: String,
    val triggerAt: Long,
    val outcome: String? = null
) {
    val isPending: Boolean get() = outcome == null

    fun recipients(): List<Recipient> =
        phones.mapIndexed { i, p -> Recipient(p, labels.getOrElse(i) { p }) }

    fun whoText(): String = when {
        labels.isEmpty() -> "ismeretlen címzett"
        labels.size == 1 -> labels.first()
        else -> "${labels.size} címzett"
    }

    /**
     * Felolvasható összefoglaló.
     *
     * A HÁTRALÉVŐ IDŐ IS ELHANGZIK, nem csak az időpont. Ha valaki este
     * beállít egy hajnali üzenetet, a „négy óra harminc" önmagában nem mondja
     * meg, hogy az hat óra múlva lesz-e vagy harminc perc múlva. Egy
     * védőhálónál ez a különbség számít.
     */
    fun speakSummary(): String {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(triggerAt))
        if (!isPending) {
            return "$time, ${whoText()}. $outcome"
        }
        val left = triggerAt - System.currentTimeMillis()
        val whenText = when {
            left <= 0 -> "bármelyik pillanatban"
            left < 60_000 -> "kevesebb mint egy perc múlva"
            left < 60 * 60_000 -> "${left / 60_000} perc múlva"
            left < 24 * 60 * 60_000L -> {
                val h = left / (60 * 60_000)
                val m = (left % (60 * 60_000)) / 60_000
                if (m == 0L) "$h óra múlva" else "$h óra $m perc múlva"
            }
            else -> "${left / (24 * 60 * 60_000L)} nap múlva"
        }
        return "${whoText()}, $time, $whenText. ${message.take(60)}"
    }
}

/**
 * AZ IDŐZÍTETT ÜZENETEK TÁRA.
 *
 * MIÉRT ÖNÁLLÓ TÁR, ÉS NEM NAPTÁRI ESEMÉNY: a naptár egy naptár — ott egy
 * esemény van, aminek mellékhatása lehet egy üzenet. Az időzített SMS
 * viszont ÖNMAGÁBAN a tétel; a „feleség felébresztése" nem esemény. Külön
 * tárolva a lista és a törlés is egyszerűbb.
 *
 * A küldő viszont KÖZÖS (`SmsHelper`) — két küldő két igazságot jelentene.
 */
object ScheduledSmsStore {

    private const val PREFS = "superdl"
    private const val KEY = "idozitett_sms"
    private const val KEY_SCHEMA = "idozitett_sms_schema"
    private const val SCHEMA_VERSION = 1

    /**
     * Ennyi tétel fér el. Nem a tárhely miatt: egy vakon kezelhető listát
     * valahol le kell zárni, különben átláthatatlanná válik.
     */
    private const val MAX_ITEMS = 20

    /** A lejárt tételeket ennyi ideig őrizzük, hogy a sorsuk megkérdezhető legyen. */
    private const val KEEP_DONE_MS = 7L * 24 * 60 * 60 * 1000

    fun getAll(context: Context): List<ScheduledSms> = try {
        val text = JsonPrefsHelper.readJsonArray(context, PREFS, KEY, KEY_SCHEMA, SCHEMA_VERSION)
        val out = mutableListOf<ScheduledSms>()
        for (i in 0 until text.length()) {
            val o = text.getJSONObject(i)
            val phones = mutableListOf<String>()
            o.optJSONArray("phones")?.let { for (j in 0 until it.length()) phones.add(it.getString(j)) }
            val labels = mutableListOf<String>()
            o.optJSONArray("labels")?.let { for (j in 0 until it.length()) labels.add(it.getString(j)) }
            if (phones.isEmpty()) continue
            out.add(
                ScheduledSms(
                    id = o.optInt("id"),
                    phones = phones,
                    labels = labels,
                    message = o.optString("message"),
                    triggerAt = o.optLong("triggerAt"),
                    outcome = if (o.isNull("outcome")) null else o.optString("outcome").ifBlank { null }
                )
            )
        }
        out.sortedBy { it.triggerAt }
    } catch (_: Exception) {
        emptyList()
    }

    fun pending(context: Context): List<ScheduledSms> = getAll(context).filter { it.isPending }

    fun add(
        context: Context,
        recipients: List<Recipient>,
        message: String,
        triggerAt: Long
    ): ScheduledSms? {
        val all = getAll(context).toMutableList()
        if (all.count { it.isPending } >= MAX_ITEMS) return null
        val id = (all.maxOfOrNull { it.id } ?: 0) + 1
        val entry = ScheduledSms(
            id = id,
            phones = recipients.map { it.phone },
            labels = recipients.map { it.label },
            message = message,
            triggerAt = triggerAt
        )
        all.add(entry)
        save(context, all)
        return entry
    }

    fun get(context: Context, id: Int): ScheduledSms? = getAll(context).firstOrNull { it.id == id }

    fun remove(context: Context, id: Int): ScheduledSms? {
        val all = getAll(context).toMutableList()
        val removed = all.firstOrNull { it.id == id } ?: return null
        all.removeAll { it.id == id }
        save(context, all)
        return removed
    }

    /** Egy lejárt tétel sorsának felírása, és a régiek takarítása. */
    fun markDone(context: Context, id: Int, outcome: String) {
        val now = System.currentTimeMillis()
        val all = getAll(context)
            .map { if (it.id == id) it.copy(outcome = outcome) else it }
            .filter { it.isPending || now - it.triggerAt < KEEP_DONE_MS }
        save(context, all)
    }

    private fun save(context: Context, items: List<ScheduledSms>) {
        val array = JSONArray()
        items.forEach { e ->
            array.put(JSONObject().apply {
                put("id", e.id)
                put("phones", JSONArray(e.phones))
                put("labels", JSONArray(e.labels))
                put("message", e.message)
                put("triggerAt", e.triggerAt)
                put("outcome", e.outcome)
            })
        }
        JsonPrefsHelper.saveJsonArray(context, PREFS, KEY, KEY_SCHEMA, SCHEMA_VERSION, array)
    }
}
