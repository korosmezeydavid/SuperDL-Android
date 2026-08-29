package com.nitaplay.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(): List<Playlist> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val ids = o.optJSONArray("trackIds") ?: JSONArray()
                Playlist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    trackIds = (0 until ids.length()).map { ids.getLong(it) }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun create(name: String): Playlist {
        val list = getAll().toMutableList()
        val p = Playlist(id = UUID.randomUUID().toString(), name = name.trim().ifBlank { "Lista" })
        list.add(p)
        save(list)
        return p
    }

    fun delete(id: String) {
        save(getAll().filter { it.id != id })
    }

    fun addTrack(playlistId: String, trackId: Long) {
        val list = getAll().map { p ->
            if (p.id == playlistId && trackId !in p.trackIds) {
                p.copy(trackIds = p.trackIds + trackId)
            } else p
        }
        save(list)
    }

    fun removeTrack(playlistId: String, trackId: Long) {
        val list = getAll().map { p ->
            if (p.id == playlistId) p.copy(trackIds = p.trackIds.filter { it != trackId })
            else p
        }
        save(list)
    }

    private fun save(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            val ids = JSONArray()
            p.trackIds.forEach { ids.put(it) }
            o.put("trackIds", ids)
            arr.put(o)
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "nitaplay_playlists"
        private const val KEY_LIST = "list"
    }
}
