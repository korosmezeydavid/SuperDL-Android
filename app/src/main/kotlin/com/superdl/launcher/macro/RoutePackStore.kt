package com.superdl.launcher.macro

import android.content.Context
import android.util.Log
import com.superdl.launcher.catalog.CatalogStore
import com.superdl.launcher.catalog.ModuleType
import org.json.JSONArray
import org.json.JSONObject

/**
 * MEGOSZTOTT MŰVELETSOROK — egy ember egyszer végigszenvedi, utána mindenkinek megy.
 *
 * Ugyanazon a sínen fut, mint a címkecsomagok: a katalógusból jön, adatként.
 *
 * ── AMIBEN VISZONT NEM UGYANAZ, MINT A CÍMKE ───────────────────────────────
 *
 * A címkéknél három egybehangzó beküldés élesíthet egy nevet. ITT EZ NEM
 * ÉRVÉNYES, és ez tudatos döntés:
 *
 *   - Egy címke EGY SZÓ. Hárman is beküldhetik ugyanazt, és az egyezés
 *     bizonyíték. Egy műveletsor viszont LÉPÉSSOROZAT — hárman soha nem
 *     küldenék be pontosan ugyanazt, tehát a küszöb elérhetetlen lenne.
 *   - És a tévedés ára is más. Egy rossz címke félrevezet. Egy rossz
 *     műveletsor MEGNYOM valamit a te nevedben.
 *
 * Ezért a megosztott műveletsor MINDIG szerkesztett marad: emberi kéz nézi át,
 * mielőtt bárki megkapja.
 *
 * ── ÉS AMI SOHA ────────────────────────────────────────────────────────────
 *
 * Megosztott műveletsor SOHA nem indul el magától. Nincs olyan út a
 * programban, amin egy letöltött lépéssorozat a felhasználó kimondott
 * szándéka nélkül elindulhatna.
 */
object RoutePackStore {

    private const val TAG = "SDL_ROUTEPACK"
    private const val PREFS_DISCARDED = "superdl_route_pack_discards"
    private const val SUPPORTED_FORMAT = 1

    /** A megosztás- és mentésformátum verziója. Egy fájl, két célra. */
    const val FORMAT_VERSION = 1

    @Volatile
    private var cache: List<TaskRoute>? = null

    private fun discards(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_DISCARDED, Context.MODE_PRIVATE)

    fun invalidate() {
        cache = null
    }

    fun isDiscarded(context: Context, id: String): Boolean =
        discards(context).getBoolean(id, false)

    fun discard(context: Context, id: String) {
        discards(context).edit().putBoolean(id, true).apply()
    }

    fun undiscard(context: Context, id: String) {
        discards(context).edit().remove(id).apply()
    }

    /** A letöltött csomagokból származó műveletsorok. */
    fun all(context: Context): List<TaskRoute> {
        cache?.let { return it }
        val out = mutableListOf<TaskRoute>()
        try {
            for (moduleId in CatalogStore.installedIds(context, ModuleType.ROUTE_PACK)) {
                val text = CatalogStore.readModule(context, moduleId) ?: continue
                out.addAll(parse(text) ?: continue)
            }
        } catch (e: Exception) {
            Log.w(TAG, "muveletsor csomag betoltes hiba: ${e.message}")
        }
        cache = out
        return out
    }

    /**
     * EGY CSOMAG (vagy egy megosztott fájl) értelmezése.
     *
     * Ugyanaz a formátum, amit az export ír — így amit elküldesz valakinek,
     * azt a katalógusba is fel lehet tenni, átírás nélkül.
     */
    fun parse(text: String): List<TaskRoute>? = try {
        val root = JSONObject(text)
        val format = root.optInt("formatum", 1)
        if (format > SUPPORTED_FORMAT) {
            Log.w(TAG, "ujabb formatum ($format), a csomag kihagyva")
            null
        } else {
            val arr = root.optJSONArray("muveletsorok") ?: JSONArray()
            val out = mutableListOf<TaskRoute>()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { o -> TaskRoute.fromJson(o)?.let { out.add(it) } }
            }
            out
        }
    } catch (e: Exception) {
        Log.w(TAG, "muveletsor csomag ertelmezes hiba: ${e.message}")
        null
    }

    // ── EXPORT ─────────────────────────────────────────────────────────────

    /** Egy műveletsor megosztható alakja. */
    fun export(route: TaskRoute): String = JSONObject().apply {
        put("formatum", FORMAT_VERSION)
        put("muveletsorok", JSONArray().put(route.toJson()))
    }.toString(2)

    /**
     * MI MEGY EL — a jóváhagyás előtti felolvasáshoz.
     *
     * A LEGFONTOSABB MONDAT ITT A LÉPÉSNEVEKRŐL SZÓL. A műveletsor lépései
     * annak a NEVÉT viszik magukkal, amit megnyomtál — és az a név a képernyőn
     * álló szöveg volt. Egy banki alkalmazásban ez lehet egy összeg; egy
     * üzenetküldőben egy ismerős neve.
     *
     * Ezt nem lehet automatikusan kiszűrni: a program nem tudja megmondani,
     * hogy a "Kovács Béla" egy gomb felirata vagy egy ismerősöd. Amit tehetünk:
     * KIMONDJUK, mielőtt elküldenéd, hogy te dönthess.
     */
    fun exportSummary(route: TaskRoute): String = buildString {
        append("${route.name}, ${route.steps.size} lépés. ")
        append("A lépések nevei is elmennek vele: ")
        append(route.steps.joinToString(", ") { it.label.ifBlank { "névtelen lépés" } })
        append(". FIGYELEM: ezek a nevek a képernyőről származnak, ")
        append("és lehet köztük olyan, amit nem akarsz megosztani — egy összeg, ")
        append("egy ismerős neve. Hallgasd meg, mielőtt elküldöd.")
    }
}
