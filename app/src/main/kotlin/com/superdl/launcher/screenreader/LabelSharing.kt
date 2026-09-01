package com.superdl.launcher.screenreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * BEKÜLDÉS — amit egy tesztelő felcímkéz, azt a többiek is megkapják.
 *
 * A GONDOLAT: egy vak ember egyszer végigszenvedi a rossz banki alkalmazás
 * névtelen gombjait, és utána mindenki másnak már megy. Ez az a munka, amit
 * elég egyszer elvégezni — de csak akkor, ha van útja visszafelé is.
 *
 * ── ALAPBÓL KIKAPCSOLVA ────────────────────────────────────────────────────
 *
 * A gépezet megépül, de a tesztelői fázisban NEM MEGY MAGÁTÓL. Ötven
 * tesztelővel a mennyiség még elfér a szerkesztőnél, és a tár első pár száz
 * címkéje adja meg a hangnemet — az érdemes, hogy kézből menjen. Amikor a
 * beküldés több lesz, mint amennyi átnézhető: egy kapcsoló, és megy magától.
 *
 * ── ADATVÉDELEM: A LEGFONTOSABB SZABÁLY ────────────────────────────────────
 *
 * A beküldés SOHA nem visz magával képernyőszöveget, nevet, összeget,
 * üzenetet. Csak az elem SZERKEZETI ujjlenyomatát és az ÁLTALAD BEÍRT nevet.
 * Ezt nem elég szándékként kimondani: az ujjlenyomat maga sem tartalmaz
 * szöveget (lásd ElementFingerprint), tehát nincs is mit kiszivárogtatni.
 * A hibajelentőnél már megvan ez az elv; ugyanaz a szigor áll itt is.
 *
 * ── EGY KÉSZÜLÉK EGY SZAVAZAT ──────────────────────────────────────────────
 *
 * A hármas küszöb csak akkor jelent valamit, ha három KÜLÖNBÖZŐ embertől jön.
 * Ehhez a beküldés mellé kell egy azonosító — de olyan, ami SEMMIT nem árul el
 * rólad. Ezért egy véletlen szám, amit a telefon maga sorsol az első
 * beküldéskor, és soha nem hagyja el a készüléket másképp, csak ezzel együtt.
 * Nem a rosszindulat ellen szól: három telefonja bárkinek lehet. Azért kell,
 * mert enélkül a küszöb csak elméletben létezne.
 */
object LabelSharing {

    private const val PREFS = "superdl_screenreader_sharing"
    private const val KEY_ENABLED = "sharing_enabled"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_LAST_SENT = "last_sent_at"

    /** A beküldés formátumának verziója — a fogadó oldal ebből tudja, mit kap. */
    const val FORMAT_VERSION = 1

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── A FŐKAPCSOLÓ ───────────────────────────────────────────────────────

    /**
     * ALAPBÓL KI. Ez nem óvatoskodás: a beküldés a felhasználó munkáját adja
     * tovább másoknak, és ilyet magától senki nem tesz meg helyette.
     */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun toggle(context: Context): Boolean {
        val next = !isEnabled(context)
        prefs(context).edit().putBoolean(KEY_ENABLED, next).apply()
        return next
    }

    /** A készülék véletlen azonosítója. Az első kérésre születik meg. */
    fun deviceId(context: Context): String {
        prefs(context).getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString().take(12)
        prefs(context).edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    fun lastSentAt(context: Context): Long = prefs(context).getLong(KEY_LAST_SENT, 0L)

    fun markSent(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_SENT, System.currentTimeMillis()).apply()
    }

    // ── VESZÉLYES SZAVAK ───────────────────────────────────────────────────

