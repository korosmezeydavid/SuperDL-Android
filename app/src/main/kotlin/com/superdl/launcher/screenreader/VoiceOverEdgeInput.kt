package com.superdl.launcher.screenreader

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.os.Handler
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.RequiresApi

/**
 * Receives raw input in two narrow overlay strips at the usable screen edges.
 * Android retains the portions occupied by its system bars.
 */
@RequiresApi(30)
internal class VoiceOverEdgeInput(
    private val service: AccessibilityService,
    private val gestureHandler: Handler,
    private val onRegionChanged: (Region) -> Unit,
    private val isAllowed: () -> Boolean,
    private val onTone: (Int) -> Boolean,
    private val onAction: (bottom: Boolean, VoiceOverEdgeGesture.Action) -> Unit
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val strips = mutableListOf<EdgeStrip>()
    private var keyboardVisible = false

    fun setKeyboardVisible(visible: Boolean) {
        if (keyboardVisible == visible) return
        keyboardVisible = visible
        strips.filter { it.bottom }.forEach {
            if (visible) it.cancelGesture()
            it.visibility = if (visible) View.GONE else View.VISIBLE
        }
        updateRegion()
    }

    fun attach() {
        val manager = windowManager ?: return
        if (strips.isNotEmpty()) return
        try {
            val density = service.resources.displayMetrics.density
            val insets = manager.maximumWindowMetrics.windowInsets
            val statusTop = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            val cutoutTop = insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout()).top
            val landscape = service.resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val minimumStatusTop = ((if (landscape) 36 else 24) * density).toInt()
            // Some landscape layouts reserve the notch at the side but still place the
            // status bar along the top. The reported top inset can then be zero.
            val topOffset = (maxOf(statusTop, minimumStatusTop) - cutoutTop).coerceAtLeast(0) +
                (2 * density).toInt()
            for (bottom in listOf(false, true)) {
                val height = ((if (bottom) 18 else 32) * density).toInt().coerceAtLeast(1)
                val strip = EdgeStrip(bottom)
                if (bottom && keyboardVisible) strip.visibility = View.GONE
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    height,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = if (bottom) Gravity.BOTTOM else Gravity.TOP
                    val fitTypes = WindowInsets.Type.navigationBars() or WindowInsets.Type.captionBar() or
                        WindowInsets.Type.displayCutout()
                    setFitInsetsTypes(if (bottom) fitTypes or WindowInsets.Type.statusBars() else fitTypes)
                    if (!bottom) y = topOffset
                    setTitle(if (bottom) "Super DL bottom edge" else "Super DL top edge")
                }
                strips += strip
                strip.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    strip.post { if (strip in strips) updateRegion() }
                }
                manager.addView(strip, params)
            }
        } catch (error: RuntimeException) {
            detach()
            android.util.Log.w(ScreenReaderPrefs.TAG, "Az élgesztus sávjai nem érhetők el.", error)
        }
    }

    fun detach() {
        if (strips.isEmpty()) return
        val attached = strips.toList()
        strips.clear()
        onRegionChanged(Region())
        for (strip in attached) {
            strip.cancelGesture()
            if (strip.parent != null) {
                runCatching { windowManager?.removeViewImmediate(strip) }
                    .onFailure { android.util.Log.w(ScreenReaderPrefs.TAG, "Az élsáv leválasztása nem sikerült.", it) }
            }
        }
    }

    private fun updateRegion() {
        val region = Region()
        val position = IntArray(2)
        for (strip in strips) {
            if (!strip.isAttachedToWindow || strip.visibility != View.VISIBLE ||
                strip.width == 0 || strip.height == 0
            ) continue
            strip.getLocationOnScreen(position)
            region.union(Rect(position[0], position[1], position[0] + strip.width, position[1] + strip.height))
        }
        onRegionChanged(region)
    }

    private inner class EdgeStrip(val bottom: Boolean) : View(service) {
        private val recognizer = VoiceOverEdgeGesture()
        private var startX = 0f
        private var startY = 0f
        private var nextToneAt = 0L
        private val playTone = Runnable { playPendingTone() }

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isAllowed()) {
                cancelGesture()
                return false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelGesture()
                    startX = event.rawX
                    startY = event.rawY
                    recognizer.begin()
                }
                MotionEvent.ACTION_MOVE -> {
                    val density = resources.displayMetrics.density
                    val dy = (event.rawY - startY) / density
                    recognizer.move(if (bottom) -dy else dy, (event.rawX - startX) / density)
                    if (!gestureHandler.hasCallbacks(playTone)) playPendingTone()
                }
                MotionEvent.ACTION_UP -> {
                    gestureHandler.removeCallbacks(playTone)
                    val density = resources.displayMetrics.density
                    val dy = (event.rawY - startY) / density
                    recognizer.move(if (bottom) -dy else dy, (event.rawX - startX) / density)
                    recognizer.finish()?.let { onAction(bottom, it) }
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> cancelGesture()
            }
            return true
        }

        private fun playPendingTone() {
            if (!isAllowed()) {
                cancelGesture()
                return
            }
            val now = android.os.SystemClock.uptimeMillis()
            if (now < nextToneAt) {
                gestureHandler.postAtTime(playTone, nextToneAt)
                return
            }
            val tone = recognizer.nextTone() ?: return
            if (!onTone(tone)) {
                cancelGesture()
                return
            }
            nextToneAt = now + 48L
            gestureHandler.postAtTime(playTone, nextToneAt)
        }

        fun cancelGesture() {
            gestureHandler.removeCallbacks(playTone)
            recognizer.cancel()
            nextToneAt = 0L
        }
    }
}
