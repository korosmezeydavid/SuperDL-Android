package com.superdl.launcher.lock.keyguard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object KeyguardPinSettings {

    private const val PREFS = "keyguard_pin_assist"
    private const val KEY_ENABLED = "feature_enabled"
    private const val SERVICE_ID_SUFFIX = "/.lock.keyguard.KeyguardPinAccessibilityService"

    fun isFeatureEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setFeatureEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun isServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (!manager.isEnabled) return false
        val targetId = context.packageName + SERVICE_ID_SUFFIX
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id == targetId }
    }

    fun isFullyActive(context: Context): Boolean =
        isFeatureEnabled(context) && isServiceEnabled(context)

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            if (context !is android.app.Activity) {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        context.startActivity(intent)
    }

    fun speakStatus(context: Context): String {
        val feature = if (isFeatureEnabled(context)) "bekapcsolva" else "kikapcsolva"
        val service = if (isServiceEnabled(context)) {
            "aktív a Kisegítő lehetőségekben"
        } else {
            "nincs engedélyezve a Kisegítő lehetőségekben"
        }
        return "Rendszer PIN segéd: $feature, szolgáltatás $service. " +
            "A rendszer zárolási PIN képernyőjén függőleges számbillentyűzettel oldhatod fel a telefont."
    }
}