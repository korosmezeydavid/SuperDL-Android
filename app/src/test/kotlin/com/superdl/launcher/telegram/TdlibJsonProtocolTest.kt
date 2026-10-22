package com.superdl.launcher.telegram

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TdlibJsonProtocolTest {
    @Test fun communityRequestsRequireExplicitTargetAndKeepRequestIdentity() {
        val search = JSONObject(TdlibJsonProtocol.searchPublicChat("example_group", "search-1"))
        assertEquals("searchPublicChat", search.getString("@type"))
        assertEquals("example_group", search.getString("username"))
        assertEquals("search-1", search.getString("@extra"))
        assertEquals("joinChat", JSONObject(TdlibJsonProtocol.joinPublicChat(-42, "join-1")).getString("@type"))
        assertEquals("checkChatInviteLink", JSONObject(TdlibJsonProtocol.checkInviteLink(
            "https://t.me/+invitation", "preview-1")).getString("@type"))
        assertThrows(IllegalArgumentException::class.java) {
            TdlibJsonProtocol.searchPublicChat("user/path", "search-2")
        }
    }

    @Test fun botButtonsKeepTheirOrderAndDistinguishSafeActions() {
        val message = JSONObject("""{"reply_markup":{"@type":"replyMarkupInlineKeyboard","rows":[
            [{"text":"Részletek","type":{"@type":"inlineKeyboardButtonTypeCallback","data":"AQI="}},
             {"text":"Weboldal","type":{"@type":"inlineKeyboardButtonTypeUrl","url":"https://example.org"}}],
            [{"text":"Fizetés","type":{"@type":"inlineKeyboardButtonTypeBuy"}}]]}}""")
        val buttons = TdlibJsonProtocol.botButtons(message)
        assertEquals(listOf("Részletek", "Weboldal", "Fizetés"), buttons.map { it.label })
        assertEquals(TelegramBotButton.Kind.CALLBACK, buttons[0].kind)
        assertEquals("AQI=", buttons[0].data)
        assertEquals(TelegramBotButton.Kind.UNSUPPORTED, buttons[1].kind)
        assertEquals(TelegramBotButton.Kind.UNSUPPORTED, buttons[2].kind)
        val callback = JSONObject(TdlibJsonProtocol.botCallback(-42, 5, buttons[0].data, "callback-1"))
        assertEquals("getCallbackQueryAnswer", callback.getString("@type"))
        assertEquals("AQI=", callback.getJSONObject("payload").getString("data"))
        val reply = JSONObject("""{"reply_markup":{"@type":"replyMarkupShowKeyboard","rows":[
            [{"text":"/help","type":{"@type":"keyboardButtonTypeText"}}]]}}""")
        assertEquals(TelegramBotButton.Kind.TEXT, TdlibJsonProtocol.botButtons(reply).single().kind)
    }
    @Test fun onlyIncomingLiveMessageProducesNotificationKey() {
        assertEquals("-7:42", TdlibJsonProtocol.incomingMessageKey(
            """{"@type":"updateNewMessage","message":{"id":42,"chat_id":-7,"is_outgoing":false,"content":{"text":"titok"}}}"""
        ))
        assertNull(TdlibJsonProtocol.incomingMessageKey(
            """{"@type":"updateNewMessage","message":{"id":42,"chat_id":-7,"is_outgoing":true}}"""
        ))
        assertNull(TdlibJsonProtocol.incomingMessageKey(
            """{"@type":"messages","messages":[]}"""
        ))
    }
    @Test fun loginRequestsFollowOfficialJsonTypesAndRejectMalformedInputs() {
        val parameters = JSONObject(TdlibJsonProtocol.parameters(
            123456, "0123456789abcdef0123456789abcdef", "/private/db", "/private/files",
            "hu", "14", "1.88.0"
        ))
        assertEquals("setTdlibParameters", parameters.getString("@type"))
        assertEquals(false, parameters.getBoolean("use_test_dc"))
        assertEquals("/private/db", parameters.getString("database_directory"))
        assertEquals("setAuthenticationPhoneNumber",
            JSONObject(TdlibJsonProtocol.phoneNumber("+36123456789")).getString("@type"))
        assertEquals("checkAuthenticationCode",
            JSONObject(TdlibJsonProtocol.authenticationCode("12345")).getString("@type"))
        assertEquals("setAuthenticationEmailAddress",
            JSONObject(TdlibJsonProtocol.authenticationEmailAddress(" a@example.com ")).getString("@type"))
        assertEquals("checkAuthenticationPassword",
            JSONObject(TdlibJsonProtocol.authenticationPassword("secret")).getString("@type"))
        assertEquals("emailAddressAuthenticationCode",
            JSONObject(TdlibJsonProtocol.authenticationEmailCode("12345"))
                .getJSONObject("code").getString("@type"))
        assertEquals("logOut", JSONObject(TdlibJsonProtocol.logOut()).getString("@type"))
        assertThrows(IllegalArgumentException::class.java) { TdlibJsonProtocol.phoneNumber("061234") }
        assertThrows(IllegalArgumentException::class.java) {
            TdlibJsonProtocol.parameters(1, "invalid", "/db", "/files", "hu", "14", "1")
        }
    }

    @Test fun unknownLoginStateCannotBeMistakenForReady() {
        assertEquals(TelegramLoginStep.Ready, TdlibJsonProtocol.loginStep(
            """{"@type":"updateAuthorizationState","authorization_state":{"@type":"authorizationStateReady"}}"""
        ))
        assertEquals(TelegramLoginStep.Registration, TdlibJsonProtocol.loginStep(
            """{"@type":"updateAuthorizationState","authorization_state":{"@type":"authorizationStateWaitRegistration"}}"""
        ))
        assertEquals(TelegramLoginStep.EmailAddress, TdlibJsonProtocol.loginStep(
            """{"@type":"updateAuthorizationState","authorization_state":{"@type":"authorizationStateWaitEmailAddress"}}"""
        ))
        assertEquals(TelegramLoginStep.PhoneNumber, TdlibJsonProtocol.loginStep(
            """{"@type":"authorizationStateWaitPhoneNumber","@extra":"auth-state"}"""
        ))
        assertNull(TdlibJsonProtocol.loginStep("""{"@type":"ok"}"""))
    }
    @Test fun confirmedTextIsJsonEscapedAndCorrelatedWithoutImplicitPayment() {
        val json = TdlibJsonProtocol.sendText(
            TelegramSendRequest(45L, "Szia \"Dóra\"\nMásodik sor"), "send-1"
        )
        val request = JSONObject(json)
        assertEquals("sendMessage", request.getString("@type"))
        assertEquals("send-1", request.getString("@extra"))
        assertEquals(45L, request.getLong("chat_id"))
        assertEquals(
            "Szia \"Dóra\"\nMásodik sor",
            request.getJSONObject("input_message_content").getJSONObject("text").getString("text")
        )
        assertEquals(JSONObject.NULL, request.get("options"))
    }

    @Test fun emptyTextAndInvalidChatCannotBecomeRequests() {
        assertThrows(IllegalArgumentException::class.java) {
            TdlibJsonProtocol.sendText(TelegramSendRequest(0L, "Szia"), "1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TdlibJsonProtocol.sendText(TelegramSendRequest(1L, "  "), "1")
        }
    }

    @Test fun historyIsBoundedAndStartsFromLatestMessage() {
        val history = JSONObject(TdlibJsonProtocol.chatHistory(-77L))
        assertEquals("getChatHistory", history.getString("@type"))
        assertEquals(-77L, history.getLong("chat_id"))
        assertEquals(0L, history.getLong("from_message_id"))
        assertEquals(30, history.getInt("limit"))
        assertThrows(IllegalArgumentException::class.java) {
            TdlibJsonProtocol.chatHistory(-77L, limit = 101)
        }
    }

    @Test fun loginStateAndCorrelatedErrorAreDistinguished() {
        assertEquals("authorizationStateWaitCode", TdlibJsonProtocol.authorizationState(
            """{"@type":"updateAuthorizationState","authorization_state":{"@type":"authorizationStateWaitCode"}}"""
        ))
        assertNull(TdlibJsonProtocol.authorizationState("""{"@type":"ok"}"""))
        assertEquals(TdlibRequestError("send-1", 429, "FLOOD_WAIT"), TdlibJsonProtocol.requestError(
            """{"@type":"error","@extra":"send-1","code":429,"message":"FLOOD_WAIT"}"""
        ))
    }

    @Test fun chatSnapshotRequestsAndParsingPreserveNegativeIds() {
        assertEquals("getChats", JSONObject(TdlibJsonProtocol.getChats()).getString("@type"))
        assertEquals(-77L, JSONObject(TdlibJsonProtocol.getChat(-77L)).getLong("chat_id"))
        assertEquals(listOf(-77L, 45L), TdlibJsonProtocol.chatIds(
            """{"@type":"chats","chat_ids":[-77,45]}"""
        ))
        assertEquals(TelegramChat(-77L, "Csoport", 2), TdlibJsonProtocol.chat(
            """{"@type":"chat","id":-77,"title":"Csoport","unread_count":2}"""
        ))
    }

    @Test fun historyBecomesChronologicalAndMediaIsNamed() {
        val history = """{"@type":"messages","messages":[
            {"id":12,"chat_id":-77,"is_outgoing":true,
             "content":{"@type":"messagePhoto"}},
            {"id":11,"chat_id":-77,"is_outgoing":false,
             "sender_id":{"@type":"messageSenderUser","user_id":8},
             "content":{"@type":"messageText","text":{"@type":"formattedText","text":"Szia"}}}
        ]}"""
        val messages = TdlibJsonProtocol.messages(history, -77L)
        assertEquals(listOf(11L, 12L), messages.map { it.id })
        assertEquals("Szia", messages[0].text)
        assertTrue(messages[0].sender.contains("8"))
        assertEquals("Fénykép", messages[1].text)
        assertThrows(IllegalArgumentException::class.java) {
            TdlibJsonProtocol.messages(history, 45L)
        }
    }

    @Test fun liveUpdateAndReadRequestUseTheFocusedMessageOnly() {
        val live = """{"@type":"updateNewMessage","message":{"id":42,"chat_id":7,
            "is_outgoing":false,"sender_id":{"@type":"messageSenderUser","user_id":8},
            "content":{"@type":"messageText","text":{"text":"Szia"}}}}"""
        assertEquals("Szia", TdlibJsonProtocol.newMessage(live)?.text)
        assertNull(TdlibJsonProtocol.newMessage("""{"@type":"ok"}"""))
        val viewed = JSONObject(TdlibJsonProtocol.viewMessage(7, 42))
        assertEquals("viewMessages", viewed.getString("@type"))
        assertEquals(42L, viewed.getJSONArray("message_ids").getLong(0))
        assertEquals(1, viewed.getJSONArray("message_ids").length())
        assertTrue(viewed.getBoolean("force_read"))
    }

    @Test fun mediaRequestsUseLocalFilesAndRequireExplicitDownload() {
        val document = JSONObject(TdlibJsonProtocol.sendDocument(45, "/private/f.txt", "file-1"))
        val documentContent = document.getJSONObject("input_message_content")
        assertEquals("inputMessageDocument", documentContent.getString("@type"))
        val inputDocument = documentContent.getJSONObject("document")
        assertEquals("inputDocument", inputDocument.getString("@type"))
        assertEquals("/private/f.txt", inputDocument.getJSONObject("document").getString("path"))
        assertTrue(inputDocument.getBoolean("disable_content_type_detection"))
        val voice = JSONObject(TdlibJsonProtocol.sendVoiceNote(45, "/private/v.m4a", 7, "voice-1"))
        assertEquals("inputMessageVoiceNote", voice.getJSONObject("input_message_content").getString("@type"))
        val inputVoice = voice.getJSONObject("input_message_content").getJSONObject("voice_note")
        assertEquals("inputVoiceNote", inputVoice.getString("@type"))
        assertEquals("/private/v.m4a", inputVoice.getJSONObject("voice_note").getString("path"))
        assertEquals(7, inputVoice.getInt("duration"))
        val download = JSONObject(TdlibJsonProtocol.downloadFile(67, "download-1"))
        assertEquals("downloadFile", download.getString("@type"))
        assertEquals(false, download.getBoolean("synchronous"))
        assertEquals(67 to "/private/file", TdlibJsonProtocol.downloadedFile(
            """{"@type":"updateFile","file":{"id":67,"local":{"is_downloading_completed":true,"path":"/private/file"}}}"""))
        assertNull(TdlibJsonProtocol.downloadedFile(
            """{"@type":"file","id":67,"local":{"is_downloading_completed":false,"path":""}}"""))
    }

    @Test fun documentAndVoiceNoteHistoryKeepDownloadIds() {
        val history = """{"@type":"messages","messages":[
          {"id":2,"chat_id":9,"content":{"@type":"messageVoiceNote",
            "voice_note":{"voice":{"id":82,"size":400}}}},
          {"id":1,"chat_id":9,"content":{"@type":"messageDocument",
            "document":{"file_name":"levél.txt","document":{"id":81,"size":300}}}}
        ]}"""
        val messages = TdlibJsonProtocol.messages(history, 9)
        assertEquals(TelegramAttachment(81, "levél.txt", 300, false), messages[0].attachment)
        assertEquals(TelegramAttachment(82, "Hangüzenet.ogg", 400, true), messages[1].attachment)
    }

    @Test fun receivedAudioAndOtherMediaAreRecognizedAsSavableFiles() {
        val history = """{"@type":"messages","messages":[
          {"id":5,"chat_id":9,"content":{"@type":"messageAudio",
            "audio":{"file_name":"dal.mp3","audio":{"id":85,"size":500}}}},
          {"id":4,"chat_id":9,"content":{"@type":"messageVideo",
            "video":{"file_name":"film.mp4","video":{"id":84,"size":400}}}},
          {"id":3,"chat_id":9,"content":{"@type":"messagePhoto",
            "photo":{"sizes":[{"photo":{"id":81,"size":100}},
                              {"photo":{"id":83,"size":300}}]}}},
          {"id":2,"chat_id":9,"content":{"@type":"messageAnimation",
            "animation":{"file_name":"anim.gif","animation":{"id":82,"size":200}}}},
          {"id":1,"chat_id":9,"content":{"@type":"messageVideo","is_secret":true,
            "video":{"file_name":"egyszer.mp4","video":{"id":80,"size":100}}}}
        ]}"""
        val messages = TdlibJsonProtocol.messages(history, 9)
        assertNull(messages[0].attachment)
        assertEquals(TelegramAttachment(82, "anim.gif", 200, false), messages[1].attachment)
        assertEquals(TelegramAttachment(83, "Fénykép.jpg", 300, false), messages[2].attachment)
        assertEquals(TelegramAttachment(84, "film.mp4", 400, false), messages[3].attachment)
        assertEquals(TelegramAttachment(85, "dal.mp3", 500, false), messages[4].attachment)
        assertTrue(messages[4].text.startsWith("Fájl: dal.mp3"))
    }
}
