package com.superdl.launcher.locationwatch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object LocationProfileStore {

    private const val PREFS = "superdl"
    private const val KEY = "location_profiles"
    private const val MAX_PROFILES = 50

    fun getAll(context: Context): List<LocationProfile> =
        loadRaw(context).sortedByDescending { it.createdAt }

    fun getById(context: Context, id: String): LocationProfile? =
        loadRaw(context).firstOrNull { it.id == id }

    fun add(context: Context, profile: LocationProfile): LocationProfile? {
        val trimmedName = profile.name.trim()
        if (trimmedName.isBlank()) return null
        val current = loadRaw(context).toMutableList()
        if (current.any { it.id == profile.id }) return null
        while (current.size >= MAX_PROFILES) {
            val oldest = current.minByOrNull { it.createdAt } ?: break
            current.remove(oldest)
            deleteThumbnail(oldest.thumbnailPath)
        }
        val entry = profile.copy(name = trimmedName)
        current.add(entry)
        save(context, current)
        return entry
    }

    fun remove(context: Context, id: String): Boolean {
        val current = loadRaw(context)
        val removed = current.firstOrNull { it.id == id } ?: return false
        val updated = current.filterNot { it.id == id }
        save(context, updated)
        deleteThumbnail(removed.thumbnailPath)
        return true
    }

    fun buildProfileFromOcr(name: String, ocrText: String): LocationProfile? {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return null
        val tokens = LocationMatcher.tokenize(ocrText)
        if (tokens.size < 2) return null
        return LocationProfile(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            createdAt = System.currentTimeMillis(),
            ocrTokens = tokens,
            ocrFingerprint = LocationMatcher.buildFingerprint(tokens)
        )
    }

    private fun deleteThumbnail(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = java.io.File(path)
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        }
    }

    private fun loadRaw(context: Context): List<LocationProfile> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val name = item.optString("name").trim()
                    if (name.isBlank()) continue
                    val tokensArray = item.optJSONArray("ocrTokens") ?: JSONArray()
                    val tokens = buildSet {
                        for (j in 0 until tokensArray.length()) {
                            val token = tokensArray.optString(j).trim()
                            if (token.length >= 2) add(token)
                        }
                    }
                    if (tokens.isEmpty()) continue
                    add(
                        LocationProfile(
                            id = item.optString("id", UUID.randomUUID().toString()),
                            name = name,
                            createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                            ocrTokens = tokens,
                            ocrFingerprint = item.optString(
                                "ocrFingerprint",
                                LocationMatcher.buildFingerprint(tokens)
                            ),
                            thumbnailPath = item.optString("thumbnailPath").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, profiles: List<LocationProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            val tokensArray = JSONArray()
            profile.ocrTokens.sorted().forEach { tokensArray.put(it) }
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("createdAt", profile.createdAt)
                    .put("ocrTokens", tokensArray)
                    .put("ocrFingerprint", profile.ocrFingerprint)
                    .put("thumbnailPath", profile.thumbnailPath.orEmpty())
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}