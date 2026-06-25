package com.superdl.launcher.util

import android.app.Activity
import android.os.Handler
import android.os.Looper

inline fun Activity.postWhenAlive(crossinline block: () -> Unit) {
    if (isFinishing || isDestroyed) return
    Handler(Looper.getMainLooper()).post {
        if (isFinishing || isDestroyed) return@post
        block()
    }
}

fun Activity.cancelUiCallbacks(handler: Handler) {
    handler.removeCallbacksAndMessages(null)
}