package com.superdl.launcher.callfilter

import android.content.Context
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject

/**
 * HELY ALAPÚ FÓKUSZ — a telefon abból tudja, mikor kapcsoljon, hogy HOL VAGY.
 *
 * ALPH KÉRÉSE (2026-09-21): „hely alapú fókuszok! munkahelyi gps és minden
 * érzékelésekor például beáll egy a felhasználó által létrehozott
 * munkahelyi fókuszra próbamóddal ahogy az otthon vagy funkció is".
 *
 * MIÉRT NEM ELÉG AZ IDŐZÍTETT FÓKUSZ: az időzített azt feltételezi, hogy a
 * napod óraműre jár. „Hétköznap nyolctól négyig ne zavarjanak" csak addig
 * jó, amíg tényleg ott vagy. Aki szabadnapot vesz ki, azt a saját telefonja
 * némítja le a semmiért; aki bent marad tovább, azt négykor újra hívogatják.
 * A hely nem hazudik.
 *
 * @param radiusMeters ekkora körön belül számít „ott vagy"-nak
 */
data class PlaceFocus(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusMeters: Int,
    val mode: CallFilterMode,
    val enabled: Boolean = true
) {

    /** Ott vagyunk-e. A mérés pontatlanságát is beleszámítjuk. */
    fun contains(location: Location): Boolean {
        val out = FloatArray(1)
        Location.distanceBetween(lat, lon, location.latitude, location.longitude, out)
        // A GPS PONTATLANSÁGÁT HOZZÁADJUK A SUGÁRHOZ. Egy húszméteres
        // pontosságú mérésnél a „pontosan a határon" nem eldönthető, és
        // ilyenkor a nagyvonalúság a helyes: a fókusz bekapcsolása
        // visszavonható, egy elmulasztott fókusz viszont észrevétlen.
        val tolerance = location.accuracy.coerceIn(0f, 100f)
        return out[0] <= radiusMeters + tolerance
    }

    fun speakSummary(): String {
        val state = if (enabled) "" else " (kikapcsolva)"
        return "$name, ${radiusMeters} méteres körben, ${mode.menuLabel}$state"
    }
}

/**
 * A HELY ALAPÚ FÓKUSZOK TÁRA ÉS ÉLŐ ÁLLAPOTA.
 *
 * PRÓBA MÓD, AHOGY AZ OTTHON-FIGYELÉSNÉL: az első helyzet, amikor a program
 * MAGÁTÓL némítja le a telefont, nem indulhat élesben. Próbában mindent
 * végigcsinál és BEMONDJA, mit tenne — de nem szűr semmit. Az élesre váltás
 * külön, tudatos lépés.
 */
object PlaceFocusStore {

    private const val PREFS = "superdl_focus"
    private const val KEY_LIST = "hely_fokuszok"
    private const val KEY_PROBE = "hely_fokusz_proba"
    private const val KEY_ACTIVE = "hely_fokusz_aktiv"
    private const val KEY_LAST = "hely_fokusz_utolso"

    /** Ha a felhasználó nem mond mást, ekkora körrel dolgozunk. */
    const val DEFAULT_RADIUS = 120

    /** Ennél több hely már nem lista, hanem teher. */
    private const val MAX_ITEMS = 10

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── A lista ──────────────────────────────────────────────────────────

    fun all(context: Context): List<PlaceFocus> {
        val raw = prefs(context).getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                PlaceFocus(
                    id = o.optString("id"),
                    name = o.optString("nev", "Hely"),
                    lat = o.optDouble("szelesseg", 0.0),
                    lon = o.optDouble("hosszusag", 0.0),
                    radiusMeters = o.optInt("sugar", DEFAULT_RADIUS),
                    mode = CallFilterMode.fromId(o.optString("mod")),
                    enabled = o.optBoolean("bekapcsolva", true)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, items: List<PlaceFocus>) {
        val array = JSONArray()
        items.take(MAX_ITEMS).forEach { p ->
            array.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("nev", p.name)
                    put("szelesseg", p.lat)
                    put("hosszusag", p.lon)
                    put("sugar", p.radiusMeters)
                    put("mod", p.mode.id)
                    put("bekapcsolva", p.enabled)
                }
            )
        }
        prefs(context).edit().putString(KEY_LIST, array.toString()).apply()
    }

    fun add(context: Context, focus: PlaceFocus): Boolean {
        val current = all(context)
        if (current.size >= MAX_ITEMS) return false
        save(context, current + focus)
        return true
    }

    fun remove(context: Context, id: String) {
        save(context, all(context).filterNot { it.id == id })
        if (activeId(context) == id) clearActive(context)
    }

    fun toggle(context: Context, id: String): Boolean {
        var newState = false
        val updated = all(context).map {
            if (it.id == id) {
                newState = !it.enabled
                it.copy(enabled = newState)
            } else it
        }
        save(context, updated)
        if (!newState && activeId(context) == id) clearActive(context)
        return newState
    }

    // ── Próba mód ────────────────────────────────────────────────────────

    fun isProbe(context: Context): Boolean = prefs(context).getBoolean(KEY_PROBE, true)

    fun setProbe(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_PROBE, value).apply()
    }

    // ── Élő állapot ──────────────────────────────────────────────────────

    fun activeId(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE, null)?.takeIf { it.isNotBlank() }

    fun activeFocus(context: Context): PlaceFocus? {
        val id = activeId(context) ?: return null
        return all(context).firstOrNull { it.id == id && it.enabled }
    }

    fun noteActive(context: Context, id: String?) {
        prefs(context).edit().putString(KEY_ACTIVE, id ?: "").apply()
    }

    private fun clearActive(context: Context) = noteActive(context, null)

    fun noteOutcome(context: Context, text: String) {
        prefs(context).edit().putString(KEY_LAST, text).apply()
    }

    fun lastOutcome(context: Context): String =
        prefs(context).getString(KEY_LAST, null) ?: "Még nem volt hely-ellenőrzés."

    /**
     * A MOST ÉRVÉNYES MÓD, vagy null.
     *
     * PRÓBA MÓDBAN MINDIG NULL: ilyenkor a fókusz csak beszél, nem szűr.
     * Ez az egész próba mód értelme — aki most kapcsolja be először, annak
     * aznap semmiképp ne maradjon le egy hívásról egy félreértés miatt.
     */
    fun activeMode(context: Context): CallFilterMode? {
        if (isProbe(context)) return null
        return activeFocus(context)?.mode
    }

    fun speakStatus(context: Context): String {
        val items = all(context)
        val sb = StringBuilder()
        if (items.isEmpty()) {
            return "Nincs hely alapú fókuszod. A Fókusz felvétele a mostani helyemre " +
                "ponttal tudsz létrehozni egyet, ott, ahol épp vagy."
        }
        sb.append("${items.size} hely alapú fókuszod van. ")
        sb.append(
            if (isProbe(context)) {
                "PRÓBA módban: csak bemondja, mit tenne, de nem szűr. "
            } else {
                "ÉLES módban: tényleg szűr. "
            }
        )
        val active = activeFocus(context)
        sb.append(
            if (active != null) "Most itt vagy: ${active.name}. "
            else "Most egyiknél sem vagy. "
        )
        items.forEach { sb.append(it.speakSummary()).append(". ") }
        sb.append(lastOutcome(context))
        return sb.toString()
    }
}
