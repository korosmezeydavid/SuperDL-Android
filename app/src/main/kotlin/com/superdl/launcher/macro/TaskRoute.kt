package com.superdl.launcher.macro

import org.json.JSONArray
import org.json.JSONObject

/**
 * MŰVELETSOR — "ezt csináld meg helyettem".
 *
 * MIÉRT NEM "ÚTVONAL" A NEVE: a programban az útvonal már foglalt, a GPS-es
 * útvonal-rögzítőé. Két különböző dolog egy néven vakon használhatatlan: a
 * menüben hallgatva nem derülne ki, melyikbe léptél be. A tervben ez a
 * funkció "útvonal-rögzítő" — a gondolat ugyanaz, csak a neve más, hogy
 * hallás után is megkülönböztethető legyen.
 *
 * MI EZ: a havonta megismételt negyven mozdulat egy név és egy indítás.
 *
 * ── A LÉPÉS NEM KOORDINÁTA ─────────────────────────────────────────────────
 *
 * Ez a legfontosabb döntés az egészben. Egy koordinátákra felvett műveletsor
 * az első app-frissítésnél elkezd rossz gombokat nyomkodni, és a felhasználó
 * nem is tudja, mikor tért le. Ezért a lépés így hangzik: "nyomd meg azt, amin
 * az áll, hogy Kosár" — elem-ujjlenyomat és címke, nem képpont.
 *
 * Így a műveletsor túléli, ha az alkalmazás átrendeződik. És ami még
 * fontosabb: ha MÉGSEM találja, azt TUDJA, és megáll.
 */
data class TaskStep(
    val action: Action,
    /** Az elem ujjlenyomata — ez keresi meg holnap is. Üres a rendszergomboknál. */
    val fingerprint: String,
    /** Amit a felvételkor mondott az elem — ezt mondja ki lejátszáskor is. */
    val label: String,
    /** Melyik alkalmazásban történt. Az ellenőrzés ezt is nézi. */
    val packageName: String,
    /**
     * KAPCSOLÓNÁL: milyen ÁLLAPOTOT akartál elérni. Máshol null.
     *
     * MIÉRT KELL: enélkül a műveletsor mozdulatot ismétel, nem szándékot.
     * "Hotspot bekapcsolás" néven felveszel valamit, aztán legközelebb
     * elindítod — és mivel a hotspot már be van kapcsolva, a megnyomás KI
     * fogja kapcsolni. Pont a fordítottja történik annak, amit a neve ígér.
     *
     * A megnyomás azt jelenti, hogy "változtasd meg". Amit valójában akartál,
     * az az, hogy "legyen bekapcsolva". Ezt jegyezzük meg.
     */
    val desiredChecked: Boolean? = null
) {
    enum class Action {
        CLICK, LONG_CLICK, BACK, HOME, SCROLL_FORWARD, SCROLL_BACK
    }

    /** Ez hangzik el a lépés előtt. */
    fun speak(): String = when (action) {
        Action.CLICK -> when (desiredChecked) {
            true -> "bekapcsolom: $label"
            false -> "kikapcsolom: $label"
            null -> "megnyomom: $label"
        }
        Action.LONG_CLICK -> "hosszan megnyomom: $label"
        Action.BACK -> "vissza"
        Action.HOME -> "kezdőképernyő"
        Action.SCROLL_FORWARD -> "görgetés lefelé"
        Action.SCROLL_BACK -> "görgetés felfelé"
    }

    /** Kell-e ehhez a lépéshez elemet keresni a képernyőn? */
    val needsTarget: Boolean
        get() = action == Action.CLICK || action == Action.LONG_CLICK

    fun toJson(): JSONObject = JSONObject().apply {
        put("muvelet", action.name)
        put("ujjlenyomat", fingerprint)
        put("cimke", label)
        put("csomag", packageName)
        desiredChecked?.let { put("kivant_allapot", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): TaskStep? = try {
            TaskStep(
                action = Action.valueOf(o.optString("muvelet", "CLICK")),
                fingerprint = o.optString("ujjlenyomat", ""),
                label = o.optString("cimke", ""),
                packageName = o.optString("csomag", ""),
                desiredChecked = if (o.has("kivant_allapot")) {
                    o.optBoolean("kivant_allapot")
                } else {
                    null
                }
            )
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Egy elnevezett műveletsor.
 *
 * @param startPackage az az alkalmazás, ahol a felvétel indult. Lejátszáskor
 *        ezt nyitjuk meg először — enélkül a műveletsor csak akkor menne, ha
 *        a felhasználó magától pont ott áll, ahol a felvételkor állt.
 */
data class TaskRoute(
    val id: String,
    val name: String,
    val startPackage: String,
    val steps: List<TaskStep>,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("nev", name)
        put("indito_csomag", startPackage)
        put("keszult", createdAt)
        put("lepesek", JSONArray().also { arr -> steps.forEach { arr.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): TaskRoute? = try {
            val steps = mutableListOf<TaskStep>()
            val arr = o.optJSONArray("lepesek")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { s -> TaskStep.fromJson(s)?.let { steps.add(it) } }
                }
            }
            TaskRoute(
                id = o.optString("id"),
                name = o.optString("nev", "Névtelen"),
                startPackage = o.optString("indito_csomag", ""),
                steps = steps,
                createdAt = o.optLong("keszult", 0L)
            ).takeIf { it.id.isNotBlank() && it.steps.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
