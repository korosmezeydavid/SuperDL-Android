package com.superdl.launcher.catalog

import android.content.Context
import android.util.Log
import com.superdl.launcher.net.NetworkHelper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * A KATALÓGUS letöltése és a modulok kezelése.
 *
 * MŰKÖDÉS:
 *  1. Letöltjük a katalógus-fájlt (mi érhető el).
 *  2. A felhasználó kiválaszt egy modult.
 *  3. Letöltjük a modul adatfájlját a telefonra.
 *  4. A SuperDL saját motorja onnantól helyben olvassa — internet NEM kell hozzá.
 *
 * BIZTONSÁG: kizárólag JSON adatot töltünk le, SOHA nem futtatható kódot.
 *
 * ── MIÉRT MONDJUK MEG, MI A BAJ (2026-09-12) ────────────────────────────
 *
 * A HIBA, AMI EZT KIKÉNYSZERÍTETTE: egy új tesztelő ennyit írt a
 * jelentésébe: „a játékok nem töltöttek le". Utánamértük — a szerveren mind
 * a 31 modul rendben volt, HTTP 200-zal. A hiba tehát a telefonon történt,
 * csakhogy a program NEM MONDTA MEG, melyik.
 *
 * A régi `download()` ugyanazt a `null`-t adta vissza a 404-re (nincs
 * feltöltve a fájl), az 500-ra, a 403-ra (a GitHub kérés-korlátja), a
 * húszmásodperces időtúllépésre és a névfeloldási hibára. Ebből lett az az
 * egy mondat, hogy „A katalógus nem érhető el. Van internet?" — ami olyankor
 * is elhangzott, amikor bőven volt internet. A felhasználó a wifit
 * kapcsolgatta egy 404 miatt.
 *
 * Ugyanez a hibaosztály már meg volt oldva az `AppUpdateInstaller`-ben
 * (`lastInstallError`), és a kommentje szó szerint így szól: „és pont ez a
 * mondat rejtett el egy hibát hetekig." A katalógus maradt a régi hibában.
 *
 * MOSTANTÓL a `download()` nem `null`-t ad, hanem OKOT. Az ok pedig eljut a
 * felhasználóig — mert a hibajelentésbe csak az kerül be, amit ő hallott.
 */
object CatalogClient {

    private const val TAG = "SDL_CATALOG"

    /**
     * A katalógus címe. GitHub-on a "raw" cím a fájl nyers tartalmát adja.
     * A felhasználó saját tárolójára cserélhető.
     */
    var baseUrl: String = "https://raw.githubusercontent.com/korosmezeydavid/SuperDL/mobil/"

    private const val CATALOG_FILE = "mobil-katalogus.json"
    private const val TIMEOUT_MS = 20_000

    /** A letöltött modulok helye a telefonon. */
    fun modulesDir(context: Context): File =
        File(context.filesDir, "katalogus").apply { mkdirs() }

    fun moduleFile(context: Context, moduleId: String): File =
        File(modulesDir(context), "$moduleId.json")

    // ── A LETÖLTÉS EREDMÉNYE ────────────────────────────────────────────────

    /**
     * MIÉRT NEM `String?`: mert a `null` nem mond semmit. Ez a típus
     * kényszeríti ki, hogy minden bukásnak legyen OKA, és az ok emberi
     * nyelven legyen megfogalmazva már itt — ne a hívó találgassa ki.
     */
    private sealed class Fetched {
        data class Ok(val text: String) : Fetched()
        data class Fail(val reason: String) : Fetched()
    }

    // ── A KATALÓGUS LETÖLTÉSE ───────────────────────────────────────────────

    data class CatalogResult(
        val modules: List<CatalogModule>,
        val error: String?
    )

