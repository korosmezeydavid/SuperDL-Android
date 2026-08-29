package com.superdl.launcher.book

import java.text.Normalizer
import java.util.Locale

object BookSearchHelper {

    fun search(books: List<BookEntry>, query: String): List<BookEntry> {
        val tokens = normalize(query).split(' ').filter { it.length >= 2 }
        if (tokens.isEmpty()) return emptyList()
        return books.filter { book ->
            val hay = normalize("${book.title} ${book.format}")
            tokens.all { token -> hay.contains(token) }
        }
    }

    /**
     * A cím és a keresett szöveg EGYSÉGES alakra hozása.
     *
     * JAVÍTVA — ez volt az ékezetes címek hibájának oka:
     * a régi változat előbb LESZEDTE az ékezeteket (á -> a), majd egy olyan
     * szűrőt futtatott, ami "megtartotta volna" őket. Addigra viszont már nem
     * voltak ott, a magyar KETTŐS ékezetek (ő, ű) maradéka pedig kiesett a
     * szűrőn — így az "őrült" szóból "rlt" lett, és semmit nem talált.
     *
     * Most a magyar ékezeteket ELŐBB a saját párjukra cseréljük (ő -> o),
     * és csak utána takarítunk. Így az "Őrült" és az "orult" is ugyanazt adja,
     * tehát a felhasználó ékezet nélkül is megtalálja a könyvet — és fordítva.
     */
    private fun normalize(text: String): String {
        val lower = text.lowercase(Locale("hu", "HU"))
        // 1. MAGYAR ÉKEZETEK kézzel — a szabványos leszedés a kettős
        //    ékezeteknél (ő, ű) megbízhatatlan.
        val hungarian = lower
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ö', 'o').replace('ő', 'o')
            .replace('ú', 'u').replace('ü', 'u').replace('ű', 'u')
        // 2. Minden MÁS ékezet (idegen nyelvű címeknél) a szabványos úton.
        val stripped = Normalizer.normalize(hungarian, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        // 3. Ami nem betű vagy szám, az szóköz lesz.
        return stripped
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}