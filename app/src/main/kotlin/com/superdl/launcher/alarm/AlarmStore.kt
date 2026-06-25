package com.superdl.launcher.alarm

import android.content.Context
import com.superdl.launcher.storage.JsonPrefsHelper
import org.json.JSONArray
import org.json.JSONObject

object AlarmStore {

    private const val PREFS = "superdl"
    private const val KEY_ALARMS = "alarms"
    private const val KEY_ALARMS_SCHEMA = "alarms_schema"
    private const val SCHEMA_VERSION = 1
    private const val MAX_ALARMS = 12

    fun getAll(context: Context): List<AlarmEntry> {
        val list = mutableListOf<AlarmEntry>()
        val array = JsonPrefsHelper.readJsonArray(
            context = context,
            prefsName = PREFS,
            dataKey = KEY_ALARMS,
            schemaVersionKey = KEY_ALARMS_SCHEMA,
            currentSchemaVersion = SCHEMA_VERSION
        )
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                AlarmEntry(
                    id = obj.getInt("id"),
                    hour = obj.getInt("hour"),
                    minute = obj.getInt("minute"),
                    label = obj.optString("label", ""),
                    enabled = obj.optBoolean("enabled", true)
                )
            )
        }
        return list.sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    fun getEnabled(context: Context): List<AlarmEntry> =
        getAll(context).filter { it.enabled }

    fun add(context: Context, hour: Int, minute: Int, label: String): AlarmEntry? {
        val alarms = getAll(context).toMutableList()
        if (alarms.size >= MAX_ALARMS) return null
        val nextId = (alarms.maxOfOrNull { it.id } ?: 0) + 1
        val entry = AlarmEntry(nextId, hour, minute, label, enabled = true)
        alarms.add(entry)
        save(context, alarms)
        return entry
    }

    fun delete(context: Context, id: Int): AlarmEntry? {
        val alarms = getAll(context).toMutableList()
        val removed = alarms.firstOrNull { it.id == id } ?: return null
        alarms.removeAll { it.id == id }
        save(context, alarms)
        return removed
    }

    fun getNextAlarm(context: Context): AlarmEntry? {
        val nowMinutes = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
        val enabled = getEnabled(context)
        val todayUpcoming = enabled.filter { it.hour * 60 + it.minute > nowMinutes }
        if (todayUpcoming.isNotEmpty()) return todayUpcoming.minBy { it.hour * 60 + it.minute }
        return enabled.minByOrNull { it.hour * 60 + it.minute }
    }

    private fun save(context: Context, alarms: List<AlarmEntry>) {
        val array = JSONArray()
        alarms.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("hour", entry.hour)
                put("minute", entry.minute)
                put("label", entry.label)
                put("enabled", entry.enabled)
            })
        }
        JsonPrefsHelper.saveJsonArray(
            context,
            PREFS,
            KEY_ALARMS,
            KEY_ALARMS_SCHEMA,
            SCHEMA_VERSION,
            array
        )
    }
}