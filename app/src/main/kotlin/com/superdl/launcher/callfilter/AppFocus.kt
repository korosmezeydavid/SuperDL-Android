package com.superdl.launcher.callfilter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * ALKALMAZÁS SZERINTI FÓKUSZ — a telefon abból tudja, mikor szűrjön, hogy
 * MIT CSINÁLSZ ÉPP.
 *
 * ALPH KÉRÉSE (2026-09-27): „alkalmazás alapú ne zavarj/fókusz mód — pl.
 * amikor a TikTokot használom, olyankor csak a fehérlistás számok érhetnek el."
 *
 * MIÉRT NEM ELÉG AZ IDŐZÍTETT VAGY A HELY ALAPÚ: egy élő adás nem órára
 * indul, és nem egy helyhez kötött. Amíg az app elöl van, addig kell csend —
 * se előtte, se utána.
 *
 * A FEHÉRLISTA ITT IS MINDENT FELÜLÍR: aki rajta van, az a legszigorúbb
 * alkalmazás szerinti fókusz alatt is átcsörög (a CallFilterEngine a
 * fehérlistát mindennél előbb nézi).
 */
data class AppFocus(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val mode: CallFilterMode,
    val enabled: Boolean = true
) {

    fun speakSummary(): String {
        val state = if (enabled) "bekapcsolva" else "kikapcsolva"
        return "$appLabel, $state: ${modeSentence(mode)}"
    }

    companion object {
        /**
         * A VÁLASZTHATÓ MÓDOK, a legszigorúbbal kezdve.
         *
         * MIÉRT NINCS KÖZTÜK A „MINDENT FOGAD": a fókuszok közül mindig a
         * szigorúbb nyer, és az alkalmazás szerinti fókusz lényege a csend —
         * egy „mindent fogad" szabály itt csak félreértést okozna.
         */
        val PICKABLE_MODES: List<CallFilterMode> = listOf(
            CallFilterMode.TOTAL_DND,
            CallFilterMode.PRIORITY_ONLY,
            CallFilterMode.CONTACTS_ONLY
        )

        /**
         * Rövid név a listához.
         *
         * MIÉRT NEM A MÓD SAJÁT NEVE („Teljes Ne Zavarj"): a fehérlista
         * mindent felülír, tehát itt a teljes Ne Zavarj valójában azt
         * jelenti, hogy CSAK a fehérlista jön át. Így mondjuk ki, ahogy a
         * felhasználó gondolja.
         */
        fun modeLabel(mode: CallFilterMode): String = when (mode) {
            CallFilterMode.TOTAL_DND -> "Csak a fehérlista"
            CallFilterMode.PRIORITY_ONLY -> "Fehérlista és kedvencek"
            CallFilterMode.CONTACTS_ONLY -> "Fehérlista és névjegyek"
            CallFilterMode.ACCEPT_ALL -> "Mindenki"
        }

        /** Egy mondatrész: „csak a fehérlistás számok hívhatnak". */
        fun modeSentence(mode: CallFilterMode): String = when (mode) {
            CallFilterMode.TOTAL_DND ->
                "csak a fehérlistás számok hívhatnak"
            CallFilterMode.PRIORITY_ONLY ->
                "csak a fehérlistás számok, a kedvencek és a csillagozott névjegyek hívhatnak"
            CallFilterMode.CONTACTS_ONLY ->
                "csak a fehérlistás számok és az ismert névjegyek hívhatnak"
            CallFilterMode.ACCEPT_ALL ->
                "mindenki hívhat, a feketelistásokat kivéve"
        }
    }
}

/**
 * AZ ALKALMAZÁS SZERINTI FÓKUSZOK TÁRA.
 *
 * Ugyanabban a tárolóban, mint az időzített és a hely alapú fókusz, hogy a
 * fókuszok egy helyen legyenek.
 */
object AppFocusStore {

    private const val PREFS = "superdl_focus"
    private const val KEY_LIST = "app_fokuszok"