    /** HÁTTÉRSZÁLRÓL hívandó. */
    fun fetchCatalog(context: Context): CatalogResult {
        offlineReason(context)?.let { return CatalogResult(emptyList(), it) }
        return try {
            val text = when (val r = download(baseUrl + CATALOG_FILE)) {
                is Fetched.Ok -> r.text
                is Fetched.Fail -> return CatalogResult(emptyList(), "A katalógus nem érhető el: ${r.reason}")
            }
            val root = JSONObject(text)
            // A lista neve lehet "modules" (a tárolóban ez van) vagy "modulok" —
            // MINDKETTŐT elfogadjuk, hogy egy elnevezés-váltás ne törje el.
            val array = root.optJSONArray("modules")
                ?: root.optJSONArray("modulok")
                ?: return CatalogResult(emptyList(), "A katalógus üres vagy hibás.")

            val out = mutableListOf<CatalogModule>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                out.add(
                    CatalogModule(
                        id = o.optString("id"),
                        name = o.optString("nev", "Névtelen modul"),
                        type = ModuleType.fromKey(o.optString("tipus")),
                        version = o.optInt("verzio", 1),
                        description = o.optString("leiras", ""),
                        sizeBytes = o.optLong("meret", 0L),
                        filePath = o.optString("fajl"),
                        minAppVersion = o.optString("minAlkalmazasVerzio", "1.0.0"),
                        categoryId = o.optString("kategoria", ""),
                        author = o.optString("szerzo", "")
                    )
                )
            }
            Log.i(TAG, "katalogus betoltve: ${out.size} modul")
            CatalogResult(out, null)
        } catch (e: Exception) {
            Log.w(TAG, "katalogus hiba: ${e.message}")
            CatalogResult(emptyList(), "A katalógus szövegét nem sikerült értelmezni. Ez a mi hibánk, nem a tiéd.")
        }
    }

    /**
     * Egy modul letöltése a telefonra.
     * @return null ha sikerült, különben a hiba emberi nyelven
     */
    fun downloadModule(context: Context, module: CatalogModule): String? {
        offlineReason(context)?.let { return it }

        // MIÉRT ITT ELLENŐRIZZÜK A VERZIÓT: a `minAppVersion` mezőt eddig
        // beolvastuk a katalógusból, eltároltuk — és SOHA nem néztük meg.
        // Ez a „jó szolgáltatás, amit senki nem hív" mintája. A következménye
        // nem elméleti: egy újabb alkalmazást igénylő modul letöltődött, a
        // motor nem tudta értelmezni, és a játék azt mondta rá, hogy „még
        // nincs letöltött kvíz". Vagyis a felhasználó egy VÉGTELEN KÖRBE
        // került, és semmi nem árulta el, miért.
        versionBlockReason(context, module)?.let { return it }

        return try {
            val text = when (val r = download(baseUrl + module.filePath)) {
                is Fetched.Ok -> r.text
                is Fetched.Fail -> return "A modul nem tölthető le: ${r.reason}"
            }
            // Ellenőrzés: tényleg értelmes JSON-t kaptunk?
            JSONObject(text)

            // ATOMI ÍRÁS. Előbb ideiglenes fájlba, aztán átnevezés.
            //
            // MIÉRT: a régi változat egyenesen a helyére írt. Ha a telefon
            // közben leállította a programot — MIUI-n ez a leggyakoribb eset —,
            // egy FÉLBEVÁGOTT fájl maradt a helyén. A `markInstalled` ilyenkor
            // ugyan nem futott le, de a fájl ott volt, és a motor csendben
            // elbukott rajta.
            val target = moduleFile(context, module.id)
            val tmp = File(target.parentFile, "${module.id}.json.tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                // Ha az átnevezés nem megy (van, ahol a cél létezik), másoljuk.
                target.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }

            CatalogStore.markInstalled(context, module.id, module.version, module.type)
            // A címkecsomagok memóriában vannak; egy új vagy frissült csomag
            // után újra kell olvasni őket, különben a régit mondaná tovább.
            if (module.type == ModuleType.LABEL_PACK) {
                com.superdl.launcher.screenreader.LabelPackStore.invalidate()
            }
            if (module.type == ModuleType.ROUTE_PACK) {
                com.superdl.launcher.macro.RoutePackStore.invalidate()
            }
            Log.i(TAG, "modul letoltve: ${module.id} v${module.version}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "modul letoltes hiba (${module.id}): ${e.message}")
            when (e) {
                is java.io.IOException ->
                    "A letöltés nem sikerült: nem tudtam a telefonra írni. Lehet, hogy betelt a tárhely."
                else ->
                    "A letöltés nem sikerült: a fájl tartalma hibás. Ez a mi hibánk, nem a tiéd."
            }
        }
    }

    /**
     * EGY MODUL SZÖVEGE, TELEPÍTÉS NÉLKÜL.
     *
     * MIÉRT KELL KÜLÖN: a beszédtémáknál a katalógus nem „letölt", hanem
     * ELŐHALLGATÁST kínál — meghallgatod, és csak akkor kerül a helyére, ha
     * kéred. Ehhez a tartalom kell, a `markInstalled` viszont NEM: attól a
     * program azt hinné, hogy a téma már a tiéd.
     *
     * HÁTTÉRSZÁLRÓL hívandó.
     */
    fun fetchModuleText(module: CatalogModule): String? =
        when (val r = download(baseUrl + module.filePath)) {
            is Fetched.Ok -> r.text
            is Fetched.Fail -> null
        }

    /**
     * Egy már letöltött szöveg elkönyvelése telepítettként.
     * Az előhallgatás után ezzel zárjuk le a kört, hogy a katalógus is
     * „letöltve" állapotot mondjon rá.
     *
     * @return null ha sikerült, különben a hiba emberi nyelven. EDDIG semmit
     *         nem adott vissza, és a hívó nem tudta meg, ha elbukott: a téma
     *         megvolt, a katalógus mégis azt mondta rá, hogy „nincs letöltve".
     */
    fun markModuleInstalled(context: Context, module: CatalogModule): String? = try {
        CatalogStore.markInstalled(context, module.id, module.version, module.type)
        null
    } catch (e: Exception) {
        Log.w(TAG, "markInstalled hiba (${module.id}): ${e.message}")
        "A modul megvan, de nem sikerült elkönyvelni. Próbáld újra letölteni."
    }

    // ── ELLENŐRZÉSEK A LETÖLTÉS ELŐTT ───────────────────────────────────────

    /**
     * HÁLÓZAT-ELLENŐRZÉS ELŐRE.
     *
     * A `NetworkHelper` doc-commentje pontosan ezt a csapdát írja le — „húsz
     * másodpercig vár, majd annyit mond: ismeretlen cím" —, és mégsem hívta
     * senki a `catalog` csomagból. Hálózat nélkül ma húsz másodperc néma
     * várakozás jön, és pont ez alatt lép ki a felhasználó, amivel maga
     * okozza, hogy az eredményt már ne hallja meg.
     */
    private fun offlineReason(context: Context): String? =
        if (NetworkHelper.isOnline(context)) null
        else "Nincs internetkapcsolat. A már letöltött kérdéssorok hálózat nélkül is működnek."

    /** Igaz, ha ezt a modult a mostani alkalmazás-verzió tudja kezelni. */
    fun isSupported(context: Context, module: CatalogModule): Boolean =
        versionBlockReason(context, module) == null

    private fun versionBlockReason(context: Context, module: CatalogModule): String? {
        val needed = module.minAppVersion.trim()
        if (needed.isBlank()) return null
        val current = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (_: Exception) {
            return null // Ha nem tudjuk megállapítani, INKÁBB ENGEDJÜK.
        }
        if (current.isBlank()) return null
        return if (compareVersions(current, needed) < 0) {
            "Ehhez a modulhoz újabb Super DL kell: $needed. A tiéd most $current. " +
                "Frissítsd a programot, aztán töltsd le."
        } else {
            null
        }
    }

    /**
     * Két pontokkal tagolt verzió összehasonlítása.
     *
     * Szándékosan elnéző: ami nem szám, azt nullának veszi, és a rövidebbet
     * nullákkal tölti fel. Egy elgépelt verziószám a katalógusban NE zárjon
     * ki egy modult — inkább engedjük át, mint hogy elérhetetlen legyen.
     */
    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".", "-", " ").mapNotNull { it.trim().toIntOrNull() }
        val pb = b.split(".", "-", " ").mapNotNull { it.trim().toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    // ── A NYERS LETÖLTÉS ────────────────────────────────────────────────────

    private fun download(url: String): Fetched {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SuperDL")
            }
            val code = conn.responseCode
            if (code != 200) {
                Log.w(TAG, "letoltes valasz: $code ($url)")
                return Fetched.Fail(httpReason(code))
            }
            Fetched.Ok(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (e: Exception) {
            Log.w(TAG, "letoltes hiba: ${e.javaClass.simpleName} ${e.message}")
            Fetched.Fail(exceptionReason(e))
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * A HTTP-kód emberi nyelven — ÉS MEGMONDJA, KIN MÚLIK.
     *
     * Ez a legfontosabb része: a felhasználónak nem a szám kell, hanem az,
     * hogy tud-e vele kezdeni valamit. A 404 a mi hibánk, és hiába próbálja
     * újra; az 503 elmúlik magától; a 403 kivárás kérdése.
     */
    private fun httpReason(code: Int): String = when (code) {
        403 -> "a kiszolgáló most nem enged több letöltést. Várj pár percet, és próbáld újra."
        404 -> "ez a fájl nincs a kiszolgálón. Ez a mi hibánk, nem a tiéd — kérlek jelezd."
        in 500..599 -> "a kiszolgáló hibát jelez. Ez nem a te telefonod, próbáld később."
        else -> "a kiszolgáló $code kóddal válaszolt."
    }

    private fun exceptionReason(e: Exception): String = when (e) {
        is java.net.SocketTimeoutException ->
            "a kiszolgáló nem válaszolt időben. Gyenge lehet a térerő."
        is java.net.UnknownHostException ->
            "nem érem el a kiszolgálót. Ellenőrizd az internetkapcsolatot."
        is javax.net.ssl.SSLException ->
            "a biztonságos kapcsolat nem jött létre. Ellenőrizd a telefon dátumát és idejét."
        is java.io.IOException ->
            "megszakadt a kapcsolat letöltés közben."
        else ->
            "váratlan hiba: ${e.javaClass.simpleName}."
    }
}
