package com.superdl.launcher.youtube

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

object YoutubeHelper {

    fun search(
        query: String,
        onResult: (List<YoutubeVideo>) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val videos = YoutubeExtractor.search(query)
                Handler(Looper.getMainLooper()).post {
                    if (videos.isEmpty()) onError("Nincs találat: $query")
                    else onResult(videos)
                }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onError("YouTube keresés sikertelen. Ellenőrizd az internetkapcsolatot.")
                }
            }
        }.start()
    }

    fun playVideo(context: Context, video: YoutubeVideo): Boolean {
        val intent = Intent(context, YoutubePlayerActivity::class.java).apply {
            putExtra(YoutubePlayerActivity.EXTRA_VIDEO_ID, video.videoId)
            putExtra(YoutubePlayerActivity.EXTRA_TITLE, video.title)
            putExtra(YoutubePlayerActivity.EXTRA_CHANNEL, video.channel)
            if (context !is android.app.Activity) {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        context.startActivity(intent)
        return true
    }
}