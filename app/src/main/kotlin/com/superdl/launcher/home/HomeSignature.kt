package com.superdl.launcher.home

import android.content.Context
import com.superdl.launcher.storage.JsonPrefsHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * AZ „OTTHON" UJJLENYOMATA.
 *
 * NEM KOORDINÁTA, HANEM KÖRNYEZET. Egy lakásban a GPS rendszeresen nem ad
 * fixet, vagy ötven-száz métert téved — egy erre épített döntés hamis
 * riasztásokat szórna. A wifi és a mobilcella viszont nem MÉRI a helyet,
 * hanem FELISMERI a környezetet: vagy látja azt, amit otthon szokott, vagy
 * nem. Beltéren ez sokkal megbízhatóbb.
 *
 * MIÉRT HALMAZ MINDEGYIKBŐL: egy lakás több cellát is láthat, a szolgáltató
 * át is rendezheti őket, és a wifi-router is cserélhető. Ezért a betanítás
 * TÖBBSZÖR is elvégezhető, és minden alkalom HOZZÁAD, nem felülír.
 *
 * A GPS csak döntetlennél kerül elő, és csak az ellenőrzés pillanatában.
 */
data class HomeSignature(
    val bssids: Set<String>,
    val cells: Set<String>,
    val lat: Double?,
    val lon: Double?,
    val trainedCount: Int,
    val lastTrainedAt: Long
) {
    val isEmpty: Boolean get() = bssids.isEmpty() && cells.isEmpty() && lat == null

    fun speakSummary(): String {
        if (isEmpty) return "Az otthon még nincs betanítva."
        val parts = mutableListOf<String>()
        if (bssids.isNotEmpty()) {
            parts.add(if (bssids.size == 1) "egy wifi hálózat" else "${bssids.size} wifi hálózat")
        }
        if (cells.isNotEmpty()) {
            parts.add(if (cells.size == 1) "egy mobilcella" else "${cells.size} mobilcella")
        }
        if (lat != null && lon != null) parts.add("egy mentett helyzet")
        return "Az otthon $trainedCount alkalommal lett betanítva. " +
            "Megjegyezve: ${parts.joinToString(", ")}."
    }
}

object HomeSignatureStore {

    private const val PREFS = "superdl"
    private const val KEY = "otthon_ujjlenyomat"
    private const val KEY_SCHEMA = "otthon_ujjlenyomat_schema"
    private const val SCHEMA_VERSION = 1

    /**
     * Ennél több azonosítót nem tartunk meg. Nem a tárhely miatt: ha valaki
     * véletlenül nem otthon tanít be, a régi, biztosan jó bejegyzéseket ne
     * szorítsa ki a tévedés. A legrégebbi esik ki.
     */
    private const val MAX_IDS = 12

    fun get(context: Context): HomeSignature = try {
        val o = JsonPrefsHelper.readJsonObject(context, PREFS, KEY, KEY_SCHEMA, SCHEMA_VERSION)
        HomeSignature(
            bssids = readSet(o, "bssids"),
            cells = readSet(o, "cells"),
            lat = if (o.has("lat") && !o.isNull("lat")) o.optDouble("lat") else null,
            lon = if (o.has("lon") && !o.isNull("lon")) o.optDouble("lon") else null,
            trainedCount = o.optInt("trainedCount", 0),
            lastTrainedAt = o.optLong("lastTrainedAt", 0L)
        )
    } catch (_: Exception) {
        HomeSignature(emptySet(), emptySet(), null, null, 0, 0L)
    }

    fun isTrained(context: Context): Boolean = !get(context).isEmpty

    /**
     * Egy betanítási minta HOZZÁADÁSA. Sosem felülírás — lásd a fejlécet.
     * A mentett helyzet akkor cserélődik, ha az új mérés pontosabb.
     */
    fun merge(context: Context, sample: HomeSample): HomeSignature {
        val old = get(context)
        var lat = old.lat
        var lon = old.lon
        val loc = sample.location
        if (loc != null && (lat == null || lon == null || sample.accurateEnough)) {
            lat = loc.latitude
            lon = loc.longitude
        }
        val next = HomeSignature(
            bssids = trim(old.bssids + listOfNotNull(sample.bssid)),
            cells = trim(old.cells + sample.cells),
            lat = lat,
            lon = lon,
            trainedCount = old.trainedCount + 1,
            lastTrainedAt = System.currentTimeMillis()
        )
        save(context, next)
        return next
    }

    fun clear(context: Context) {
        save(context, HomeSignature(emptySet(), emptySet(), null, null, 0, 0L))
    }

    private fun trim(values: Set<String>): Set<String> =
        if (values.size <= MAX_IDS) values else values.toList().takeLast(MAX_IDS).toSet()

    private fun readSet(o: JSONObject, key: String): Set<String> {
        val array = o.optJSONArray(key) ?: return emptySet()
        val out = linkedSetOf<String>()
        for (i in 0 until array.length()) {
            val v = array.optString(i)
            if (!v.isNullOrBlank()) out.add(v)
        }
        return out
    }

    private fun save(context: Context, value: HomeSignature) {
        val o = JSONObject().apply {
            put("bssids", JSONArray(value.bssids.toList()))
            put("cells", JSONArray(value.cells.toList()))
            put("lat", value.lat ?: JSONObject.NULL)
            put("lon", value.lon ?: JSONObject.NULL)
            put("trainedCount", value.trainedCount)
            put("lastTrainedAt", value.lastTrainedAt)
        }
        JsonPrefsHelper.saveJsonObject(context, PREFS, KEY, KEY_SCHEMA, SCHEMA_VERSION, o)
    }
}
