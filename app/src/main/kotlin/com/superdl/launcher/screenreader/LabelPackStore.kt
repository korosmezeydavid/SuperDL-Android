package com.superdl.launcher.screenreader

import android.content.Context
import android.util.Log
import com.superdl.launcher.catalog.CatalogStore
import com.superdl.launcher.catalog.ModuleType
import org.json.JSONObject

/**
 * CÍMKECSOMAGOK — a közösség elnevezései.
 *
 * MIÉRT: egy friss telepítés ma ÜRESEN indul. Minden vak felhasználó
 * egyenként, magának fedezi fel ugyanannak a banki alkalmazásnak ugyanazt a
 * névtelen gombját. Ez az a munka, amit elég egyszer elvégezni.
 *
 * A csomag ADAT, nem kód: a katalógusból jön, ugyanazon a sínen, mint a kvíz
 * vagy a rádiócsomag.
 *
 * ── HÁROM VASSZABÁLY ───────────────────────────────────────────────────────
 *
 * 1. A SAJÁT CÍMKÉD MINDIG VERI A KÖZÖSÉT. A közösségi név soha nem írja felül
 *    azt, amit te adtál. Ezért van külön tárban: nem keveredik, nem tud
 *    véletlenül fölé kerülni.
 * 2. ELVETHETŐ. Ha valaki hülyeséget küldött be, a címkekezelőben elveted, és
 *    nálad eltűnik. Az elvetés a te döntésed, és megmarad a csomag frissítése
 *    után is.
 * 3. VISSZAVONHATÓ KÖZPONTILAG. A csomag `visszavont` listája kulcsokat sorol
 *    fel: ami ott van, az nálad is eltűnik a következő frissítéssel. Egy rossz
 *    címkét nem csak beadni kell tudni, hanem visszaszedni is.
 *
 * ── A FORMÁTUM, ÉS MIÉRT ILYEN ─────────────────────────────────────────────
 *
 * A formátumot ELSŐRE kell eltalálni. A címkecsomag olvasásához új alkalmazás-
 * verzió kell (a modul-típus a programban van felsorolva), tehát egy későbbi
 * formátum-változás mindenkinek frissítést jelentene. Ezért van benne néhány
 * mező, ami MA MÉG ÜRES — olcsóbb most beletenni, mint fél év múlva kiadni:
 *
 *   megjegyzes  — "ez az, ami tényleg elküldi" (a félelem nem az, hogy nem
 *                 tudod, mi a gomb, hanem hogy nem tudod, mi történik)
 *   eredet      — "szerkeszto" vagy "harman": hogyan élesedett a címke.
 *                 Az MK4 hármas küszöbének átláthatósága ezen fog állni.
 *   verziotol / verzioig — melyik alkalmazás-verziókra érvényes
 *   ujjlenyomat — az elem-ujjlenyomat, hogy a címke akkor is találjon, ha
 *                 az elem elmozdult
 *
 * ```json
 * {
 *   "formatum": 1,
 *   "id": "kozlekedes",
 *   "nev": "Közlekedés",
 *   "verzio": 3,
 *   "visszavont": ["csomag|id|valami"],
 *   "alkalmazasok": [
 *     { "csomag": "hu.pelda.app", "nevek": [
 *         { "kulcs": "hu.pelda.app|id|hu.pelda.app:id/buy",
 *           "cimke": "Jegyvásárlás",
 *           "megjegyzes": "", "eredet": "szerkeszto",
 *           "verziotol": "", "verzioig": "",
 *           "ujjlenyomat": "" }
 *     ]}
 *   ]
 * }
 * ```
 */
object LabelPackStore {

    private const val TAG = "SDL_LABELPACK"

    /** Az ELVETETT közösségi címkék. Kicsi, ezért marad prefs-ben. */
    private const val PREFS_DISCARDED = "superdl_screenreader_pack_discards"

    /** A támogatott legmagasabb formátum-verzió. */
    private const val SUPPORTED_FORMAT = 1

    data class PackLabel(
        val key: String,
        val packageName: String,
        val label: String,
        val note: String,
        val origin: String,
        val fingerprint: ElementFingerprint.Print?,
        val packName: String
    )

    /**
     * A betöltött csomagok, csomagnév szerint csoportosítva.
     *
     * MEMÓRIÁBAN, nem prefs-ben: a csomag fájlként úgyis megvan a telefonon, és
     * ha átmásolnánk egy második tárba, a kettő idővel szétcsúszna. Egy igazság
     * van, a fájl.
     */
    @Volatile
    private var cache: Map<String, List<PackLabel>>? = null

    @Volatile
    private var loadedPackCount = 0

    private fun discards(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_DISCARDED, Context.MODE_PRIVATE)

    /** Új csomag érkezett vagy változott — a következő kérdésnél újraolvassuk. */
    fun invalidate() {
        cache = null
    }

    fun packCount(context: Context): Int {
        ensureLoaded(context)
        return loadedPackCount
    }

    fun labelCount(context: Context): Int {
        ensureLoaded(context)
        return cache?.values?.sumOf { it.size } ?: 0
    }

    // ── ELVETÉS ────────────────────────────────────────────────────────────

    fun isDiscarded(context: Context, key: String): Boolean =
        discards(context).getBoolean(key, false)

