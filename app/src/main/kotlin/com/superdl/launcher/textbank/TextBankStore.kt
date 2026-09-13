package com.superdl.launcher.textbank

import android.content.Context
import android.util.Log
import com.superdl.launcher.storage.JsonPrefsHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * A SZÖVEGTÁR — EGY TÁR, TÖBB AJTÓ.
 *
 * MIÉRT NEM ÉPÍTETTÜNK ÚJAT: a mátrix billentyűzetnek már volt szövegtára
 * (`keyboard/MatrixTextBank`), pontosan erre a célra. Csak épp BE VOLT ZÁRVA a
 * billentyűzetbe: a tételeknek nem volt nevük, nem lehetett belőlük küldeni, és
 * a WiFi portálon sem látszottak.
 *
 * Két tár ugyanarra a célra a legrosszabb, amit tehetünk: a felhasználó soha
 * nem tudná, melyikben van a számlaszám. Ezért a régit KINYITJUK, és mindenhol
 * ugyanaz a tartalom látszik — billentyűzet, SMS-küldés, diktálás, portál.
 *
 * A SORREND A LÉTREHOZÁS SORRENDJE, és ez szándékos. Nem „leggyakoribb elöl":
 * vakon az sokkal rosszabb, mert ami tegnap a harmadik volt, az ma legyen ma is
 * a harmadik. Átrendezni a portálon lehet.
 */
object TextBankStore {

    private const val TAG = "SDL_SZOVEGTAR"

    private const val PREFS = "superdl"
    private const val KEY = "szovegtar"
    private const val KEY_SCHEMA = "szovegtar_schema"
    private const val SCHEMA_VERSION = 1

    /** A régi, csak gombhoz kötött tár — innen migrálunk. */
    private const val OLD_PREFS = "superdl_text_bank"
    private const val KEY_MIGRATED = "szovegtar_migralva"

    /**
     * Ennél több tételt vakon végighallgatni nem lehet. Nem a tárhely korlátoz,
     * hanem a fül.
     */
    const val MAX_ITEMS = 30

    // ── Olvasás ──────────────────────────────────────────────────────────

    fun getAll(context: Context): List<TextBankEntry> {
        migrateIfNeeded(context)
        return read(context)
    }

    fun get(context: Context, id: Int): TextBankEntry? =
        getAll(context).firstOrNull { it.id == id }

    /** A mátrix billentyűzet gombjához kötött szöveg, vagy null. */
    fun forSlot(context: Context, slot: String): TextBankEntry? =
        getAll(context).firstOrNull { it.matrixSlot == slot }

    fun count(context: Context): Int = getAll(context).size

    // ── Írás ─────────────────────────────────────────────────────────────

    /** @return az új tétel, vagy null ha megtelt a tár. */
    fun add(
        context: Context,
        name: String,
        text: String,
        matrixSlot: String? = null
    ): TextBankEntry? {
        val all = getAll(context).toMutableList()
        if (all.size >= MAX_ITEMS) return null
        // EGY GOMBON EGY SZÖVEG. Ha ez a gomb foglalt, a régi kötés megszűnik —
        // különben két tétel ülne ugyanazon a gombon, és találgatni kellene.
        if (matrixSlot != null) {
            for (i in all.indices) {
                if (all[i].matrixSlot == matrixSlot) all[i] = all[i].copy(matrixSlot = null)
            }
        }
        val entry = TextBankEntry(
            id = (all.maxOfOrNull { it.id } ?: 0) + 1,
            name = name.trim().ifBlank { text.trim().take(20) },
            text = text.trim(),
            matrixSlot = matrixSlot
        )
        all.add(entry)
        save(context, all)
        return entry
    }

    fun update(
        context: Context,
        id: Int,
        name: String,
        text: String,
        matrixSlot: String?
    ): Boolean {
        val all = getAll(context).toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return false
        if (matrixSlot != null) {
            for (i in all.indices) {
                if (i != index && all[i].matrixSlot == matrixSlot) {
                    all[i] = all[i].copy(matrixSlot = null)
                }
            }
        }
        all[index] = all[index].copy(
            name = name.trim().ifBlank { all[index].name },
            text = text.trim(),
            matrixSlot = matrixSlot
        )
        save(context, all)
        return true
    }

