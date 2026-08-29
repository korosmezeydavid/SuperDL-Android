package com.superdl.launcher.games.hangman

import android.content.Context
import org.json.JSONObject
import com.superdl.launcher.catalog.CatalogStore
import com.superdl.launcher.catalog.ModuleType

/**
 * AKASZTÓFA JÁTÉK — a katalógusból letöltött szókészletekkel.
 *
 * A motor itt van, a SZAVAK a katalógusból jönnek. Így új szókészlethez nem
 * kell új alkalmazás-verzió: elég feltölteni egy fájlt.
 */
data class HangmanState(
    val word: String,
    val guessed: Set<Char>,
    val wrongCount: Int,
    val maxWrong: Int = 8
) {
    /** Felolvasható alak: a rejtett betűket "üres"-nek mondjuk. */
    fun speakWord(): String =
        word.map {
            when {
                !it.isLetter() -> it.toString()
                it.lowercaseChar() in guessed -> it.toString()
                else -> "üres"
            }
        }.joinToString(", ")

    /** Kijelzőn megjelenő alak. */
    fun maskedWord(): String =
        word.map { if (it.lowercaseChar() in guessed || !it.isLetter()) it else '_' }
            .joinToString(" ")

    val isWon: Boolean
        get() = word.all { !it.isLetter() || it.lowercaseChar() in guessed }

    val isLost: Boolean get() = wrongCount >= maxWrong

    val livesLeft: Int get() = maxWrong - wrongCount
}

object HangmanLoader {

    /** Alapszavak, ha nincs letöltött szókészlet — így a játék mindig indítható. */
    private val BUILT_IN = listOf(
        "alma", "asztal", "barát", "cica", "csillag", "erdő", "felhő", "gomba",
        "hajó", "iskola", "kenyér", "könyv", "labda", "madár", "napsütés",
        "ablak", "virág", "vonat", "zene", "tenger", "torony", "szekrény"
    )

    /** A letöltött szókészletek betöltése (több modul szavai összefésülve). */
    fun loadWords(context: Context): List<String> {
        val ids = CatalogStore.installedIds(context, ModuleType.WORD_GAME)
        val words = mutableListOf<String>()
        ids.forEach { id ->
            val text = CatalogStore.readModule(context, id) ?: return@forEach
            try {
                val array = JSONObject(text).optJSONArray("szavak") ?: return@forEach
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.length >= 3 }?.let { words += it }
                }
            } catch (_: Exception) {
            }
        }
        return if (words.isEmpty()) BUILT_IN else words
    }

    fun newGame(context: Context): HangmanState =
        HangmanState(word = loadWords(context).random(), guessed = emptySet(), wrongCount = 0)

    /** A magyar ábécé betűi a találgatáshoz. */
    val ALPHABET: List<Char> = "aábcdeéfghiíjklmnoóöőprstuúüűvz".toList()
}
