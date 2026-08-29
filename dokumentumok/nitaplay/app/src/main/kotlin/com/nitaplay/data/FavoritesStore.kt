package com.nitaplay.data

import android.content.Context

class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getIds(): Set<Long> {
        val raw = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun isFavorite(id: Long): Boolean = getIds().contains(id)

    fun toggle(id: Long): Boolean {
        val set = getIds().toMutableSet()
        val nowFavorite = if (set.contains(id)) {
            set.remove(id)
            false
        } else {
            set.add(id)
            true
        }
        prefs.edit().putStringSet(KEY_IDS, set.map { it.toString() }.toSet()).apply()
        return nowFavorite
    }

    fun add(id: Long) {
        val set = getIds().toMutableSet()
        set.add(id)
        prefs.edit().putStringSet(KEY_IDS, set.map { it.toString() }.toSet()).apply()
    }

    companion object {
        private const val PREFS = "nitaplay_favorites"
        private const val KEY_IDS = "ids"
    }
}
