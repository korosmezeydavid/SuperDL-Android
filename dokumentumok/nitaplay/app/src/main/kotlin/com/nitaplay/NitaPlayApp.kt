package com.nitaplay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.nitaplay.data.AppPreferences
import com.nitaplay.data.FavoritesStore
import com.nitaplay.data.MusicRepository
import com.nitaplay.data.PlaylistStore
import com.nitaplay.player.PlayerController

class NitaPlayApp : Application() {
    lateinit var prefs: AppPreferences
        private set
    lateinit var musicRepository: MusicRepository
        private set
    lateinit var favoritesStore: FavoritesStore
        private set
    lateinit var playlistStore: PlaylistStore
        private set
    lateinit var playerController: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = AppPreferences(this)
        musicRepository = MusicRepository(this)
        favoritesStore = FavoritesStore(this)
        playlistStore = PlaylistStore(this)
        playerController = PlayerController(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_playing)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "nitaplay_playback"
        lateinit var instance: NitaPlayApp
            private set
    }
}
