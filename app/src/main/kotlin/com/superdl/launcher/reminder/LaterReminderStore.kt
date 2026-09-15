package com.superdl.launcher.reminder

import android.content.Context
import android.provider.CallLog
import com.superdl.launcher.storage.JsonPrefsHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * A FÜGGŐ EMLÉKEZTETŐK TÁRA.
 *
 * Külön lista a visszahívandóknak és külön az üzeneteknek — de ugyanabban a
 * tárban, mert a gépezetük azonos, és két külön tárnál a biztonsági mentésbe
 * az egyiket előbb-utóbb elfelejtenénk beletenni. (Pont ez történt egyszer a
 * szövegtárral: telefoncserénél elveszett.)
 */
object LaterReminderStore {

    private const val PREFS = "superdl"
    private const val KEY = "kesobbi_emlekeztetok"
    private const val KEY_SCHEMA = "kesobbi_emlekeztetok_schema"
    private const val SCHEMA_VERSION = 1

    /** Ennél többet nem tartunk. Ami ennél több, azt már nem lista, hanem teher. */
    private const val MAX_ITEMS = 50

    // ── Olvasás ──────────────────────────────────────────────────────────

    fun all(context: Context): List<LaterReminder> {
        val out = mutableListOf<LaterReminder>()
        val array = JsonPrefsHelper.readJsonArray(
            context, PREFS, KEY, KEY_SCHEMA, SCHEMA_VERSION
        )
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            out.add(
                LaterReminder(
                    id = obj.optInt("id", 0),
                    kind = obj.optString("kind", LaterReminder.KIND_CALL),
                    number = obj.optString("number", ""),
                    name = obj.optString("name", ""),
                    dueAt = obj.optLong("dueAt", 0L),
                    note = obj.optString("note", ""),
                    createdAt = obj.optLong("createdAt", 0L)
                )
            )
        }
        return out.sortedBy { it.dueAt }
    }

    /** A visszahívandók, időrendben. A lejártak elöl — azok a sürgősek. */
    fun calls(context: Context): List<LaterReminder> = ofKind(context, LaterReminder.KIND_CALL)

    /** A függő üzenetek, időrendben. */
    fun messages(context: Context): List<LaterReminder> = ofKind(context, LaterReminder.KIND_SMS)

    private fun ofKind(context: Context, kind: String): List<LaterReminder> =
        all(context).filter { it.kind == kind }

    fun get(context: Context, id: Int): LaterReminder? = all(context).firstOrNull { it.id == id }

    fun count(context: Context, kind: String): Int = ofKind(context, kind).size

    // ── Írás ─────────────────────────────────────────────────────────────

    /**
     * Felvesz egy emlékeztetőt. UGYANARRA A SZÁMRA CSAK EGY LEHET: ha kétszer
     * kéred ugyanazt, az időpont íródik felül, nem keletkezik két bemondás
     * ugyanarról az emberről.
     */
    fun add(
        context: Context,
        kind: String,
        number: String,
        name: String,
        dueAt: Long,
        note: String = ""
    ): LaterReminder? {
        val current = all(context).toMutableList()
        current.removeAll { it.kind == kind && sameNumber(it.number, number) }
        if (current.size >= MAX_ITEMS) return null
        val nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
        val entry = LaterReminder(
            id = nextId,
            kind = kind,
            number = number.trim(),
            name = name.trim(),
            dueAt = dueAt,
            note = note.trim(),
            createdAt = System.currentTimeMillis()
        )
        current.add(entry)
        save(context, current)
        return entry
    }

    fun remove(context: Context, id: Int) {
        val current = all(context).toMutableList()
        current.removeAll { it.id == id }
        save(context, current)
    }

    /** Új időpont ugyanannak a tételnek — ez a halasztás. */
    fun reschedule(context: Context, id: Int, dueAt: Long): LaterReminder? {
        val current = all(context).toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return null
        val updated = current[index].copy(dueAt = dueAt)
        current[index] = updated
        save(context, current)
        return updated
    }

    private fun save(context: Context, items: List<LaterReminder>) {
        val array = JSONArray()
        items.sortedBy { it.dueAt }.take(MAX_ITEMS).forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("kind", entry.kind)
                    .put("number", entry.number)
                    .put("name", entry.name)
                    .put("dueAt", entry.dueAt)
                    .put("note", entry.note)
                    .put("createdAt", entry.createdAt)
            )
        }
        JsonPrefsHelper.saveJsonArray(
            context, PREFS, KEY, KEY_SCHEMA, SCHEMA_VERSION, array
        )
    }

    // ── Magától takarítás ────────────────────────────────────────────────

    /**
     * AKIT KÖZBEN VISSZAHÍVTÁL, LEKERÜL A LISTÁRÓL.
     *
     * Egy lista, amit kézzel kell takarítani, két hét alatt használhatatlan
     * lesz: húsz tétel áll rajta, tizennyolcat elintéztél, és a kettő
     * fontosat nem találod köztük. A hívásnaplóból látszik, ha az emlékeztető
     * felvétele óta ment hívás arra a számra — az elintézettnek számít.
     *
     * @return hány tétel került le
     */
    fun pruneCalledBack(context: Context): Int {
        val pending = calls(context)
        if (pending.isEmpty()) return 0
        val removable = pending.filter { entry ->
            entry.number.isNotBlank() && calledSince(context, entry.number, entry.createdAt)
        }
        if (removable.isEmpty()) return 0
        val current = all(context).toMutableList()
        current.removeAll { item -> removable.any { it.id == item.id } }
        save(context, current)
        return removable.size
    }

    /** Ment-e KIMENŐ hívás erre a számra a megadott idő óta. */
    private fun calledSince(context: Context, number: String, since: Long): Boolean = try {
        var found = false
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
            "${CallLog.Calls.TYPE} = ${CallLog.Calls.OUTGOING_TYPE} AND ${CallLog.Calls.DATE} > ?",
            arrayOf(since.toString()),
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            while (cursor.moveToNext()) {
                val called = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty() else ""
                if (sameNumber(called, number)) {
                    found = true
                    break
                }
            }
        }
        found
    } catch (_: Throwable) {
        // A hívásnapló olvasása engedélyhez kötött, és nem is kötelező.
        // Ha nincs meg, a lista marad — kézzel is törölhető.
        false
    }

    /**
     * Két telefonszám akkor azonos, ha az utolsó nyolc számjegyük egyezik.
     * A körzetszám és a nemzetközi előhívó hol ott van, hol nincs; a
     * hívásnaplóban gyakran más alakban szerepel, mint ahogy beírtad.
     */
    private fun sameNumber(a: String, b: String): Boolean {
        val x = a.filter { it.isDigit() }
        val y = b.filter { it.isDigit() }
        if (x.isBlank() || y.isBlank()) return false
        val n = minOf(8, x.length, y.length)
        return x.takeLast(n) == y.takeLast(n)
    }
}