    /** Ennél több app már nem lista, hanem teher. */
    const val MAX_ITEMS = 20

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<AppFocus> {
        val raw = prefs(context).getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val pkg = o.optString("csomag")
                if (pkg.isBlank()) return@mapNotNull null
                AppFocus(
                    id = o.optString("id"),
                    packageName = pkg,
                    appLabel = o.optString("nev", pkg),
                    mode = CallFilterMode.fromId(o.optString("mod")),
                    enabled = o.optBoolean("bekapcsolva", true)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, items: List<AppFocus>) {
        val array = JSONArray()
        items.take(MAX_ITEMS).forEach { f ->
            array.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("csomag", f.packageName)
                    put("nev", f.appLabel)
                    put("mod", f.mode.id)
                    put("bekapcsolva", f.enabled)
                }
            )
        }
        prefs(context).edit().putString(KEY_LIST, array.toString()).apply()
    }

    fun findByPackage(context: Context, packageName: String): AppFocus? =
        all(context).firstOrNull { it.packageName == packageName }

    /**
     * Mentés. EGY APPHOZ EGY SZABÁLY: ha már van, azt írjuk felül (és
     * bekapcsoljuk) — két ellentmondó szabály ugyanarra az appra csak
     * zavart okozna.
     *
     * @return false, ha a lista megtelt
     */
    fun upsert(context: Context, focus: AppFocus): Boolean {
        val current = all(context)
        val existing = current.firstOrNull { it.packageName == focus.packageName }
        if (existing != null) {
            save(
                context,
                current.map {
                    if (it.packageName == focus.packageName) {
                        focus.copy(id = existing.id, enabled = true)
                    } else it
                }
            )
            return true
        }
        if (current.size >= MAX_ITEMS) return false
        save(context, current + focus)
        return true
    }

    fun remove(context: Context, id: String) {
        save(context, all(context).filterNot { it.id == id })
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
        return newState
    }

    /** A most érvényes szabály (az előtérben lévő apphoz), vagy null. */
    fun activeFocus(context: Context): AppFocus? {
        val pkg = ForegroundAppTracker.activePackage(context) ?: return null
        return all(context).firstOrNull { it.enabled && it.packageName == pkg }
    }

    /** A most érvényes mód, vagy null, ha nincs elöl fókuszos app. */
    fun activeMode(context: Context): CallFilterMode? = activeFocus(context)?.mode

    /**
     * Egy rövid mondat a hívásszűrő állapotához, vagy üres szöveg, ha nincs
     * bekapcsolt alkalmazás szerinti fókusz.
     */
    fun speakBrief(context: Context): String {
        val active = activeFocus(context)
        if (active != null) {
            return "Most érvényben: alkalmazás szerinti fókusz, mert ez van előtérben: " +
                "${active.appLabel}. Most ${AppFocus.modeSentence(active.mode)}."
        }
        val enabled = all(context).filter { it.enabled }
        if (enabled.isEmpty()) return ""
        return "Alkalmazás szerinti fókusz: ${enabled.joinToString(", ") { it.appLabel }}. " +
            "Amíg valamelyik előtérben van, a hozzá beállított szűrés érvényes."
    }

    fun speakStatus(context: Context): String {
        val items = all(context)
        if (items.isEmpty()) {
            return "Még nincs alkalmazás szerinti fókusz. Az Új fókusz egy alkalmazáshoz " +
                "ponttal tudsz létrehozni egyet."
        }
        val sb = StringBuilder()
        val on = items.count { it.enabled }
        sb.append("${items.size} alkalmazás szerinti fókuszod van, ebből $on bekapcsolva. ")
        if (!ForegroundAppTracker.isTrackingPossible(context)) {
            // MIÉRT MONDJUK KI: kisegítő szolgáltatás nélkül nem tudjuk, mi
            // van elöl — a szabályok ilyenkor csendben nem hatnának, és a
            // felhasználó nem értené, miért csörög a telefon.
            sb.append(
                "FIGYELEM: most nem működik, mert sem a Super DL képernyőolvasó, sem a " +
                    "Rendszer PIN segéd nincs engedélyezve a kisegítő lehetőségek között. " +
                    "Legalább az egyikre szükség van, hogy tudjam, melyik alkalmazás van elöl. "
            )
        }
        val active = activeFocus(context)
        if (active != null) {
            sb.append("Most érvényben: ${active.appLabel}. ")
        }
        items.forEach { sb.append(it.speakSummary()).append(". ") }
        sb.append("A fehérlistás számok mindig átcsörögnek.")
        return sb.toString()
    }
}
