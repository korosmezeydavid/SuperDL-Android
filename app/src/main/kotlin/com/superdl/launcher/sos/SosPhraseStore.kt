package com.superdl.launcher.sos

import android.content.Context
import com.superdl.launcher.assistant.VoiceAssistantHelper
import com.superdl.launcher.storage.JsonPrefsHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * S.O.S. HÍVÓMONDATOK — a hangos vészjelzés.
 *
 * ALPH KÉRÉSE (2026-09-03): „az sos-hoz egész nyugodtan lehessen szintén akár
 * elenával is szólni, betanítani egy mondatra, hogy ha ezt bemondom neki,
 * azonnal hívja az sos számokat kérdések nélkül."
 *
 * MIÉRT KÜLÖN TÁR, ÉS NEM AZ ELENA FELÉBRESZTŐ MONDATAI KÖZÉ:
 * a felébresztő mondat után a program VÁR egy parancsot — vészhelyzetben ez
 * egy fölösleges lépés. Ez a mondat NEM felébreszt, hanem CSELEKSZIK: amint
 * a figyelő meghallja, indul a lánc. Ezért kell külön listában tartani, és
 * ezért kell szigorúbb feltétel is hozzá (lásd lentebb).
 *
 * A TÉVES RIASZTÁS ELLEN HÁROM VÉDELEM VAN:
 *  1. A mondat legalább KÉT SZÓ és legalább TIZENKÉT karakter. Egy szó
 *     („segítség") beszélgetés közben is elhangozhat; egy egész mondat
 *     („kérem hívja a segítséget") nem szokott véletlenül kicsúszni.
 *  2. A felismert szövegnek a TELJES mondatot tartalmaznia kell, nem elég
 *     egy szava.
 *  3. A visszaszámlálás (S.O.S. paraméterek) itt is érvényes: ha be van
 *     kapcsolva, öt másodperc alatt egy balra söpréssel megállítható.
 *     Aki kikapcsolta, az tudatosan vállalta, hogy azonnal indul.
 */
object SosPhraseStore {

    private const val PREFS = "sos_phrase_prefs"
    private const val KEY_PHRASES = "phrases"
    private const val SCHEMA_VERSION_KEY = "schema_version"
    private const val CURRENT_SCHEMA = 1

    /** Ennél rövidebb mondatot nem fogadunk el — lásd a védelem 1. pontját. */
    const val MIN_LENGTH = 12

    /** Ennél több mondatot nem tartunk: a sok mondat sok téves riasztás. */
    const val MAX_PHRASES = 5

    data class SosPhrase(
        val id: String,
        val phrase: String,
        val createdAt: Long = System.currentTimeMillis()
    )

    fun all(context: Context): List<SosPhrase> {
        val array = JsonPrefsHelper.readJsonArray(
            context,
            PREFS,
            KEY_PHRASES,
            SCHEMA_VERSION_KEY,
            CURRENT_SCHEMA
        )
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val phrase = obj.optString("phrase").trim()
            if (phrase.isBlank()) return@mapNotNull null
            SosPhrase(
                id = obj.optString("id", "sos_phrase_$index"),
                phrase = phrase,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }

    fun isEmpty(context: Context): Boolean = all(context).isEmpty()

    /**
     * Elfogadható-e a mondat? A hívó ebből tudja, MIT mondjon a
     * felhasználónak — ezért nem csak igen-nem, hanem indoklás is jár.
     * Null = rendben van.
     */
    fun rejectReason(context: Context, raw: String): String? {
        val normalized = VoiceAssistantHelper.normalize(raw)
        return when {
            normalized.length < MIN_LENGTH ->
                "Ez a mondat túl rövid. Mondj egy egész mondatot, legalább két szót, " +
                    "hogy beszélgetés közben véletlenül ne hangozzon el."
            normalized.split(" ").filter { it.isNotBlank() }.size < 2 ->
                "Egy szó kevés, mert véletlenül is kimondhatod. Mondj egy egész mondatot."
            all(context).any { VoiceAssistantHelper.normalize(it.phrase) == normalized } ->
                "Ez a mondat már be van tanítva."
            all(context).size >= MAX_PHRASES ->
                "Elérted a maximum $MAX_PHRASES hívómondatot. Előbb törölj egyet."
            else -> null
        }
    }

    fun add(context: Context, raw: String): Boolean {
        if (rejectReason(context, raw) != null) return false
        val phrases = all(context).toMutableList()
        phrases.add(
            SosPhrase(
                id = "sos_phrase_${System.currentTimeMillis()}",
                phrase = VoiceAssistantHelper.normalize(raw)
            )
        )
        save(context, phrases)
        return true
    }

    fun remove(context: Context, id: String): Boolean {
        val phrases = all(context)
        if (phrases.none { it.id == id }) return false
        save(context, phrases.filter { it.id != id })
        return true
    }

    /**
     * Elhangzott-e a vészjelző mondat? A TELJES mondatnak benne kell lennie
     * a felismert szövegben — így az sem baj, ha a felismerő elé-mögé rak
     * egy szót, de egyetlen közös szó még nem indít riasztást.
     */
    fun matches(context: Context, spoken: String): Boolean {
        val normalized = VoiceAssistantHelper.normalize(spoken)
        if (normalized.length < MIN_LENGTH) return false
        return all(context).any { saved ->
            val phrase = VoiceAssistantHelper.normalize(saved.phrase)
            phrase.length >= MIN_LENGTH && normalized.contains(phrase)
        }
    }

    fun speakAll(context: Context): String {
        val phrases = all(context)
        if (phrases.isEmpty()) {
            return "Nincs betanított S.O.S. hívómondat. Az S.O.S. hívómondat tanítása " +
                "menüpontban tudsz felvenni egyet."
        }
        return "${phrases.size} S.O.S. hívómondat: " +
            phrases.joinToString(". ") { it.phrase } + "."
    }

    private fun save(context: Context, phrases: List<SosPhrase>) {
        val array = JSONArray()
        for (phrase in phrases) {
            array.put(
                JSONObject()
                    .put("id", phrase.id)
                    .put("phrase", phrase.phrase)
                    .put("createdAt", phrase.createdAt)
            )
        }
        JsonPrefsHelper.saveJsonArray(
            context,
            PREFS,
            KEY_PHRASES,
            SCHEMA_VERSION_KEY,
            CURRENT_SCHEMA,
            array
        )
    }
}