    fun discard(context: Context, key: String) {
        discards(context).edit().putBoolean(key, true).apply()
    }

    /** Meggondoltad magad: a közösségi címke visszakérése. */
    fun undiscard(context: Context, key: String) {
        discards(context).edit().remove(key).apply()
    }

    // ── KERESÉS ────────────────────────────────────────────────────────────

    /**
     * Közösségi név egy elemhez.
     *
     * ELŐBB pontos kulcs, UTÁNA ujjlenyomat — ugyanaz a sorrend, mint a saját
     * címkéknél, és ugyanaz a szigor: a bizonytalan találat bizonytalanként jön
     * vissza, nem állításként.
     */
    fun match(
        context: Context,
        node: android.view.accessibility.AccessibilityNodeInfo,
        packageName: String,
        exactKey: String?,
        screenWidth: Int,
        screenHeight: Int
    ): ScreenReaderLabels.Match? {
        ensureLoaded(context)
        val list = cache?.get(packageName)?.takeIf { it.isNotEmpty() } ?: return null

        if (exactKey != null) {
            list.firstOrNull { it.key == exactKey && !isDiscarded(context, it.key) }?.let {
                return ScreenReaderLabels.Match(it.label, sure = true, score = 1f)
            }
        }

        val current = ElementFingerprint.of(node, screenWidth, screenHeight) ?: return null
        var best: PackLabel? = null
        var bestScore = 0f
        for (entry in list) {
            val print = entry.fingerprint ?: continue
            if (isDiscarded(context, entry.key)) continue
            val s = ElementFingerprint.score(current, print)
            if (s > bestScore) {
                bestScore = s
                best = entry
            }
        }
        val hit = best ?: return null
        if (bestScore < ElementFingerprint.THRESHOLD_MAYBE) return null
        return ScreenReaderLabels.Match(
            hit.label,
            sure = bestScore >= ElementFingerprint.THRESHOLD_SURE,
            score = bestScore
        )
    }

    /** Minden közösségi címke — a címkekezelő listájához. */
    fun allLabels(context: Context): List<PackLabel> {
        ensureLoaded(context)
        return cache?.values?.flatten()?.sortedWith(
            compareBy({ it.packageName }, { it.label.lowercase() })
        ).orEmpty()
    }

    // ── BETÖLTÉS ───────────────────────────────────────────────────────────

    private fun ensureLoaded(context: Context) {
        if (cache != null) return
        synchronized(this) {
            if (cache != null) return
            cache = load(context)
        }
    }

    private fun load(context: Context): Map<String, List<PackLabel>> {
        val out = mutableMapOf<String, MutableList<PackLabel>>()
        var packs = 0
        try {
            for (moduleId in CatalogStore.installedIds(context, ModuleType.LABEL_PACK)) {
                val text = CatalogStore.readModule(context, moduleId) ?: continue
                val parsed = parse(text) ?: continue
                packs++
                for (entry in parsed) {
                    out.getOrPut(entry.packageName) { mutableListOf() }.add(entry)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "cimkecsomag betoltes hiba: ${e.message}")
        }
        loadedPackCount = packs
        return out
    }

    /**
     * Egy csomag JSON-jának feldolgozása.
     *
     * A visszavont kulcsok itt esnek ki — nem külön lépésben. Így nincs olyan
     * pillanat, amikor egy visszavont címke betöltve, de még nem szűrve lenne.
     */
    fun parse(text: String): List<PackLabel>? {
        return try {
            val root = JSONObject(text)
            val format = root.optInt("formatum", 1)
            if (format > SUPPORTED_FORMAT) {
                // ÚJABB formátum, mint amit ismerünk. NEM találgatunk: inkább
                // kihagyjuk az egész csomagot, mint hogy félig értsük.
                Log.w(TAG, "ujabb formatum ($format), a csomag kihagyva")
                return null
            }
            val packName = root.optString("nev", "Címkecsomag")

            val revoked = mutableSetOf<String>()
            root.optJSONArray("visszavont")?.let { arr ->
                for (i in 0 until arr.length()) revoked.add(arr.optString(i))
            }

            val out = mutableListOf<PackLabel>()
            val apps = root.optJSONArray("alkalmazasok") ?: return emptyList()
            for (i in 0 until apps.length()) {
                val app = apps.optJSONObject(i) ?: continue
                val pkg = app.optString("csomag").takeIf { it.isNotBlank() } ?: continue
                val names = app.optJSONArray("nevek") ?: continue
                for (j in 0 until names.length()) {
                    val n = names.optJSONObject(j) ?: continue
                    val key = n.optString("kulcs").takeIf { it.isNotBlank() } ?: continue
                    if (key in revoked) continue
                    val label = n.optString("cimke").takeIf { it.isNotBlank() } ?: continue
                    out.add(
                        PackLabel(
                            key = key,
                            packageName = pkg,
                            label = label,
                            note = n.optString("megjegyzes", ""),
                            origin = n.optString("eredet", ""),
                            fingerprint = ElementFingerprint.parse(n.optString("ujjlenyomat", "")),
                            packName = packName
                        )
                    )
                }
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "cimkecsomag ertelmezes hiba: ${e.message}")
            null
        }
    }
}
