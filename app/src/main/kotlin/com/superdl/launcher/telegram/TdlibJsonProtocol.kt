package com.superdl.launcher.telegram

import org.json.JSONArray
import org.json.JSONObject

/**
 * A small, versioned boundary to TDLib's official JSON interface. No network or native library
 * is loaded here. Callers must serialize requests and process updates in their received order.
 * Never write returned JSON to the Android log: it can contain messages and login codes.
 */
object TdlibJsonProtocol {
    fun parameters(apiId: Int, apiHash: String, databaseDirectory: String,
                   filesDirectory: String, languageCode: String, systemVersion: String,
                   appVersion: String): String {
        require(apiId > 0)
        require(apiHash.matches(Regex("[0-9a-fA-F]{32}")))
        require(databaseDirectory.isNotBlank() && filesDirectory.isNotBlank())
        require(languageCode.isNotBlank() && systemVersion.isNotBlank() && appVersion.isNotBlank())
        return JSONObject()
            .put("@type", "setTdlibParameters")
            .put("use_test_dc", false)
            .put("database_directory", databaseDirectory)
            .put("files_directory", filesDirectory)
            .put("database_encryption_key", "")
            .put("use_file_database", true)
            .put("use_chat_info_database", true)
            .put("use_message_database", true)
            .put("use_secret_chats", false)
            .put("api_id", apiId)
            .put("api_hash", apiHash)
            .put("system_language_code", languageCode)
            .put("device_model", "SuperDL Android")
            .put("system_version", systemVersion)
            .put("application_version", appVersion)
            .toString()
    }

    fun phoneNumber(number: String): String {
        require(number.matches(Regex("\\+[1-9][0-9]{5,14}")))
        return JSONObject().put("@type", "setAuthenticationPhoneNumber")
            .put("phone_number", number)
            .put("settings", JSONObject.NULL).toString()
    }

    fun authenticationCode(code: String): String {
        require(code.isNotBlank())
        return JSONObject().put("@type", "checkAuthenticationCode")
            .put("code", code).toString()
    }

    fun authenticationEmailAddress(address: String): String {
        val normalized = address.trim()
        require(normalized.contains('@') && normalized.substringAfter('@').contains('.'))
        return JSONObject().put("@type", "setAuthenticationEmailAddress")
            .put("email_address", normalized).toString()
    }

    fun authenticationPassword(password: String): String {
        require(password.isNotEmpty())
        return JSONObject().put("@type", "checkAuthenticationPassword")
            .put("password", password).toString()
    }

    fun authenticationEmailCode(code: String): String {
        require(code.isNotBlank())
        return JSONObject().put("@type", "checkAuthenticationEmailCode")
            .put("code", JSONObject().put("@type", "emailAddressAuthenticationCode")
                .put("code", code)).toString()
    }

    fun logOut(): String = JSONObject().put("@type", "logOut").toString()

    fun loadChats(limit: Int = 30): String {
        require(limit in 1..100)
        return JSONObject()
            .put("@type", "loadChats")
            .put("chat_list", JSONObject.NULL)
            .put("limit", limit)
            .toString()
    }

    /** Snapshot after loadChats; subsequent updateNewChat/updateChatPosition events keep it fresh. */
    fun getChats(limit: Int = 30): String {
        require(limit in 1..100)
        return JSONObject()
            .put("@type", "getChats")
            .put("chat_list", JSONObject.NULL)
            .put("limit", limit)
            .toString()
    }

    fun getChat(chatId: Long): String {
        require(chatId != 0L)
        return JSONObject()
            .put("@type", "getChat")
            .put("chat_id", chatId)
            .toString()
    }

    fun chatHistory(chatId: Long, fromMessageId: Long = 0L, limit: Int = 30): String {
        require(chatId != 0L)
        require(fromMessageId >= 0L)
        require(limit in 1..100)
        return JSONObject()
            .put("@type", "getChatHistory")
            .put("chat_id", chatId)
            .put("from_message_id", fromMessageId)
            .put("offset", 0)
            .put("limit", limit)
            .put("only_local", false)
            .toString()
    }

