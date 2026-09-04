package com.superdl.launcher.share

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Egy feltöltés nyoma.
 *
 * MIÉRT KELL EGYÁLTALÁN ELŐZMÉNY: aki feltölt egy fájlt és elküldi a linket,
 * annak három nap múlva fogalma sincs, mit küldött, hova, és él-e még.
 * Látva ezt a böngésző előzményéből össze lehet kaparni. Vakon nem.
 */
data class ShareEntry(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val providerId: String,
    val providerName: String,
    val spokenProvider: String,
    val url: String,
    val deleteToken: String,
    val uploadedAt: Long,
    val expiresAt: Long,
    val oneTime: Boolean
) {
    val expired: Boolean get() = System.currentTimeMillis() >= expiresAt

    /**
     * „még két nap és négy óra" — EMBERI IDŐ, nem időbélyeg.
     * A windowsos `emberi_ido` szemlélete: a felhasználó nem dátumot akar
     * hallani, hanem azt, hogy sürgős-e.
     */
    fun remainingText(): String {
        val left = expiresAt - System.currentTimeMillis()
        if (left <= 0) return "lejárt"
        val orak = left / 3600_000L
        val napok = orak / 24
        val maradekOra = orak % 24
        return when {
            napok >= 1 && maradekOra >= 1 -> "még $napok nap és $maradekOra óra"
            napok >= 1 -> "még $napok nap"
            orak >= 1 -> "még $orak óra"
            else -> "kevesebb mint egy óra"
        }
    }

    /** Ez hangzik el a listában lépkedve. */
    fun speakLine(): String {
        val figyelem = if (oneTime) " Csak egyszer tölthető le." else ""
        return "$fileName, ${CloudTargets.sizeText(sizeBytes)}, $spokenProvider. " +
            "${remainingText()}.$figyelem"
    }
}

/**
 * A MEGOSZTÁSI ELŐZMÉNYEK TÁRA.
 *
 * A program SAJÁT tárhelyén él, nem a megosztott tárolón: ez magánadat —
 * mit kinek küldtél —, ennek semmi keresnivalója egy olyan mappában, amit
 * más alkalmazások is olvasnak.
 */
object ShareHistoryStore {

    private const val FILE_NAME = "megosztas_elozmenyek.json"

    /** Ennél régebbi LEJÁRT sor kiesik: egy nappal a lejárat után. */
    private const val GRACE_MS = 24 * 3600_000L

    /** Ennyi sornál többet nem tartunk. */
    private const val MAX_ENTRIES = 200

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /**
     * Minden sor, lejárat szerint: elöl, ami hamarabb tűnik el.
     *
     * A LEJÁRT SOROK EGY NAPIG MARADNAK. Aki tegnap küldött egy linket, és ma
     * nem érti, miért nem működik, annak EZ a válasz — ha némán eltüntetnénk,
     * a kérdésére nem lenne felelet.
     */
    fun all(context: Context): List<ShareEntry> {
        val now = System.currentTimeMillis()
        return read(context)
            .filter { now < it.expiresAt + GRACE_MS }
            .sortedBy { it.expiresAt }
    }

    fun add(context: Context, entry: ShareEntry) {
        val list = read(context).toMutableList()
        list.add(0, entry)
        write(context, list.take(MAX_ENTRIES))
    }

    fun remove(context: Context, id: String) {
        write(context, read(context).filter { it.id != id })
    }

    /** A lejártak eltakarítása — a felhasználó kérésére, egyben. */
    fun purgeExpired(context: Context): Int {
        val all = read(context)
        val maradok = all.filter { !it.expired }
        write(context, maradok)
        return all.size - maradok.size
    }

    private fun read(context: Context): List<ShareEntry> {
        val f = file(context)
        if (!f.isFile) return emptyList()
        return try {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url")
                if (url.isBlank()) return@mapNotNull null
                ShareEntry(
                    id = o.optString("id", url.hashCode().toString()),
                    fileName = o.optString("fileName", "ismeretlen fájl"),
                    sizeBytes = o.optLong("sizeBytes", 0L),
                    providerId = o.optString("providerId"),
                    providerName = o.optString("providerName"),
                    spokenProvider = o.optString("spokenProvider", o.optString("providerName")),
                    url = url,
                    deleteToken = o.optString("deleteToken"),
                    uploadedAt = o.optLong("uploadedAt", 0L),
                    expiresAt = o.optLong("expiresAt", 0L),
                    oneTime = o.optBoolean("oneTime", false)
                )
            }
        } catch (_: Exception) {
            // Sérült fájl: inkább üres lista, mint összeomlás. A következő
            // feltöltés úgyis felülírja.
            emptyList()
        }
    }

    private fun write(context: Context, list: List<ShareEntry>) {
        try {
            val arr = JSONArray()
            list.forEach { e ->
                arr.put(
                    JSONObject().apply {
                        put("id", e.id)
                        put("fileName", e.fileName)
                        put("sizeBytes", e.sizeBytes)
                        put("providerId", e.providerId)
                        put("providerName", e.providerName)
                        put("spokenProvider", e.spokenProvider)
                        put("url", e.url)
                        put("deleteToken", e.deleteToken)
                        put("uploadedAt", e.uploadedAt)
                        put("expiresAt", e.expiresAt)
                        put("oneTime", e.oneTime)
                    }
                )
            }
            file(context).writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
            // A tár elvesztése kellemetlen, de nem indok arra, hogy a
            // feltöltés maga meghiúsuljon.
        }
    }
}
