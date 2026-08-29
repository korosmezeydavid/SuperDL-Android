package com.superdl.launcher.games.quiz

import android.content.Context
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
 */
object QuizLoader {

    /** A letöltött kvíz-modulok betöltése. */
    fun loadAll(context: Context): List<QuizSet> {
        val ids = CatalogStore.installedIds(context, ModuleType.QUIZ)
        return ids.mapNotNull { load(context, it) }
    }

    fun load(context: Context, moduleId: String): QuizSet? {
        val text = CatalogStore.readModule(context, moduleId) ?: return null
        return try {
            val root = JSONObject(text)
            val array = root.optJSONArray("kerdesek") ?: return null
            val questions = mutableListOf<QuizQuestion>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val answersArray = o.optJSONArray("valaszok") ?: continue
                val answers = (0 until answersArray.length()).map { answersArray.getString(it) }
                if (answers.size < 2) continue
                questions.add(
                    QuizQuestion(
                        question = o.optString("kerdes"),
                        answers = answers,
                        correctIndex = o.optInt("helyes", 0).coerceIn(0, answers.lastIndex),
                        explanation = o.optString("magyarazat", "")
                    )
                )
            }
            if (questions.isEmpty()) return null
            QuizSet(
                id = moduleId,
                name = root.optString("nev", moduleId),
                // A kérdések SORRENDJE keveredik, hogy másodszorra se legyen
                // ugyanaz — így ismételve is van értelme játszani.
                questions = questions.shuffled()
            )
        } catch (_: Exception) {
            null
        }
    }
}