    fun viewMessage(chatId: Long, messageId: Long): String {
        require(chatId != 0L && messageId != 0L)
        return JSONObject().put("@type", "viewMessages")
            .put("chat_id", chatId)
            .put("message_ids", JSONArray().put(messageId))
            .put("source", JSONObject.NULL)
            .put("force_read", true).toString()
    }

    fun newMessage(json: String): TelegramMessage? {
        val update = JSONObject(json)
        if (update.optString("@type") != "updateNewMessage") return null
        val message = update.optJSONObject("message") ?: return null
        val chatId = message.optLong("chat_id")
        if (chatId == 0L) return null
        val wrapper = JSONObject().put("@type", "messages")
            .put("messages", JSONArray().put(message))
        return messages(wrapper.toString(), chatId).firstOrNull()
    }

    /** The caller must pass a request produced by TelegramNavigation.confirmSend(). */
    fun sendText(confirmed: TelegramSendRequest, requestId: String): String {
        require(confirmed.chatId != 0L)
        require(confirmed.text.isNotBlank())
        require(requestId.isNotBlank())
        val formatted = JSONObject()
            .put("@type", "formattedText")
            .put("text", confirmed.text)
            .put("entities", JSONArray())
        val content = JSONObject()
            .put("@type", "inputMessageText")
            .put("text", formatted)
            .put("link_preview_options", JSONObject.NULL)
            .put("clear_draft", false)
        return JSONObject()
            .put("@type", "sendMessage")
            .put("@extra", requestId)
            .put("chat_id", confirmed.chatId)
            .put("topic_id", JSONObject.NULL)
            .put("reply_to", JSONObject.NULL)
            .put("options", JSONObject.NULL)
            .put("reply_markup", JSONObject.NULL)
            .put("input_message_content", content)
            .toString()
    }

    fun sendDocument(chatId: Long, path: String, requestId: String): String =
        sendLocalFile(chatId, path, requestId, false, 0)

    fun sendVoiceNote(chatId: Long, path: String, durationSeconds: Int, requestId: String): String =
        sendLocalFile(chatId, path, requestId, true, durationSeconds)

    private fun sendLocalFile(chatId: Long, path: String, requestId: String,
                              voiceNote: Boolean, durationSeconds: Int): String {
        require(chatId != 0L && path.isNotBlank() && requestId.isNotBlank())
        require(durationSeconds >= 0)
        val file = JSONObject().put("@type", "inputFileLocal").put("path", path)
        val content = if (voiceNote) JSONObject()
            .put("@type", "inputMessageVoiceNote")
            .put("voice_note", JSONObject()
                .put("@type", "inputVoiceNote")
                .put("voice_note", file)
                .put("duration", durationSeconds)
                .put("waveform", ""))
            .put("caption", JSONObject.NULL)
            .put("self_destruct_type", JSONObject.NULL)
        else JSONObject()
            .put("@type", "inputMessageDocument")
            .put("document", JSONObject()
                .put("@type", "inputDocument")
                .put("document", file)
                .put("thumbnail", JSONObject.NULL)
                .put("disable_content_type_detection", true))
            .put("caption", JSONObject.NULL)
        return JSONObject().put("@type", "sendMessage")
            .put("@extra", requestId).put("chat_id", chatId)
            .put("topic_id", JSONObject.NULL).put("reply_to", JSONObject.NULL)
            .put("options", JSONObject.NULL).put("reply_markup", JSONObject.NULL)
            .put("input_message_content", content).toString()
    }

    fun downloadFile(fileId: Int, requestId: String): String {
        require(fileId > 0 && requestId.isNotBlank())
        return JSONObject().put("@type", "downloadFile").put("@extra", requestId)
            .put("file_id", fileId).put("priority", 16).put("offset", 0)
            .put("limit", 0).put("synchronous", false).toString()
    }