    /**
     * Ezek a címkék SOHA nem élesedhetnek automatikusan, hárman sem.
     *
     * MIÉRT: a tévedés ára itt nem egy rossz név, hanem egy elveszett fájl vagy
     * egy elutalt összeg. Ha valaki rossz gombra írja rá, hogy "Mégse", és az
     * a gomb valójában fizet, akkor a hármas küszöb nem véd meg senkit — csak
     * gyorsabban terjeszti a bajt.
     *
     * Ugyanaz a szigor, amiért a bankjegyfelismerő kimaradt a tesztváltozatból.
     *
     * A jelölés a beküldésben megy vele: a szerkesztő látja, hogy ezt kézbe
     * kell venni. A felhasználónál semmi nem tiltódik le — a SAJÁT címkéje az
     * övé, azt bármire elnevezheti.
     */
    private val DANGEROUS_WORDS = listOf(
        "töröl", "torol", "fizet", "elküld", "elkuld", "küld", "kuld",
        "véglegesít", "veglegesit", "kilép", "kilep", "utal", "vásárol",
        "vasarol", "megerősít", "megerosit", "jóváhagy", "jovahagy",
        "megszüntet", "megszuntet", "leállít", "leallit", "formáz", "formaz"
    )

    fun isDangerous(label: String): Boolean {
        val l = label.lowercase().trim()
        return DANGEROUS_WORDS.any { l.contains(it) }
    }

    // ── A BEKÜLDÉS ÖSSZEÁLLÍTÁSA ───────────────────────────────────────────

    data class Bundle(
        val json: String,
        val labelCount: Int,
        val appCount: Int,
        val dangerousCount: Int,
        /** Emberi nyelvű összefoglaló, hogy a jóváhagyás előtt HALLD, mit küldesz. */
        val summary: String
    )

    /**
     * A beküldendő csomag összeállítása.
     *
     * CSAK azok a címkék mennek, amikhez van UJJLENYOMAT. Ujjlenyomat nélkül
     * a címke csak a te telefonodon jelent valamit — máshol egy értelmetlen
     * kulcs lenne, ami senkinek nem találna semmit. Jobb nem elküldeni, mint
     * szemetet küldeni.
     *
     * @return null, ha nincs mit küldeni
     */
    fun build(context: Context): Bundle? {
        val entries = ScreenReaderLabels.allLabels(context)
            .filter { ScreenReaderLabels.hasFingerprint(context, it.key) }
        if (entries.isEmpty()) return null

        val byApp = entries.groupBy { it.packageName }
        var dangerous = 0

        val apps = JSONArray()
        for ((pkg, list) in byApp) {
            val names = JSONArray()
            for (e in list) {
                val risky = isDangerous(e.label)
                if (risky) dangerous++
                names.put(
                    JSONObject().apply {
                        put("kulcs", e.key)
                        put("cimke", e.label)
                        put("ujjlenyomat", ScreenReaderLabels.fingerprintRaw(context, e.key).orEmpty())
                        // A JELÖLÉS VELE MEGY. Nem a fogadó oldalra bízzuk,
                        // hogy majd észreveszi: a küldő oldal is tudja, mit
                        // kell kézbe venni.
                        put("veszelyes", risky)
                    }
                )
            }
            apps.put(
                JSONObject().apply {
                    put("csomag", pkg)
                    put("nevek", names)
                }
            )
        }

        val root = JSONObject().apply {
            put("formatum", FORMAT_VERSION)
            put("kuldo", deviceId(context))
            put("darab", entries.size)
            put("alkalmazasok", apps)
        }

        val summary = buildString {
            append("${entries.size} elnevezés, ${byApp.size} alkalmazásból. ")
            if (dangerous > 0) {
                append(
                    "Ebből $dangerous olyan szót tartalmaz, ami műveletet jelent — " +
                        "töröl, fizet, elküld. Ezeket a szerkesztő kézbe veszi, " +
                        "nem élesednek automatikusan. "
                )
            }
            append("Képernyőszöveg, név, összeg NEM megy velük — csak az elem szerkezete és a beírt név.")
        }

        return Bundle(
            json = root.toString(2),
            labelCount = entries.size,
            appCount = byApp.size,
            dangerousCount = dangerous,
            summary = summary
        )
    }
}
