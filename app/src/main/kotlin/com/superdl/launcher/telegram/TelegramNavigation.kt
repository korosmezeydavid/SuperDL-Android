package com.superdl.launcher.telegram

/** UI state only. Telegram account data is supplied by a future TDLib adapter. */
data class TelegramChat(val id: Long, val title: String, val unreadCount: Int)

data class TelegramMessage(
    val id: Long,
    val chatId: Long,
    val sender: String,
    val text: String,
    val outgoing: Boolean,
    val attachment: TelegramAttachment? = null,
    val botButtons: List<TelegramBotButton> = emptyList()
)

data class TelegramBotButton(val label: String, val kind: Kind, val data: String = "") {
    enum class Kind { CALLBACK, TEXT, UNSUPPORTED }
}

data class TelegramAttachment(val fileId: Int, val name: String, val size: Long, val voiceNote: Boolean)

data class TelegramSendRequest(val chatId: Long, val text: String)

sealed interface TelegramPage {
    data object Chats : TelegramPage
    data class Messages(val chatId: Long) : TelegramPage
    data class ReviewSend(val chatId: Long, val text: String) : TelegramPage
}

/** Keeps gesture focus stable when asynchronous Telegram updates reorder lists. */
class TelegramNavigation {
    var page: TelegramPage = TelegramPage.Chats
        private set

    private var chats = emptyList<TelegramChat>()
    private var messages = emptyList<TelegramMessage>()
    private var chatIndex = 0
    private var messageIndex = 0

    fun updateChats(items: List<TelegramChat>) {
        val focusedId = chats.getOrNull(chatIndex)?.id
        chats = items.distinctBy { it.id }
        chatIndex = focusedId?.let { id -> chats.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 } ?: chatIndex.coerceAtMost((chats.size - 1).coerceAtLeast(0))
    }

    fun updateMessages(chatId: Long, items: List<TelegramMessage>) {
        if ((page as? TelegramPage.Messages)?.chatId != chatId) return
        val focusedId = messages.getOrNull(messageIndex)?.id
        val history = items.filter { it.chatId == chatId }.distinctBy { it.id }
        val newestHistoryId = history.maxOfOrNull { it.id } ?: 0L
        val liveAfterHistory = messages.filter { it.id > newestHistoryId }
        messages = (history + liveAfterHistory).distinctBy { it.id }.sortedBy { it.id }
        messageIndex = focusedId?.let { id -> messages.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 } ?: if (focusedId == null) (messages.size - 1).coerceAtLeast(0)
                else messageIndex.coerceAtMost((messages.size - 1).coerceAtLeast(0))
    }

    fun isAtLatestMessage(): Boolean = page is TelegramPage.Messages &&
        messages.isNotEmpty() && messageIndex == messages.lastIndex

    /** Insert a live update without replacing the history or moving an older reading position. */
    fun appendMessage(message: TelegramMessage): Boolean {
        if ((page as? TelegramPage.Messages)?.chatId != message.chatId) return false
        val followLatest = messages.isEmpty() || messageIndex == messages.lastIndex
        val focusedId = messages.getOrNull(messageIndex)?.id
        messages = (messages.filterNot { it.id == message.id } + message).sortedBy { it.id }
        messageIndex = if (followLatest) messages.lastIndex
            else messages.indexOfFirst { it.id == focusedId }.takeIf { it >= 0 } ?: 0
        return true
    }

    fun move(delta: Int): String {
        when (page) {
            TelegramPage.Chats -> {
                if (chats.isEmpty()) return "Nincs beszélgetés."
                chatIndex = (chatIndex + delta).mod(chats.size)
            }
            is TelegramPage.Messages -> {
                if (messages.isEmpty()) return "Nincs üzenet ebben a beszélgetésben."
                val next = (messageIndex + delta).coerceIn(0, messages.lastIndex)
                if (next == messageIndex) return (if (delta > 0) "Ez a legfrissebb üzenet. "
                    else "Ez a legrégebbi betöltött üzenet. ") + speakCurrent()
                messageIndex = next
            }
            is TelegramPage.ReviewSend -> return speakCurrent()
        }
        return speakCurrent()
    }

    fun openFocusedChat(): Long? {
        if (page != TelegramPage.Chats) return null
        val chat = chats.getOrNull(chatIndex) ?: return null
        page = TelegramPage.Messages(chat.id)
        messages = emptyList()
        messageIndex = 0
        return chat.id
    }

    fun reviewSend(text: String): Boolean {
        val chatId = (page as? TelegramPage.Messages)?.chatId ?: return false
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        page = TelegramPage.ReviewSend(chatId, trimmed)
        return true
    }

    fun focusedMessage(): TelegramMessage? =
        if (page is TelegramPage.Messages) messages.getOrNull(messageIndex) else null

    /** Only an explicit second activation produces a send request. */
    fun confirmSend(): TelegramSendRequest? {
        val review = page as? TelegramPage.ReviewSend ?: return null
        page = TelegramPage.Messages(review.chatId)
        return TelegramSendRequest(review.chatId, review.text)
    }

    fun back() {
        page = when (val current = page) {
            TelegramPage.Chats -> TelegramPage.Chats
            is TelegramPage.Messages -> TelegramPage.Chats
            is TelegramPage.ReviewSend -> TelegramPage.Messages(current.chatId)
        }
    }

    fun speakCurrent(): String = when (val current = page) {
        TelegramPage.Chats -> chats.getOrNull(chatIndex)?.let { chat ->
            "${chat.title}. ${chat.unreadCount} olvasatlan üzenet. ${chatIndex + 1} / ${chats.size}."
        } ?: "Nincs beszélgetés."
        is TelegramPage.Messages -> messages.getOrNull(messageIndex)?.let { message ->
            val author = if (message.outgoing) "Te" else message.sender
            "$author: ${message.text}. ${messageIndex + 1} / ${messages.size}."
        } ?: "Nincs üzenet ebben a beszélgetésben."
        is TelegramPage.ReviewSend -> {
            val recipient = chats.firstOrNull { it.id == current.chatId }?.title ?: "ismeretlen címzett"
            "Küldés ellenőrzése. Címzett: $recipient. Üzenet: ${current.text}. " +
                "Jobbra küldés, balra vissza szerkesztéshez."
        }
    }
}