    fun botCallback(chatId: Long, messageId: Long, base64Data: String, requestId: String): String {
        require(chatId != 0L && messageId != 0L && requestId.isNotBlank())
        require(base64Data.isNotBlank())
        return JSONObject().put("@type", "getCallbackQueryAnswer")
            .put("@extra", requestId).put("chat_id", chatId).put("message_id", messageId)
            .put("payload", JSONObject().put("@type", "callbackQueryPayloadData")
                .put("data", base64Data)).toString()
    }

    fun checkInviteLink(link: String, requestId: String): String {
        require(link.startsWith("https://t.me/") || link.startsWith("https://telegram.me/"))
        return JSONObject().put("@type", "checkChatInviteLink")
            .put("@extra", requestId).put("invite_link", link).toString()
    }

    fun joinByInviteLink(link: String, requestId: String): String {
        require(link.startsWith("https://t.me/") || link.startsWith("https://telegram.me/"))
        return JSONObject().put("@type", "joinChatByInviteLink")
            .put("@extra", requestId).put("invite_link", link).toString()
    }

    fun searchPublicChat(username: String, requestId: String): String {
        require(username.matches(Regex("[A-Za-z0-9_]{5,32}")) && requestId.isNotBlank())
        return JSONObject().put("@type", "searchPublicChat")
            .put("@extra", requestId).put("username", username).toString()
    }

    fun joinPublicChat(chatId: Long, requestId: String): String {
        require(chatId != 0L && requestId.isNotBlank())
        return JSONObject().put("@type", "joinChat")
            .put("@extra", requestId).put("chat_id", chatId).toString()
    }

    fun botButtons(message: JSONObject): List<TelegramBotButton> {
        val markup = message.optJSONObject("reply_markup") ?: return emptyList()
        val inline = markup.optString("@type") == "replyMarkupInlineKeyboard"
        if (!inline && markup.optString("@type") != "replyMarkupShowKeyboard") return emptyList()
        val rows = markup.optJSONArray("rows") ?: return emptyList()
        val buttons = mutableListOf<TelegramBotButton>()
        for (rowIndex in 0 until rows.length()) {
            val row = rows.optJSONArray(rowIndex) ?: continue
            for (column in 0 until row.length()) {
                val button = row.optJSONObject(column) ?: continue
                val label = button.optString("text").trim().take(100)
                if (label.isBlank()) continue
                val type = button.optJSONObject("type") ?: continue
                val kind = when (type.optString("@type")) {
                    "inlineKeyboardButtonTypeCallback" -> TelegramBotButton.Kind.CALLBACK
                    "keyboardButtonTypeText" -> TelegramBotButton.Kind.TEXT
                    else -> TelegramBotButton.Kind.UNSUPPORTED
                }
                buttons += TelegramBotButton(label, kind,
                    if (kind == TelegramBotButton.Kind.CALLBACK) type.optString("data") else "")
            }
        }
        return buttons
    }

    fun downloadedFile(json: String): Pair<Int, String>? {
        val objectValue = JSONObject(json)
        val file = when (objectValue.optString("@type")) {
            "file" -> objectValue
            "updateFile" -> objectValue.optJSONObject("file") ?: return null
            else -> return null
        }
        val local = file.optJSONObject("local") ?: return null
        if (!local.optBoolean("is_downloading_completed")) return null
        val path = local.optString("path")
        val id = file.optInt("id")
        return if (id > 0 && path.isNotBlank()) id to path else null
    }

    fun responseType(json: String): String = JSONObject(json).optString("@type")

    /** Only a live incoming update may trigger a user notification. Never exposes message text. */
    fun incomingMessageKey(json: String): String? {
        val update = JSONObject(json)
        if (update.optString("@type") != "updateNewMessage") return null
        val message = update.optJSONObject("message") ?: return null
        if (message.optBoolean("is_outgoing")) return null
        val id = message.optLong("id")
        val chatId = message.optLong("chat_id")
        if (id == 0L || chatId == 0L) return null
        return "$chatId:$id"
    }

