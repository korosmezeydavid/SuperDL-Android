package com.superdl.launcher.macro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A műveletsorok tárolása és a FELVÉTEL állapota.
 *
 * A felvétel állapota szándékosan itt van, nem a képernyőolvasóban: a felvételt
 * a képernyőolvasó gyűjti, de a menü is látja, hogy megy-e — és a kettő nem
 * beszélget egymással közvetlenül.
 */
object TaskRouteStore {

    private const val PREFS = "superdl_task_routes"
    private const val KEY_ROUTES = "routes_json"
    private const val KEY_RECORDING = "recording"
    private const val KEY_LAST_STOP = "last_stop"
    private const val KEY_LAST_STOP_AT = "last_stop_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── HOL AKADT EL LEGUTÓBB ──────────────────────────────────────────────
    //
    // MIÉRT KELL (Alph, 2026-09-18): a műveletsor szándékosan megáll, ha nem
    // azt találja a képernyőn, amit vár — és ki is mondja, hol tart. DE ezt
    // csak az hallja, aki épp ott van. Mire hibajelentés készül, a mondat
    // elszállt, és a jelentésben nyoma sincs: se összeomlás, se hiba.
    //
    // Ugyanaz a tanulság, mint az Elena figyelőnél: amit a program magától
    // abbahagy, azt le is kell írnia.

    fun noteStop(context: Context, routeName: String, stepIndex: Int, reason: String) {
        prefs(context).edit()
            .putString(KEY_LAST_STOP, "$routeName, ${stepIndex + 1}. lépés — $reason")
            .putLong(KEY_LAST_STOP_AT, System.currentTimeMillis())
            .apply()
    }

    /** A legutóbbi elakadás leírása, vagy null, ha még nem volt ilyen. */
    fun lastStop(context: Context): String? {
        val p = prefs(context)
        val reason = p.getString(KEY_LAST_STOP, null)?.takeIf { it.isNotBlank() } ?: return null
        val at = p.getLong(KEY_LAST_STOP_AT, 0L)
        if (at <= 0L) return reason
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(at))
        return "$stamp — $reason"
    }

    // ── FELVÉTEL ───────────────────────────────────────────────────────────

    /**
     * A felvétel közben gyűjtött lépések MEMÓRIÁBAN.
     *
     * MIÉRT NEM LEMEZEN: egy félbehagyott felvétel ne maradjon utána szemétként
     * a telefonon. Ha a program közben leáll, a félkész műveletsor egyszerűen
     * nincs — ez jobb, mint egy csonka lépéssor, ami lejátszva félúton hagyna.
     */
    private val recorded = mutableListOf<TaskStep>()

    @Volatile
    private var recordingStartPackage: String = ""

    // MIÉRT: a "felvétel megy" jelző lemezen van, a lépések viszont csak memóriában.
    // Ha a program felvétel közben leállt, az új folyamatban a jelző örökre igaz
    // maradt (lépések nélkül). Az új folyamat első hozzáférésekor ezért töröljük.
    @Volatile
    private var checkedThisProcess = false

    private fun clearStaleRecordingFlag(context: Context) {
        if (checkedThisProcess) return
        checkedThisProcess = true
        val p = prefs(context)
        if (p.getBoolean(KEY_RECORDING, false)) {
            p.edit().putBoolean(KEY_RECORDING, false).apply()
        }
    }

    fun isRecording(context: Context): Boolean {
        clearStaleRecordingFlag(context)
        return prefs(context).getBoolean(KEY_RECORDING, false)
    }

    fun startRecording(context: Context, startPackage: String) {
        clearStaleRecordingFlag(context)
        recorded.clear()
        recordingStartPackage = startPackage
        prefs(context).edit().putBoolean(KEY_RECORDING, true).apply()
    }

    fun cancelRecording(context: Context) {
        recorded.clear()
        recordingStartPackage = ""
        prefs(context).edit().putBoolean(KEY_RECORDING, false).apply()
    }

    fun addStep(step: TaskStep) {
        // Ötven lépésnél megállunk. Ennél hosszabb műveletsort vakon már nem
        // lehet követni, és ha elromlik, kideríthetetlen, hol.
        if (recorded.size >= 50) return
        recorded.add(step)
    }

    fun recordedCount(): Int = recorded.size

    fun recordedStartPackage(): String = recordingStartPackage

    /**
     * A felvétel lezárása és mentése.
     * @return a mentett műveletsor, vagy null ha nem volt mit menteni
     */
    fun finishRecording(context: Context, name: String): TaskRoute? {
        prefs(context).edit().putBoolean(KEY_RECORDING, false).apply()
        if (recorded.isEmpty()) {
            recordingStartPackage = ""
            return null
        }
        val route = TaskRoute(
            id = System.currentTimeMillis().toString(),
            name = name.trim().ifBlank { "Névtelen műveletsor" },
            startPackage = recordingStartPackage,
            steps = recorded.toList(),
            createdAt = System.currentTimeMillis()
        )
        save(context, all(context) + route)
        recorded.clear()
        recordingStartPackage = ""
        return route
    }

    // ── TÁROLÁS ────────────────────────────────────────────────────────────

    fun all(context: Context): List<TaskRoute> {
        val raw = prefs(context).getString(KEY_ROUTES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<TaskRoute>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { o -> TaskRoute.fromJson(o)?.let { out.add(it) } }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun byId(context: Context, id: String): TaskRoute? = all(context).firstOrNull { it.id == id }

    fun remove(context: Context, id: String) {
        save(context, all(context).filter { it.id != id })
    }

    private fun save(context: Context, routes: List<TaskRoute>) {
        val arr = JSONArray()
        routes.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(KEY_ROUTES, arr.toString()).apply()
    }

    /** A lejátszandó műveletsor azonosítója — a menü teszi le, a szolgáltatás veszi fel. */
    private const val KEY_PENDING = "pending_play"

    fun requestPlay(context: Context, id: String) {
        prefs(context).edit().putString(KEY_PENDING, id).apply()
    }

    fun takePendingPlay(context: Context): String? {
        val id = prefs(context).getString(KEY_PENDING, null)
        if (id != null) prefs(context).edit().remove(KEY_PENDING).apply()
        return id
    }
}
