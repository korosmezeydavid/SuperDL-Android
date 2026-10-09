package com.superdl.launcher.sound

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemRingtoneHelperTest {
    @Test fun notificationFolderRecognizesRootAndNestedFiles() {
        assertTrue(SystemRingtoneHelper.isNotificationPath("Notifications/custom.ogg"))
        assertTrue(SystemRingtoneHelper.isNotificationPath("Music/Notifications/tones/custom.mp3"))
        assertTrue(SystemRingtoneHelper.isNotificationPath("C:\\Phone\\Notifications\\own.wav"))
        assertFalse(SystemRingtoneHelper.isNotificationPath("Music/notification-samples/demo.mp3"))
        assertFalse(SystemRingtoneHelper.isNotificationPath("Ringtones/call.ogg"))
    }
}
