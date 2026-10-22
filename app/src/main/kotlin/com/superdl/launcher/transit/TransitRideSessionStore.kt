package com.superdl.launcher.transit

import android.content.Context
import android.util.Base64
import android.util.Log

/** Private, synchronous checkpoint writes. Persist before any spoken one-time event. */
object TransitRideSessionStore {
    private const val TAG = "SuperDL.TransitRideStore"
    private const val PREFS = "transit_ride_session"
    private const val KEY_SESSION = "selected_trip_and_progress_v1"

    fun save(context: Context, session: TransitRideSession): Boolean = try {
        val bytes = TransitRideSessionCodec.encode(session)
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SESSION, encoded).commit()
    } catch (error: Exception) {
        Log.w(TAG, "Could not persist selected ride", error)
        false
    }

    fun load(context: Context): TransitRideSession? {
        val encoded = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SESSION, null) ?: return null
        return try {
            if (encoded.length > TransitRideSessionCodec.MAX_BYTES * 2) return null
            TransitRideSessionCodec.decode(Base64.decode(encoded, Base64.DEFAULT))
        } catch (error: Exception) {
            Log.w(TAG, "Could not read selected ride", error)
            null
        }
    }

    fun clear(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_SESSION).commit()
}
