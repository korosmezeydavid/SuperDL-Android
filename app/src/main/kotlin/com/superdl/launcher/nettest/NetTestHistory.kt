package com.superdl.launcher.nettest

import org.json.JSONArray
import org.json.JSONObject

/**
 * A KORÁBBI MÉRÉSEK — a windowsos napló (nettest_naplo.json) párja.
 *
 * Miért kell? Két hét múlva ebből derül ki, hogy „minden este nyolckor
 * beesik a sebesség" — ezzel már érdemben lehet reklamálni a szolgáltatónál.
 *
 * Itt csak a JSON-forma és a felolvasható sorok vannak (Android nélkül,
 * tesztelhetően); a tárolás a `NetTestStore`-ban. A publikus IP-t
 * SZÁNDÉKOSAN maszkolva mentjük, ahogy Windowson is.
 */
object NetTestHistory {

    /** Ennyi korábbi mérést őrzünk meg (mint Windowson). */
    const val MAX = 200

    data class Entry(
        val time: String,
        val mode: String,
        val downMbps: Double,
        val upMbps: Double,
        val latencyAvgMs: Double,
        /** KIND_WIFI / KIND_MOBILE … — telefonon ez sokat számít: wifin vagy mobilon mértük? */
        val kind: String = "",
        val connection: String = "",
        val wifiDbm: Int = 0,
        val maskedIp: String = "",
        val provider: String = ""
    )

    fun entryOf(e: NetTestResult): Entry = Entry(
        time = e.time,
        mode = e.mode,
        downMbps = e.speed.downMbps,
        upMbps = e.speed.upMbps,
        latencyAvgMs = e.speed.latencyAvgMs,
        kind = e.local.kind,
        connection = e.local.connection,
        wifiDbm = e.local.wifi?.rssi ?: 0,
        maskedIp = NetTestText.maskIp(e.pub.ip),
        provider = e.pub.provider
    )

    fun toJson(list: List<Entry>): String {
        val arr = JSONArray()
        list.takeLast(MAX).forEach { x ->
            arr.put(
                JSONObject()
                    .put("ido", x.time)
                    .put("mod", x.mode)
                    .put("le_mbps", x.downMbps)
                    .put("fel_mbps", x.upMbps)
                    .put("keses_atlag_ms", x.latencyAvgMs)
                    .put("tipus", x.kind)
                    .put("kapcsolat", x.connection)
                    .put("wifi_dbm", x.wifiDbm)
                    .put("ip", x.maskedIp)
                    .put("szolgaltato", x.provider)
            )
        }
        return arr.toString()
    }

    /** Hibatűrő beolvasás: sérült adatnál üres lista, sosem kivétel. */
    fun fromJson(text: String?): List<Entry> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Entry(
                    time = o.optString("ido", ""),
                    mode = o.optString("mod", MODE_FULL),
                    downMbps = o.optDouble("le_mbps", 0.0).let { if (it.isNaN()) 0.0 else it },
                    upMbps = o.optDouble("fel_mbps", 0.0).let { if (it.isNaN()) 0.0 else it },
                    latencyAvgMs = o.optDouble("keses_atlag_ms", 0.0).let { if (it.isNaN()) 0.0 else it },
                    kind = o.optString("tipus", ""),
                    connection = o.optString("kapcsolat", ""),
                    wifiDbm = o.optInt("wifi_dbm", 0),
                    maskedIp = o.optString("ip", ""),
                    provider = o.optString("szolgaltato", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Új mérés a napló végére; a legrégebbiek kiesnek a MAX felett. */
    fun append(list: List<Entry>, entry: Entry): List<Entry> = (list + entry).takeLast(MAX)

    /**
     * = nettest.naplo_sorok — a korábbi mérések egysoros összefoglalói,
     * LEGÚJABB ELÖL. TELEFONON PLUSZ: a sor végén a kapcsolat (wifi vagy
     * mobilnet) — a telefon a kettő közt váltogat, és enélkül két mérés nem
     * lenne összevethető. A gyors ellenőrzésnél nem mondunk „le 0"-t: ott nem
     * mértünk sebességet.
     */
    fun lines(list: List<Entry>, count: Int = 30): List<String> {
        val out = list.takeLast(count).reversed().map { x ->
            val base = if (x.mode == MODE_QUICK) {
                "${x.time} – gyors ellenőrzés, késleltetés ${NetTestText.num(x.latencyAvgMs)} ezredmásodperc"
            } else {
                NetTestText.historyLine(x.time, x.downMbps, x.upMbps, x.latencyAvgMs)
            }
            if (x.connection.isNotBlank()) "$base – ${x.connection}" else base
        }
        return out.ifEmpty { listOf("Még nincs korábbi mérés.") }
    }

    /** = nettest.naplo_atlag */
    fun average(list: List<Entry>, count: Int = 10): String =
        NetTestText.historyAverage(list.takeLast(count).map { it.downMbps })
}