    /** TDLib can return a failed request even after a send was accepted locally. */
    fun requestError(json: String): TdlibRequestError? {
        val objectValue = JSONObject(json)
        if (objectValue.optString("@type") != "error") return null
        return TdlibRequestError(
            requestId = objectValue.optString("@extra").ifBlank { null },
            code = objectValue.optInt("code"),
            message = objectValue.optString("message")
        )
    }

    fun authorizationState(json: String): String? {
        val objectValue = JSONObject(json)
        val type = objectValue.optString("@type")
        if (type.startsWith("authorizationState")) return type
        if (type != "updateAuthorizationState") return null
        return objectValue.optJSONObject("authorization_state")?.optString("@type")
    }

    /** Unknown states are deliberately not treated as a successful login. */
    fun loginStep(json: String): TelegramLoginStep? = when (authorizationState(json)) {
        null -> null
        "authorizationStateWaitTdlibParameters" -> TelegramLoginStep.Parameters
        "authorizationStateWaitPhoneNumber" -> TelegramLoginStep.PhoneNumber
        "authorizationStateWaitEmailAddress" -> TelegramLoginStep.EmailAddress
        "authorizationStateWaitCode" -> TelegramLoginStep.Code
        "authorizationStateWaitEmailCode" -> TelegramLoginStep.EmailCode
        "authorizationStateWaitPassword" -> TelegramLoginStep.Password
        "authorizationStateWaitOtherDeviceConfirmation" -> TelegramLoginStep.OtherDevice
        "authorizationStateWaitRegistration" -> TelegramLoginStep.Registration
        "authorizationStateWaitPremiumPurchase" -> TelegramLoginStep.PremiumPurchase
        "authorizationStateReady" -> TelegramLoginStep.Ready
        "authorizationStateLoggingOut", "authorizationStateClosing", "authorizationStateClosed" ->
            TelegramLoginStep.Closed
        else -> TelegramLoginStep.Unsupported
    }

    fun chatIds(json: String): List<Long> {
        val result = JSONObject(json)
        require(result.optString("@type") == "chats")
        val ids = result.getJSONArray("chat_ids")
        return (0 until ids.length()).map { ids.getLong(it) }
    }

    fun chat(json: String): TelegramChat {
        val result = JSONObject(json)
        require(result.optString("@type") == "chat")
        return TelegramChat(
            id = result.getLong("id"),
            title = result.optString("title").ifBlank { "Névtelen beszélgetés" },
            unreadCount = result.optInt("unread_count").coerceAtLeast(0)
        )
    }

