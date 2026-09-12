package com.superdl.launcher.games.quiz

import android.content.Context
import android.util.Log
import org.json.JSONObject
import com.superdl.launcher.catalog.CatalogStore
import com.superdl.launcher.catalog.ModuleType

/** Egy kérdés a kvízből. */
data class QuizQuestion(
    val question: String,
    val answers: List<String>,
    val correctIndex: Int,
    val explanation: String
) {
    fun isCorrect(index: Int): Boolean = index == correctIndex
}

/** Egy betöltött kvíz-modul. */
data class QuizSet(
    val id: String,
    val name: String,
    val questions: List<QuizQuestion>
)

/**
 * A KVÍZ-MOTOR: a katalógusból letöltött kérdéssorok betöltése.
 *
 * A motor az alkalmazásban van, a TARTALOM a katalógusból jön. Így egy új
 * kvízhez nem kell új alkalmazás-verzió — elég feltölteni egy JSON fájlt.
 *
 * ── MIÉRT MONDJA MEG, HA HIBÁS (2026-09-12) ─────────────────────────────
 *
 * A `loadAll` egy `mapNotNull` volt, a `load` pedig NÉGY helyen adott vissza
 * `null`-t, egyetlen napló-sor nélkül. A következmény nem elméleti volt:
 *
 *   - a katalógus „letöltve"-t mondott a modulra (a `CatalogStore` tudta),
 *   - a játék viszont azt, hogy „Még nincs letöltött kvíz",
 *   - és visszairányította a felhasználót ABBA a menübe, ahol már volt.
 *
 * Végtelen kör, és semmi nem árulta el, hogy a fájl hibás. Elég volt hozzá
 * egy csonka letöltés vagy egy elgépelt kulcs a JSON-ban.
 *
 * Mostantól minden kiesésnek OKA van, és az ok eljut a felhasználóig.
 */
object QuizLoader {

    private const val TAG = "SDL_QUIZ"

    /**
     * A betöltés teljes eredménye.
     *
     * @param sets a használható kérdéssorok
     * @param brokenIds azok a modulok, amiket a katalógus telepítettként
     *        tart nyilván, de a motor nem tud elolvasni
     */
    data class LoadResult(
        val sets: List<QuizSet>,
        val brokenIds: List<String>
    ) {
        /**
         * Mit mondjunk, ha NINCS használható kvíz.
         *
         * A két eset gyökeresen más, és eddig ugyanazt hallotta rájuk a
         * felhasználó. Az egyikben tölteni kell, a másikban ÚJRA tölteni —
         * és az utóbbit a régi mondat nem csak elhallgatta, hanem aktívan
         * félrevezetett vele.
         */
        fun speakWhyEmpty(): String = if (brokenIds.isEmpty()) {
            "Még nincs letöltött kvíz. A Beállítások, Katalógus, Elérhető modulok " +
                "pontban tölthetsz le kérdéssorokat. Söpörj jobbra, és odaviszlek."
        } else if (brokenIds.size == 1) {
            "Egy letöltött kérdéssor hibás, ezért nem tudom megnyitni. " +
                "Töltsd le újra a Beállítások, Katalógus, Elérhető modulok pontban. " +
                "Söpörj jobbra, és odaviszlek."
        } else {
            "${brokenIds.size} letöltött kérdéssor hibás, ezért nem tudom megnyitni. " +
                "Töltsd le újra őket a Beállítások, Katalógus, Elérhető modulok pontban. " +
                "Söpörj jobbra, és odaviszlek."
        }

        /** Mit mondjunk, ha VAN használható kvíz, de valamelyik hibás. */
        fun speakPartialWarning(): String? = when {
            brokenIds.isEmpty() -> null
            brokenIds.size == 1 -> "Egy kérdéssor hibás, azt kihagytam. Érdemes újra letölteni."
            else -> "${brokenIds.size} kérdéssor hibás, azokat kihagytam. Érdemes újra letölteni őket."
        }
    }

    /**
     * A letöltött kvíz-modulok betöltése, a hibásak számbavételével.
     */
    fun loadAllDetailed(context: Context): LoadResult {
        val ids = CatalogStore.installedIds(context, ModuleType.QUIZ)
        val sets = mutableListOf<QuizSet>()
        val broken = mutableListOf<String>()
        ids.forEach { id ->
            val set = load(context, id)
            if (set != null) sets.add(set) else broken.add(id)
        }
        if (broken.isNotEmpty()) {
            Log.w(TAG, "hibas kvizmodulok: ${broken.joinToString(", ")}")
        }
        return LoadResult(sets, broken)
    }

    /** A letöltött kvíz-modulok betöltése. */
    fun loadAll(context: Context): List<QuizSet> = loadAllDetailed(context).sets

    fun load(context: Context, moduleId: String): QuizSet? {
        val text = CatalogStore.readModule(context, moduleId)
        if (text == null) {
            // A katalógus szerint telepítve van, a fájl mégsem olvasható:
            // törölte a rendszer takarítója, vagy félbeszakadt a letöltés.
            Log.w(TAG, "$moduleId: a fajl nem olvashato")
            return null
        }
        return try {
            val root = JSONObject(text)
            val array = root.optJSONArray("kerdesek")
            if (array == null) {
                Log.w(TAG, "$moduleId: nincs 'kerdesek' tomb a fajlban")
                return null
            }
            val questions = mutableListOf<QuizQuestion>()
            var skipped = 0
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val answersArray = o.optJSONArray("valaszok")
                if (answersArray == null) {
                    skipped++
                    continue
                }
                val answers = (0 until answersArray.length()).map { answersArray.getString(it) }
                if (answers.size < 2) {
                    skipped++
                    continue
                }
                questions.add(
                    QuizQuestion(
                        question = o.optString("kerdes"),
                        answers = answers,
                        correctIndex = o.optInt("helyes", 0).coerceIn(0, answers.lastIndex),
                        explanation = o.optString("magyarazat", "")
                    )
                )
            }
            if (skipped > 0) {
                Log.w(TAG, "$moduleId: $skipped kerdes kihagyva (hianyos valaszok)")
            }
            if (questions.isEmpty()) {
                Log.w(TAG, "$moduleId: egyetlen hasznalhato kerdes sincs benne")
                return null
            }
            QuizSet(
                id = moduleId,
                name = root.optString("nev", moduleId),
                // A kérdések SORRENDJE keveredik, hogy másodszorra se legyen
                // ugyanaz — így ismételve is van értelme játszani.
                questions = questions.shuffled()
            )
        } catch (e: Exception) {
            Log.w(TAG, "$moduleId: ertelmezesi hiba - ${e.message}")
            null
        }
    }
}