    fun remove(context: Context, id: Int): TextBankEntry? {
        val all = getAll(context).toMutableList()
        val removed = all.firstOrNull { it.id == id } ?: return null
        all.removeAll { it.id == id }
        save(context, all)
        return removed
    }

    /** Egy tétel mozgatása a listában (a portál fel/le gombjaihoz). */
    fun move(context: Context, id: Int, delta: Int): Boolean {
        val all = getAll(context).toMutableList()
        val from = all.indexOfFirst { it.id == id }
        if (from < 0) return false
        val to = (from + delta).coerceIn(0, all.lastIndex)
        if (to == from) return false
        val item = all.removeAt(from)
        all.add(to, item)
        save(context, all)
        return true
    }

    /** A billentyűzetről jövő mentés: gombhoz kötött, név nélküli felvétel. */
    fun setForSlot(context: Context, slot: String, text: String) {
        val existing = forSlot(context, slot)
        if (existing != null) {
            update(context, existing.id, existing.name, text, slot)
        } else {
            add(context, text.trim().take(20), text, slot)
        }
    }

    fun clearSlot(context: Context, slot: String) {
        val existing = forSlot(context, slot) ?: return
        remove(context, existing.id)
    }

    // ── Felolvasás ───────────────────────────────────────────────────────

    fun speakAll(context: Context): String {
        val all = getAll(context)
        if (all.isEmpty()) {
            return "A szövegtár üres. A WiFi portál Szövegtár lapján a " +
                "leggyorsabb feltölteni, mert ott begépelheted."
        }
        return "${all.size} sablon. " + all.joinToString(". ") { it.speakShort() }
    }

    // ── Migráció a régi, gombhoz kötött tárból ───────────────────────────

    /**
     * A RÉGI TÁRAT NEM TÖRÖLJÜK.
     *
     * Ha a migráció bármiért elhasal — sérült érték, váratlan kivétel —, a
     * tartalom ott marad a régi helyén, és kézzel visszaszedhető. Egy elveszett
     * számlaszám pont az, amit a legnehezebb újra bevinni.
     */
    private fun migrateIfNeeded(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        try {
            val old = context.applicationContext
                .getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE)
            val current = read(context).toMutableList()
            var nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
            // A régi kulcsok alakja: slot_KEY_7
            for ((key, value) in old.all) {
                if (!key.startsWith("slot_")) continue
                val text = (value as? String)?.trim().orEmpty()
                if (text.isBlank()) continue
                val slot = key.removePrefix("slot_")
                if (current.any { it.matrixSlot == slot }) continue
                current.add(
                    TextBankEntry(
                        id = nextId++,
                        name = text.take(20),
                        text = text,
                        matrixSlot = slot
                    )
                )
            }
            if (current.isNotEmpty()) save(context, current)
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            Log.i(TAG, "szovegtar migralva: ${current.size} tetel")
        } catch (t: Throwable) {
            // A migráció kudarca nem viheti magával a szövegtárat. Jelöletlenül
            // hagyjuk, hogy legközelebb újra megpróbálja.
            Log.w(TAG, "migracio hiba: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    // ── Lemez ────────────────────────────────────────────────────────────

    private fun read(context: Context): List<TextBankEntry> = try {
        val array = JsonPrefsHelper.readJsonArray(
            context, PREFS, KEY, KEY_SCHEMA, SCHEMA_VERSION
        )
        val out = mutableListOf<TextBankEntry>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val text = o.optString("text")
            if (text.isBlank()) continue
            out.add(
                TextBankEntry(
                    id = o.optInt("id"),
                    name = o.optString("name").ifBlank { text.take(20) },
                    text = text,
                    matrixSlot = o.optString("slot").ifBlank { null }
                )
            )
        }
        out
    } catch (t: Throwable) {
        Log.w(TAG, "olvasas hiba: ${t.message}")
        emptyList()
    }

    private fun save(context: Context, items: List<TextBankEntry>) {
        val array = JSONArray()
        items.forEach { e ->
            array.put(JSONObject().apply {
                put("id", e.id)
                put("name", e.name)
                put("text", e.text)
                put("slot", e.matrixSlot ?: "")
            })
        }
        JsonPrefsHelper.saveJsonArray(context, PREFS, KEY, KEY_SCHEMA, SCHEMA_VERSION, array)
    }
}