    /** TDLib history is newest-first. Reverse it for gesture reading in chronological order. */
    fun messages(json: String, chatId: Long): List<TelegramMessage> {
        val result = JSONObject(json)
        require(result.optString("@type") == "messages")
        val items = result.getJSONArray("messages")
        return (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            require(item.getLong("chat_id") == chatId)
            val content = item.getJSONObject("content")
            val attachment = when (content.optString("@type")) {
                "messageDocument" -> content.optJSONObject("document")?.let { doc ->
                    val file = doc.optJSONObject("document")
                    val id = file?.optInt("id") ?: 0
                    if (id > 0) TelegramAttachment(id, doc.optString("file_name").ifBlank { "Fájl" },
                        file?.optLong("size") ?: 0, false) else null
                }
                "messageAudio" -> content.optJSONObject("audio")?.let { audio ->
                    val file = audio.optJSONObject("audio")
                    val id = file?.optInt("id") ?: 0
                    if (id > 0) TelegramAttachment(id,
                        audio.optString("file_name").ifBlank { "Hangfájl.mp3" },
                        file?.optLong("size") ?: 0, false) else null
                }
                "messageVideo" -> if (!content.optBoolean("is_secret"))
                    content.optJSONObject("video")?.let { video ->
                        val file = video.optJSONObject("video")
                        val id = file?.optInt("id") ?: 0
                        if (id > 0) TelegramAttachment(id,
                            video.optString("file_name").ifBlank { "Videó.mp4" },
                            file?.optLong("size") ?: 0, false) else null
                    } else null
                "messageVoiceNote" -> content.optJSONObject("voice_note")?.let { voice ->
                    val file = voice.optJSONObject("voice")
                    val id = file?.optInt("id") ?: 0
                    if (id > 0) TelegramAttachment(id, "Hangüzenet.ogg", file?.optLong("size") ?: 0, true)
                    else null
                }
                "messageAnimation" -> content.optJSONObject("animation")?.let { animation ->
                    val file = animation.optJSONObject("animation")
                    val id = file?.optInt("id") ?: 0
                    if (id > 0) TelegramAttachment(id,
                        animation.optString("file_name").ifBlank { "Animáció.gif" },
                        file?.optLong("size") ?: 0, false) else null
                }
                "messageVideoNote" -> if (!content.optBoolean("is_secret"))
                    content.optJSONObject("video_note")?.optJSONObject("video")?.let { file ->
                        val id = file.optInt("id")
                        if (id > 0) TelegramAttachment(id, "Videóüzenet.mp4", file.optLong("size"), false)
                        else null
                    } else null
                "messagePhoto" -> if (!content.optBoolean("is_secret")) {
                    val sizes = content.optJSONObject("photo")?.optJSONArray("sizes")
                    val photos = (0 until (sizes?.length() ?: 0)).mapNotNull { sizeIndex ->
                        sizes?.optJSONObject(sizeIndex)?.optJSONObject("photo")?.takeIf { it.optInt("id") > 0 }
                    }
                    photos.maxByOrNull { it.optLong("size") }?.let { file ->
                        val id = file.optInt("id")
                        if (id > 0) TelegramAttachment(id, "Fénykép.jpg", file.optLong("size"), false)
                        else null
                    }
                } else null
                else -> null
            }
            val text = when (content.optString("@type")) {
                "messageText" -> content.optJSONObject("text")?.optString("text").orEmpty()
                "messageDocument" -> "Fájl: ${attachment?.name ?: "ismeretlen"}"
                "messageAudio" -> attachment?.let { "Fájl: ${it.name}. Hangfájl" } ?: "Hangfájl"
                "messageVideo" -> attachment?.let { "Fájl: ${it.name}. Videó" } ?: "Videó"
                "messageAnimation" -> attachment?.let { "Fájl: ${it.name}. Animáció" } ?: "Animáció"
                "messageVideoNote" -> if (attachment != null) "Fájl: videóüzenet" else "Videóüzenet"
                "messagePhoto" -> if (attachment != null) "Fájl: fénykép" else "Fénykép"
                "messageVoiceNote" -> "Hangüzenet"
                else -> "Nem szöveges üzenet"
            }
            val senderId = item.optJSONObject("sender_id")
            val sender = when (senderId?.optString("@type")) {
                "messageSenderUser" -> "Felhasználó ${senderId.optLong("user_id")}"
                "messageSenderChat" -> "Beszélgetés ${senderId.optLong("chat_id")}"
                else -> "Ismeretlen feladó"
            }
            TelegramMessage(
                id = item.getLong("id"),
                chatId = chatId,
                sender = sender,
                text = text,
                outgoing = item.optBoolean("is_outgoing"),
                attachment = attachment,
                botButtons = botButtons(item)
            )
        }.asReversed()
    }
}

data class TdlibRequestError(val requestId: String?, val code: Int, val message: String)

enum class TelegramLoginStep {
    Parameters, PhoneNumber, EmailAddress, Code, EmailCode, Password,
    OtherDevice, Registration, PremiumPurchase, Ready, Closed, Unsupported
}
