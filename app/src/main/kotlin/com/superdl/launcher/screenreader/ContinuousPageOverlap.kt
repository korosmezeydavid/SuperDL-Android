package com.superdl.launcher.screenreader

internal object ContinuousPageOverlap {
    fun firstUnread(previous: List<String>, current: List<String>): Int? {
        if (current.isEmpty() || previous == current) return null
        val overlap = (minOf(previous.size, current.size) downTo 1).firstOrNull { length ->
            previous.takeLast(length) == current.take(length)
        } ?: 0
        return overlap.takeIf { it < current.size }
    }
}