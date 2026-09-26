package com.superdl.launcher.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.superdl.launcher.patrol.PatrolAnnouncer
import com.superdl.launcher.patrol.PatrolNotificationClassifier
import com.superdl.launcher.patrol.PatrolStore
import com.superdl.launcher.system.QuietModeHelper

class SuperNotificationListener : NotificationListenerService() {

    /** Az már látott értesítés-kulcsok (a „csak egyszer szóljon" frissítésekhez). */
    private val seenKeys = LinkedHashSet<String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeNotifications?.forEach {
            rememberKey(it.key)
            postNotification(it)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // A "már láttuk" döntés a tárolás ELŐTT kell.
        val alreadySeen = !rememberKey(sbn.key)
        postNotification(sbn)
        if (shouldAnnounce(sbn, alreadySeen)) {
            maybeAnnounceNotification(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationStore.remove(sbn.key)
        seenKeys.remove(sbn.key)
    }

    /** @return true, ha a kulcs új volt. */
    private fun rememberKey(key: String): Boolean {
        val added = seenKeys.add(key)
        while (seenKeys.size > MAX_SEEN_KEYS) {
            seenKeys.remove(seenKeys.first())
        }
        return added
    }

    // MIÉRT: a saját értesítéseinket (pl. bejövő hívás), a csoport-összesítőket,
    // a folyamatban lévő (zene, letöltés, navigáció) értesítéseket és a
    // "csak egyszer szóljon" frissítéseket is bemondtuk — újra és újra.
    private fun shouldAnnounce(sbn: StatusBarNotification, alreadySeen: Boolean): Boolean {
        if (sbn.packageName == packageName) return false
        val notification = sbn.notification ?: return false
        if ((notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return false
        if (sbn.isOngoing && notification.category != android.app.Notification.CATEGORY_CALL) return false
        if (alreadySeen && (notification.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE) != 0) {
            return false
        }
        return true
    }

    private fun postNotification(sbn: StatusBarNotification) {
        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (_: Exception) {
            sbn.packageName
        }
        NotificationStore.add(sbn, label)
    }

    private fun isEmailNotification(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName.lowercase()
        if (pkg.contains("mail") || pkg.contains("gmail") || pkg.contains("outlook") ||
            pkg.contains("yahoo") || pkg.contains("email")
        ) {
            return true
        }
        val category = sbn.notification?.category
        return category == android.app.Notification.CATEGORY_EMAIL
    }

    private fun maybeAnnounceNotification(sbn: StatusBarNotification) {
        if (!PatrolStore.isMasterEnabled(this)) return
        if (QuietModeHelper.shouldSuppressNotificationAnnouncements(this)) return
        if (PatrolStore.isQuietNow(this)) return

        val kind = PatrolNotificationClassifier.classify(sbn)
        val enabled = when (kind) {
            PatrolNotificationClassifier.Kind.CALL -> PatrolStore.isCallAlertEnabled(this)
            PatrolNotificationClassifier.Kind.SMS -> PatrolStore.isSmsAlertEnabled(this)
            PatrolNotificationClassifier.Kind.OTHER -> PatrolStore.isNotificationAlertEnabled(this)
        }
        if (!enabled) return

        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (_: Exception) {
            sbn.packageName
        }
        val message = PatrolNotificationClassifier.speakMessage(kind, label, title, text)
        val soundCategory = when (kind) {
            PatrolNotificationClassifier.Kind.CALL ->
                com.superdl.launcher.feedback.AlertSoundCategory.ALARM_CLOCK
            PatrolNotificationClassifier.Kind.SMS ->
                com.superdl.launcher.feedback.AlertSoundCategory.SMS
            PatrolNotificationClassifier.Kind.OTHER ->
                if (isEmailNotification(sbn)) {
                    com.superdl.launcher.feedback.AlertSoundCategory.EMAIL
                } else {
                    com.superdl.launcher.feedback.AlertSoundCategory.GENERAL_NOTIFICATION
                }
        }
        PatrolAnnouncer.announce(this, message, soundCategory = soundCategory)
    }

    private companion object {
        const val MAX_SEEN_KEYS = 200
    }
}