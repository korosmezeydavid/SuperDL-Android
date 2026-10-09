package com.superdl.launcher.search

import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** A hangvezérelt Super Surf strukturált adatforrásai. Hálózati szálon hívandó. */
object SuperSurfHelper {
    data class Reading(val title: String, val body: String, val source: String, val url: String)
    data class Conversion(val amount: Double, val from: String, val to: String)

    private const val WIKI_API = "https://hu.wikipedia.org/w/api.php"
    private const val DICTIONARY_API = "https://hu.wiktionary.org/w/api.php"

    fun wikipedia(term: String): Reading? {
        val found = JSONObject(get("$WIKI_API?action=query&list=search&srsearch=${encode(term)}&srlimit=1&format=json"))
            .optJSONObject("query")?.optJSONArray("search") ?: return null
        val title = found.optJSONObject(0)?.optString("title")?.takeIf { it.isNotBlank() } ?: return null
        val pages = JSONObject(get("$WIKI_API?action=query&prop=extracts&explaintext=1&redirects=1&titles=${encode(title)}&format=json"))
            .optJSONObject("query")?.optJSONObject("pages") ?: return null
        val page = pages.optJSONObject(pages.keys().asSequence().firstOrNull() ?: return null) ?: return null
        val body = page.optString("extract").trim().takeIf { it.isNotBlank() } ?: return null
        return Reading(title, body, "Wikipédia", "https://hu.wikipedia.org/wiki/${encode(title)}")
    }

    fun dictionary(term: String): Reading? {
        val word = term.trim().substringBefore(' ').takeIf { it.isNotBlank() } ?: return null
        val response = JSONObject(get("$DICTIONARY_API?action=parse&page=${encode(word)}&prop=text&format=json&formatversion=2"))
        val parsed = response.optJSONObject("parse") ?: return null
        val html = parsed.optString("text")
        if (html.isBlank()) return null
        // Az API csak a szócikk törzsét adja vissza; a szerkesztési vezérlőket és
        // hivatkozási lábjegyzeteket eltávolítjuk, a címsorokat megőrizzük.
        val clean = html
            .replace(Regex("(?is)<(script|style|table|sup)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?is)<span[^>]*class=\"mw-editsection[^\"]*\"[^>]*>.*?</span>"), " ")
            .replace(Regex("(?i)</?(h[1-6]|p|li|ul|ol|dl|dt|dd|br)[^>]*>"), "\n")
        val body = Html.fromHtml(clean, Html.FROM_HTML_MODE_LEGACY).toString()
            .lines().map { it.trim() }.filter { it.isNotBlank() }
            .joinToString("\n").take(60_000).takeIf { it.isNotBlank() } ?: return null
        return Reading(word, body, "Wikiszótár", "https://hu.wiktionary.org/wiki/${encode(word)}")
    }

    fun conversionFromSpeech(text: String): Conversion? {
        val words = text.lowercase(Locale.ROOT).replace(',', '.').trim()
        val amount = Regex("\\d+(?:\\.\\d+)?").find(words)?.value?.toDoubleOrNull() ?: 1.0
        if (!amount.isFinite() || amount <= 0 || amount > 1_000_000_000) return null
        val currencies = mapOf(
            "eur" to "EUR", "euró" to "EUR", "euro" to "EUR",
            "huf" to "HUF", "forint" to "HUF",
            "usd" to "USD", "dollár" to "USD", "dollar" to "USD",
            "gbp" to "GBP", "font" to "GBP", "angol font" to "GBP",
            "chf" to "CHF", "frank" to "CHF", "svájci frank" to "CHF",
            "pln" to "PLN", "zloty" to "PLN", "złoty" to "PLN"
        )
        val endings = listOf("ban", "ben", "hoz", "hez", "höz", "ból", "ből", "nak", "nek", "ba", "be", "ra", "re", "ot", "et", "at", "t")
        val matched = Regex("[\\p{L}]+") .findAll(words).mapNotNull { match ->
            currencies[match.value] ?: endings.firstNotNullOfOrNull { suffix ->
                if (match.value.endsWith(suffix)) currencies[match.value.removeSuffix(suffix)] else null
            }
        }.toList()
        val from = matched.firstOrNull() ?: "EUR"
        val to = matched.drop(1).firstOrNull { it != from } ?: if (from == "HUF") "EUR" else "HUF"
        return Conversion(amount, from, to)
    }

    fun belongsToSite(url: String, domain: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https" && uri.scheme != "http") return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host == domain || host.endsWith(".$domain")
    }

    fun exchange(query: String): Reading? {
        val request = conversionFromSpeech(query) ?: return null
        val response = JSONObject(get("https://api.frankfurter.app/latest?from=${request.from}&to=${request.to}"))
        val rate = response.optJSONObject("rates")?.optDouble(request.to) ?: return null
        if (!rate.isFinite() || rate <= 0) return null
        val result = request.amount * rate
        val body = "${request.amount.formatAmount()} ${request.from} = ${result.formatAmount()} ${request.to}. " +
            "1 ${request.from} = ${rate.formatAmount()} ${request.to}. " +
            "Az árfolyam dátuma: ${response.optString("date")}. Tájékoztató középárfolyam; a bank vagy pénzváltó eltérő árfolyamot alkalmazhat."
        return Reading("Árfolyam: ${request.from} – ${request.to}", body, "Frankfurter", "https://frankfurter.app")
    }

    fun sourceArticle(url: String): String? {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" && uri.scheme != "http") return null
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SuperDL Android research (accessible reader)")
            setRequestProperty("Accept", "text/html")
        }
        return try {
            if (connection.responseCode !in 200..299 ||
                !connection.contentType.orEmpty().contains("text/html", ignoreCase = true)) return null
            val html = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText().take(2_000_000) }
            val withoutNoise = html
                .replace(Regex("(?is)<(script|style|nav|header|footer|aside|form|iframe)[^>]*>.*?</\\1>"), " ")
            val article = Regex("(?is)<article\\b[^>]*>(.*?)</article>").find(withoutNoise)?.groupValues?.get(1)
                ?: Regex("(?is)<main\\b[^>]*>(.*?)</main>").find(withoutNoise)?.groupValues?.get(1)
                ?: withoutNoise
            val readable = article
                .replace(Regex("(?i)</?(h[1-6]|p|li|br|blockquote)[^>]*>"), "\n")
            Html.fromHtml(readable, Html.FROM_HTML_MODE_LEGACY).toString()
                .lines().map { it.trim() }.filter { it.isNotBlank() }
                .joinToString("\n\n").take(100_000).takeIf { it.length >= 80 }
        } finally {
            connection.disconnect()
        }
    }

    private fun Double.formatAmount(): String = String.format(Locale.forLanguageTag("hu-HU"), "%,.2f", this)
    private fun encode(text: String): String = URLEncoder.encode(text.trim(), StandardCharsets.UTF_8.name())

    private fun get(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "SuperDL Android research (contact: korosmezey.david.richard@gmail.com)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
