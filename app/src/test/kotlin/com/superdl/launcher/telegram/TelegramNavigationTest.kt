package com.superdl.launcher.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramNavigationTest {
    @Test fun incomingReorderKeepsConversationFocus() {
        val navigation = TelegramNavigation()
        navigation.updateChats(listOf(TelegramChat(1, "Anna", 0), TelegramChat(2, "Béla", 1)))
        navigation.move(1)
        navigation.updateChats(listOf(TelegramChat(2, "Béla", 2), TelegramChat(1, "Anna", 0)))
        assertTrue(navigation.speakCurrent().startsWith("Béla"))
        assertEquals(2L, navigation.openFocusedChat())
    }

    @Test fun messageUpdateDoesNotMoveReadingFocus() {
        val navigation = TelegramNavigation()
        navigation.updateChats(listOf(TelegramChat(7, "Csapat", 0)))
        navigation.openFocusedChat()
        navigation.updateMessages(7, listOf(TelegramMessage(11, 7, "Éva", "Első", false)))
        assertTrue(navigation.isAtLatestMessage())
        navigation.updateMessages(7, listOf(
            TelegramMessage(11, 7, "Éva", "Első", false),
            TelegramMessage(12, 7, "Péter", "Második", false)
        ))
        assertTrue(navigation.speakCurrent().contains("Első"))
    }

    @Test fun newMessagesFollowLatestButPreserveOlderReadingPosition() {
        val navigation = TelegramNavigation()
        navigation.updateChats(listOf(TelegramChat(7, "Csapat", 0)))
        navigation.openFocusedChat()
        navigation.updateMessages(7, listOf(
            TelegramMessage(11, 7, "Éva", "Első", false),
            TelegramMessage(12, 7, "Péter", "Második", false)
        ))
        assertTrue(navigation.speakCurrent().contains("Második"))
        assertTrue(navigation.appendMessage(TelegramMessage(13, 7, "Éva", "Harmadik", false)))
        assertTrue(navigation.speakCurrent().contains("Harmadik"))
        navigation.move(-1)
        assertTrue(navigation.appendMessage(TelegramMessage(14, 7, "Éva", "Negyedik", false)))
        assertTrue(navigation.speakCurrent().contains("Második"))
        assertFalse(navigation.appendMessage(TelegramMessage(15, 8, "Más", "Másik chat", false)))
    }

    @Test fun sendRequiresReviewAndCannotRepeatOnDoubleActivation() {
        val navigation = TelegramNavigation()
        navigation.updateChats(listOf(TelegramChat(5, "Dóra", 0)))
        assertNull(navigation.confirmSend())
        navigation.openFocusedChat()
        assertFalse(navigation.reviewSend("  "))
        assertTrue(navigation.reviewSend(" Szia "))
        assertTrue(navigation.speakCurrent().contains("Címzett: Dóra"))
        assertEquals(TelegramSendRequest(5, "Szia"), navigation.confirmSend())
        assertNull(navigation.confirmSend())
    }

    @Test fun goingBackFromReviewNeverSends() {
        val navigation = TelegramNavigation()
        navigation.updateChats(listOf(TelegramChat(5, "Dóra", 0)))
        navigation.openFocusedChat()
        navigation.reviewSend("Titok")
        navigation.back()
        assertNull(navigation.confirmSend())
    }
}
