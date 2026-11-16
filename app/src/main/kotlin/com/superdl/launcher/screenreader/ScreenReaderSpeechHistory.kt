package com.superdl.launcher.screenreader

internal class ScreenReaderSpeechHistory(private val limit: Int) {
    private val entries = ArrayDeque<String>()

    init {
        require(limit > 0)
    }

    fun add(text: String) {
        val entry = text.trim()
        if (entry.isEmpty() || entries.lastOrNull() == entry) return
        if (entries.size == limit) entries.removeFirst()
        entries.addLast(entry)
    }

    fun newestFirst(): List<String> = entries.reversed()

    fun clear() = entries.clear()
}