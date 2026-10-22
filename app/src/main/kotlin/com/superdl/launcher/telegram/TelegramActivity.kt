package com.superdl.launcher.telegram

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.superdl.launcher.BuildConfig
import com.superdl.launcher.MainActivity
import com.superdl.launcher.feedback.SoundFeedback
import com.superdl.launcher.feedback.SoundType
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager
import com.superdl.launcher.voice.SpeechPunctuation
import com.superdl.launcher.voice.VoiceInput
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A helyi debug Telegram-próba saját beszéddel és gesztusokkal; nem igényel TalkBacket. */
class TelegramActivity : AppCompatActivity() {
    private lateinit var tts: TtsManager
    private lateinit var sounds: SoundFeedback
    private lateinit var voiceInput: VoiceInput
    private lateinit var voiceRecorder: TelegramVoiceRecorder
    private lateinit var display: TextView
    private val sessionObserver: (String) -> Unit = { json -> if (!destroyed) receive(json) }
    private val navigation = TelegramNavigation()
    private var authStep: TelegramLoginStep? = null
    private var chatIds = emptyList<Long>()
    private val chats = linkedMapOf<Long, TelegramChat>()
    private var historyChatId: Long? = null
    private var messageMenuHintPending = false
    private var pendingSendId: String? = null
    private var pendingSendSequence = 0
    private var actionIndex = 0
    private var actions: List<Action> = emptyList()
    private var browsing = true
    private var chatListAnnounced = false
    private val viewedMessages = mutableSetOf<Pair<Long, Long>>()
    private var destroyed = false
    private var foreground = false
    private var pendingMedia: PendingMedia? = null
    private var pendingDownload: TelegramAttachment? = null
    private var playAfterDownload = false
    private var voicePlayer: MediaPlayer? = null
    private val playbackFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change <= AudioManager.AUDIOFOCUS_LOSS) runOnUiThread { stopVoicePlayback(true) }
    }
    private var pendingDownloadedPath: String? = null
    private var pendingUpload: PendingMedia? = null
    private var pendingUploadMessageId: Long? = null
    private var pendingInviteLink: String? = null
    private var invitePreviewRequest: String? = null
    private var joinRequest: String? = null
    private var callbackRequest: String? = null
    private var publicSearchRequest: String? = null
    private var pendingPublicChatId: Long? = null
    private var publicJoinRequest: String? = null
    private data class PendingMedia(val file: File, val name: String, val duration: Int = 0,
                                    val voice: Boolean = false)
    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) dictateMessage() else show("Mikrofonengedély nélkül nem tudok üzenetet diktálni.")
    }
    private val recordingPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginVoiceRecording() else show("Mikrofonengedély nélkül nem tudok hangüzenetet felvenni.")
    }
    private val pickDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) prepareDocument(uri)
    }
    private val saveDocument = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        val target = result.data?.data
        if (target == null) { pendingDownloadedPath = null; return@registerForActivityResult }
        val path = pendingDownloadedPath ?: return@registerForActivityResult
        pendingDownloadedPath = null
        Thread {
            val saved = try {
                contentResolver.openOutputStream(target)?.use { output ->
                    File(path).inputStream().use { input -> input.copyTo(output) }
                } != null
            } catch (_: Exception) { false }
            runOnUiThread { show(if (saved) "A fájlt elmentettem a kiválasztott helyre."
                else "A fájl mentése nem sikerült.") }
        }.start()
    }

    private data class Action(val label: String, val run: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Telegram – próba"
        tts = TtsManager(this)
        sounds = SoundFeedback(this)
        voiceInput = VoiceInput(this)
        voiceRecorder = TelegramVoiceRecorder(this)
        display = TextView(this).apply {
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            addView(this@TelegramActivity.display, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val gestures = SwipeGestureListener(this,
            onSwipeUp = { sounds.play(SoundType.SWIPE_UP); if (!voiceRecorder.isRecording) move(-1) },
            onSwipeDown = { sounds.play(SoundType.SWIPE_DOWN);
                if (!voiceRecorder.isRecording) move(1) },
            onSwipeRight = { sounds.play(SoundType.SWIPE_RIGHT);
                if (voiceInput.finishDictation()) Unit
                else if (voiceRecorder.isRecording) finishVoiceRecording() else activate() },
            onSwipeLeft = { sounds.play(SoundType.SWIPE_LEFT); back() },
            onDoubleTap = { announceCurrent() },
            onLongPress = { openActions() })
        root.setOnTouchListener { _, event -> gestures.detector.onTouchEvent(event); true }
        setContentView(root)

        if (!BuildConfig.FEATURE_TELEGRAM) {
            show("A Telegram-próba ebben a változatban nem érhető el.")
            return
        }
        show("Telegram próba. Fel és le: léptetés. Jobbra: kiválasztás. Balra: vissza.")
        TelegramSession.observe(sessionObserver)
        try {
            ContextCompat.startForegroundService(this,
                Intent(this, TelegramConnectionService::class.java))
        } catch (_: Exception) {
            show("A háttérkapcsolat nem indult. A Telegram csak a nyitott képernyőn maradhat aktív.")
        }
        when (TelegramSession.start(applicationContext)) {
            TdlibNativeTransport.StartResult.Started -> show("Kapcsolódás a Telegramhoz.")
            TdlibNativeTransport.StartResult.AlreadyRunning -> {
                show("A Telegram-kapcsolat aktív.")
                TelegramSession.requestAuthState()
            }
            TdlibNativeTransport.StartResult.NativeLibraryMissing -> show("A Telegram natív összetevője hiányzik erről a készülékről.")
            TdlibNativeTransport.StartResult.ClientUnavailable -> show("A Telegram nem indult el. Próbáld meg újra.")
        }
    }

    private fun receive(json: String) {
        try {
            TdlibJsonProtocol.loginStep(json)?.let { step -> onAuth(step); return }
            TdlibJsonProtocol.requestError(json)?.let { error ->
                if (error.requestId == invitePreviewRequest) {
                    invitePreviewRequest = null; pendingInviteLink = null
                    chatActions(); show("A meghívólink nem ellenőrizhető. Hibakód: ${error.code}.")
                    return
                }
                if (error.requestId == joinRequest) {
                    joinRequest = null; pendingInviteLink = null
                    chatActions()
                    show(if (error.message.contains("INVITE_REQUEST_SENT"))
                        "A csatlakozási kérelmet elküldtük. A tagság még nincs jóváhagyva."
                        else "A csatlakozás nem sikerült. Hibakód: ${error.code}.")
                    return
                }
                if (error.requestId == callbackRequest) {
                    callbackRequest = null
                    messageActions(); show("A botgomb nem hajtható végre. Hibakód: ${error.code}.")
                    return
                }
                if (error.requestId == publicSearchRequest) {
                    publicSearchRequest = null; pendingPublicChatId = null
                    chatActions(); show("A nyilvános csoport vagy csatorna nem található. Hibakód: ${error.code}.")
                    return
                }
                if (error.requestId == publicJoinRequest) {
                    publicJoinRequest = null; pendingPublicChatId = null
                    chatActions()
                    show(if (error.message.contains("INVITE_REQUEST_SENT"))
                        "A csatlakozási kérelmet elküldtük. A tagság még nincs jóváhagyva."
                        else "A csatlakozás nem sikerült. Hibakód: ${error.code}.")
                    return
                }
                val failedSend = error.requestId != null && error.requestId == pendingSendId
                if (failedSend) {
                    pendingSendId = null
                    pendingUpload?.let { media ->
                        pendingMedia = media
                        pendingUpload = null
                        pendingUploadMessageId = null
                    }
                }
                if (error.requestId?.startsWith("telegram-download-") == true) {
                    pendingDownload = null
                    playAfterDownload = false
                }
                if (failedSend && pendingMedia != null) {
                    reviewMedia(pendingMedia!!)
                    show("A Telegram elutasította a fájlt. Hibakód: ${error.code}. " +
                        "Indok: ${error.message.take(180)}. Újrapróbálhatod vagy elvetheted.")
                } else show("A Telegram elutasította a műveletet. Hibakód: ${error.code}. " +
                    "Indok: ${error.message.take(180)}. Próbáld újra.")
                return
            }
            TdlibJsonProtocol.downloadedFile(json)?.let { (id, path) ->
                val attachment = pendingDownload
                if (attachment != null && attachment.fileId == id) {
                    pendingDownload = null
                    if (playAfterDownload) {
                        playAfterDownload = false
                        playVoiceFile(path)
                        return
                    }
                    pendingDownloadedPath = path
                    show("A ${attachment.name} letöltve. Válassz mentési helyet.")
                    saveDocument.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = if (attachment.voiceNote) "audio/ogg" else "application/octet-stream"
                        putExtra(Intent.EXTRA_TITLE, attachment.name)
                    })
                }
                return
            }
            when (TdlibJsonProtocol.responseType(json)) {
                "chatInviteLinkInfo" -> {
                    val info = JSONObject(json)
                    if (info.optString("@extra") != invitePreviewRequest) return
                    invitePreviewRequest = null
                    val name = info.optString("title").ifBlank { "Névtelen közösség" }
                    val count = info.optInt("member_count").coerceAtLeast(0)
                    if (!info.isNull("subscription_info")) {
                        pendingInviteLink = null; chatActions()
                        show("$name. A meghívó előfizetést kér; ebben a próbában nem csatlakozunk.")
                    } else {
                        val requestOnly = info.optBoolean("creates_join_request")
                        show("$name. $count tag. " + if (requestOnly)
                            "Jóváhagyást igénylő csatlakozási kérelem. Jobbra: kérelem elküldése."
                            else "Jobbra: csatlakozás. Balra: mégse.")
                        setActions(listOf(Action(if (requestOnly) "Csatlakozási kérelem elküldése"
                            else "Csatlakozás megerősítése") {
                            if (joinRequest != null) { show("Az előző csatlakozás válaszára várunk."); return@Action }
                            val link = pendingInviteLink ?: return@Action
                            val id = "telegram-join-${++pendingSendSequence}"
                            if (send(TdlibJsonProtocol.joinByInviteLink(link, id))) {
                                joinRequest = id
                                show("Csatlakozás folyamatban. A tagság még nincs igazolva.")
                            }
                        }, Action("Mégse") { pendingInviteLink = null; chatActions(); show("Csatlakozás elvetve.") }))
                    }
                }
                "callbackQueryAnswer" -> {
                    val response = JSONObject(json)
                    if (response.optString("@extra") != callbackRequest) return
                    callbackRequest = null
                    messageActions()
                    val answer = response.optString("text").ifBlank { "A bot fogadta a gombnyomást." }
                    val url = response.optString("url")
                    show(if (url.isBlank()) answer else "$answer A bot külső oldalt javasol; ezt nem nyitottam meg.")
                    (navigation.page as? TelegramPage.Messages)?.chatId?.let { chatId ->
                        historyChatId = chatId
                        send(TdlibJsonProtocol.chatHistory(chatId))
                    }
                }
                "superdlSessionStopped" -> show("A Telegram-kapcsolat leállt.")
                "ok" -> {
                    val response = JSONObject(json)
                    if (response.optString("@extra") == publicJoinRequest) {
                        publicJoinRequest = null; pendingPublicChatId = null
                        chatActions(); show("Csatlakozás sikeres. A beszélgetéslista frissítése.")
                        send(TdlibJsonProtocol.getChats())
                    } else if (authStep == TelegramLoginStep.Ready && chatIds.isEmpty()) {
                        send(TdlibJsonProtocol.getChats())
                    }
                }
                "chats" -> {
                    chatIds = TdlibJsonProtocol.chatIds(json)
                    chats.keys.retainAll(chatIds.toSet())
                    chatIds.forEach { send(TdlibJsonProtocol.getChat(it)) }
                    updateChats()
                }
                "chat" -> {
                    val response = JSONObject(json)
                    if (response.optString("@extra") == publicSearchRequest) {
                        publicSearchRequest = null
                        val type = response.optJSONObject("type")
                        if (type?.optString("@type") != "chatTypeSupergroup") {
                            chatActions(); show("Ez a nyilvános név nem csoport vagy csatorna. Nem csatlakoztunk.")
                            return
                        }
                        val chatId = response.optLong("id")
                        if (chatId == 0L) { chatActions(); show("A közösség azonosítója hiányzik."); return }
                        pendingPublicChatId = chatId
                        val kind = if (type.optBoolean("is_channel")) "csatorna" else "csoport"
                        val title = response.optString("title").ifBlank { "Névtelen közösség" }
                        setActions(listOf(Action("Csatlakozás megerősítése") {
                            if (publicJoinRequest != null) { show("Az előző csatlakozás válaszára várunk."); return@Action }
                            val target = pendingPublicChatId ?: return@Action
                            val id = "telegram-public-join-${++pendingSendSequence}"
                            if (send(TdlibJsonProtocol.joinPublicChat(target, id))) {
                                publicJoinRequest = id
                                show("Csatlakozás folyamatban. A tagság még nincs igazolva.")
                            }
                        }, Action("Mégse") {
                            pendingPublicChatId = null; chatActions(); show("Csatlakozás elvetve.")
                        }))
                        show("$title, nyilvános $kind. Jobbra: csatlakozás megerősítése. Balra: mégse.")
                        return
                    }
                    if (response.optString("@extra") == joinRequest) {
                        joinRequest = null; pendingInviteLink = null
                        chatActions(); show("Csatlakozás sikeres. A beszélgetéslista frissítése.")
                        send(TdlibJsonProtocol.getChats())
                    }
                    val chat = TdlibJsonProtocol.chat(json)
                    if (chat.id in chatIds) {
                        chats[chat.id] = chat
                        updateChats()
                    }
                }
                "messages" -> {
                    val chatId = historyChatId ?: return
                    historyChatId = null
                    navigation.updateMessages(chatId, TdlibJsonProtocol.messages(json, chatId))
                    if (foreground && browsing && (navigation.page as? TelegramPage.Messages)?.chatId == chatId) {
                        val hint = if (messageMenuHintPending) " Hosszan érintve az egyéb műveletek menüje nyílik meg." else ""
                        messageMenuHintPending = false
                        show(navigation.speakCurrent() + hint)
                        markFocusedRead()
                    }
                }
                "updateNewMessage" -> {
                    val message = TdlibJsonProtocol.newMessage(json) ?: return
                    val wasLatest = navigation.isAtLatestMessage()
                    if (navigation.appendMessage(message) && foreground && browsing && wasLatest) {
                        show(navigation.speakCurrent())
                        markFocusedRead()
                    }
                    if (message.chatId in chatIds) send(TdlibJsonProtocol.getChat(message.chatId))
                }
                "updateChatReadInbox" -> {
                    val update = JSONObject(json)
                    val chatId = update.optLong("chat_id")
                    chats[chatId]?.let { chat ->
                        chats[chatId] = chat.copy(unreadCount = update.optInt("unread_count").coerceAtLeast(0))
                        navigation.updateChats(chatIds.mapNotNull(chats::get))
                    }
                }
                "message" -> {
                    val response = JSONObject(json)
                    if (response.optString("@extra") == pendingSendId) {
                        pendingUploadMessageId = response.optLong("id").takeIf { it != 0L }
                        pendingSendId = null
                        show("A Telegram átvette a küldést. A kézbesítés még nem igazolt.")
                    }
                }
                "updateMessageSendSucceeded" -> {
                    val oldId = JSONObject(json).optLong("old_message_id")
                    if (oldId != 0L && oldId == pendingUploadMessageId) {
                        pendingUpload?.file?.delete()
                        pendingUpload = null
                        pendingUploadMessageId = null
                    }
                    show("Az üzenet elküldve.")
                }
            }
        } catch (_: Exception) {
            // A kivétel szövege fiókadatot vagy üzenetet tartalmazhat; nem naplózzuk.
            show("A Telegram válaszát nem sikerült értelmezni.")
        }
    }

    private fun onAuth(step: TelegramLoginStep) {
        if (authStep == step) return
        authStep = step
        when (step) {
            TelegramLoginStep.Parameters -> show("A Telegram-kapcsolat beállítása folyamatban.")
            TelegramLoginStep.PhoneNumber -> loginAction("Telefonszám szükséges, nemzetközi formában.",
                "Telefonszám, például +36123456789", false) { send(TdlibJsonProtocol.phoneNumber(it)) }
            TelegramLoginStep.EmailAddress -> loginAction("Telegram e-mail-cím szükséges.",
                "E-mail-cím", false) { send(TdlibJsonProtocol.authenticationEmailAddress(it)) }
            TelegramLoginStep.Code -> loginAction("Telegram belépési kód szükséges. A kódot nem olvassuk fel.",
                "Belépési kód", true) { send(TdlibJsonProtocol.authenticationCode(it)) }
            TelegramLoginStep.EmailCode -> loginAction("Telegram e-mail-kód szükséges. A kódot nem olvassuk fel.",
                "E-mail-kód", true) { send(TdlibJsonProtocol.authenticationEmailCode(it)) }
            TelegramLoginStep.Password -> loginAction("Kétlépcsős jelszó szükséges. A jelszót nem olvassuk fel.",
                "Kétlépcsős jelszó", true) { send(TdlibJsonProtocol.authenticationPassword(it)) }
            TelegramLoginStep.Ready -> {
                show("Telegram belépve. Beszélgetések betöltése.")
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    show("Az új üzenetek jelzéséhez értesítési engedélyt kérek.")
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7634)
                }
                send(TdlibJsonProtocol.loadChats())
                send(TdlibJsonProtocol.getChats())
            }
            TelegramLoginStep.OtherDevice -> show("A belépést a másik Telegram-eszközön kell jóváhagyni. Várakozás.")
            TelegramLoginStep.Registration -> show("Ez a fiók regisztrációt kér. A próba még nem kezeli ezt az állapotot.")
            TelegramLoginStep.PremiumPurchase -> show("A Telegram további jóváhagyást kér. A próba nem folytatja automatikusan.")
            TelegramLoginStep.Closed -> show("A Telegram-kapcsolat bezárult.")
            TelegramLoginStep.Unsupported -> show("Ismeretlen Telegram belépési állapot. Nincs automatikus továbblépés.")
        }
    }

    private fun loginAction(message: String, hint: String, secret: Boolean, onValue: (String) -> Boolean) {
        show("$message Megnyitom a beviteli ablakot. Írd be az adatot, majd válaszd a Folytatás gombot.")
        setActions(listOf(Action("Adat megadása: $hint") { input(hint, secret, onValue) }))
        // Egyetlen menüpontnál a fel/le söprés ugyanarra az elemre jutna vissza.
        // A bekért adatmezőt ezért magától megnyitjuk; később jobbra újranyitható.
        val expectedStep = authStep
        display.post { if (!destroyed && authStep == expectedStep) input(hint, secret, onValue) }
    }

    private fun input(hint: String, secret: Boolean, onValue: (String) -> Boolean) {
        val field = EditText(this).apply {
            this.hint = hint
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else if (hint.startsWith("Telefonszám")) InputType.TYPE_CLASS_PHONE
                else InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            contentDescription = hint
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(hint)
            .setView(field)
            .setPositiveButton("Folytatás", null)
            .setNeutralButton(if (secret) "Bevitel súgó" else "Beírt szöveg felolvasása", null)
            .setNegativeButton("Mégse", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val value = field.text.toString()
                    if (!onValue(value)) return@setOnClickListener
                    field.text.clear()
                    dialog.dismiss()
                    sounds.play(SoundType.ACTION_OK)
                    if (secret) show("Adat elküldve. Várakozás a Telegram válaszára.")
                } catch (_: IllegalArgumentException) {
                    sounds.play(SoundType.ACTION_ERROR)
                    show("Az adat formátuma nem megfelelő. Próbáld újra.")
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (secret) show("A kódot és a jelszót biztonsági okból nem olvasom fel. A Folytatás gombbal küldheted el.")
                else show(field.text?.toString()?.takeIf { it.isNotBlank() }
                    ?.map { it.toString() }?.joinToString(", ") ?: "Még nem írtál be semmit.")
            }
            field.requestFocus()
            field.post {
                (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    private fun updateChats() {
        navigation.updateChats(chatIds.mapNotNull(chats::get))
        if (!chatListAnnounced && navigation.page == TelegramPage.Chats && chats.isNotEmpty()) {
            chatListAnnounced = true
            show(navigation.speakCurrent() + " Jobbra: megnyitás. Hosszan érintve: egyéb műveletek és beállítások. Balra: vissza a SuperDL-be.")
            chatActions()
        }
    }

    private fun chatActions(): Unit = setActions(listOf(
        Action("Kijelölt beszélgetés felolvasása") { show(navigation.speakCurrent()) },
        Action("Előző beszélgetés") { show(navigation.move(-1)) },
        Action("Következő beszélgetés") { show(navigation.move(1)) },
        Action("Beszélgetés megnyitása") {
            val chatId = navigation.openFocusedChat()
            if (chatId == null) show("Nincs megnyitható beszélgetés.")
            else {
                historyChatId = chatId
                show("Üzenetek betöltése.")
                send(TdlibJsonProtocol.chatHistory(chatId))
                messageActions()
            }
        },
        Action("Beszélgetéslista frissítése") { send(TdlibJsonProtocol.getChats()) },
        Action("Csatlakozás csoporthoz vagy csatornához meghívólinkkel") { askInviteLink() },
        Action("Nyilvános csoport vagy csatorna keresése név alapján") { askPublicChat() },
        Action(if (TelegramConnectionService.isMuted(this)) "Üzenethang bekapcsolása"
            else "Üzenethang némítása") {
            val muted = !TelegramConnectionService.isMuted(this)
            TelegramConnectionService.setMuted(this, muted)
            chatActions()
            show(if (muted) "Az új Telegram-üzenetek hangja némítva. Az értesítés megmarad."
                else "Az új Telegram-üzenetek hangja bekapcsolva.")
        },
        Action("Vissza a SuperDL-be, Telegram a háttérben") { returnToLauncher() }
    ), browsingMode = true)

    private fun messageActions(): Unit = setActions(listOf(
        Action("Új szöveges üzenet diktálása") { beginMessageDictation() },
        Action("Hangüzenet felvétele") { requestVoiceRecording() },
        Action("Fájl küldése") { pickDocument.launch(arrayOf("*/*")) },
        Action("Kijelölt fájl vagy hangüzenet mentése") { downloadFocusedAttachment() },
        Action("Kijelölt hangüzenet lejátszása") { playFocusedVoice() },
        Action("Kijelölt üzenet botgombjai") { botButtonActions() },
        Action("Üzenetek frissítése") {
            val chatId = (navigation.page as? TelegramPage.Messages)?.chatId ?: return@Action
            historyChatId = chatId
            send(TdlibJsonProtocol.chatHistory(chatId))
        },
        Action("Vissza a beszélgetésekhez") { browsing = true; back() }
    ), browsingMode = true)

    private fun askInviteLink() {
        input("Telegram meghívólink, https://t.me/ kezdetű", false) { value ->
            val link = value.trim()
            val request = try { TdlibJsonProtocol.checkInviteLink(link,
                "telegram-invite-${++pendingSendSequence}") }
            catch (_: IllegalArgumentException) {
                show("Teljes Telegram meghívólinket adj meg, https://t.me/ kezdetűt.")
                return@input false
            }
            pendingInviteLink = link
            invitePreviewRequest = JSONObject(request).getString("@extra")
            if (send(request)) { show("Meghívó ellenőrzése folyamatban."); true }
            else { pendingInviteLink = null; invitePreviewRequest = null; false }
        }
    }

    private fun askPublicChat() {
        input("Nyilvános Telegram-név, kukac nélkül", false) { value ->
            val username = value.trim().removePrefix("@")
            val request = try { TdlibJsonProtocol.searchPublicChat(username,
                "telegram-public-search-${++pendingSendSequence}") }
            catch (_: IllegalArgumentException) {
                show("Legalább öt karakteres Telegram-nevet adj meg, kukac nélkül.")
                return@input false
            }
            publicSearchRequest = JSONObject(request).getString("@extra")
            if (send(request)) { show("Nyilvános közösség keresése."); true }
            else { publicSearchRequest = null; false }
        }
    }

    private fun botButtonActions() {
        val message = navigation.focusedMessage()
        val buttons = message?.botButtons.orEmpty()
        if (message == null || buttons.isEmpty()) { show("A kijelölt üzenetben nincs botgomb."); return }
        setActions(buttons.map { button ->
            Action("Botgomb: ${button.label}" + if (button.kind == TelegramBotButton.Kind.UNSUPPORTED)
                ", itt nem támogatott típus" else "") {
                when (button.kind) {
                    TelegramBotButton.Kind.TEXT -> {
                        if (navigation.reviewSend(button.label)) {
                            reviewActions(); show(navigation.speakCurrent())
                        }
                    }
                    TelegramBotButton.Kind.CALLBACK -> {
                        if (callbackRequest != null) { show("Az előző botgomb válaszára várunk."); return@Action }
                        val id = "telegram-callback-${++pendingSendSequence}"
                        val request = TdlibJsonProtocol.botCallback(message.chatId, message.id, button.data, id)
                        if (send(request)) {
                            callbackRequest = id
                            show("Botgomb elküldve. Várakozás a bot válaszára.")
                        }
                    }
                    TelegramBotButton.Kind.UNSUPPORTED ->
                        show("Ez a botgomb más műveletet kér, amit a kliens még nem kezel. Nem nyomtam meg.")
                }
            }
        } + Action("Vissza az üzenetekhez") { messageActions(); show(navigation.speakCurrent()) })
        show("${buttons.size} botgomb. Fel-le választás, jobbra gombnyomás, balra vissza. ${actions.first().label}")
    }

    private fun requestVoiceRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) beginVoiceRecording()
        else recordingPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun beginVoiceRecording() {
        if (navigation.page !is TelegramPage.Messages) return
        tts.speakThen("Hangüzenet felvétele. A sípolás után beszélj. Jobbra a leállítás, balra az elvetés.") {
            if (destroyed || navigation.page !is TelegramPage.Messages) return@speakThen
            try { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).apply {
                startTone(ToneGenerator.TONE_PROP_BEEP2, 130)
                display.postDelayed({ release() }, 200)
            } } catch (_: Exception) { }
            display.postDelayed({
                if (destroyed || navigation.page !is TelegramPage.Messages) return@postDelayed
                if (!voiceRecorder.start()) { show("A hangfelvétel nem indult el."); messageActions() }
                else {
                    display.text = "Hangüzenet felvétele folyamatban. Jobbra: leállítás. Balra: elvetés."
                    setActions(listOf(Action("Felvétel leállítása") { finishVoiceRecording() },
                        Action("Felvétel elvetése") { voiceRecorder.cancel(); messageActions(); show("Felvétel elvetve.") }))
                }
            }, 250)
        }
    }

    private fun finishVoiceRecording() {
        val recording = voiceRecorder.stop()
        if (recording == null) { messageActions(); show("Nem sikerült menteni a hangüzenetet."); return }
        val (file, duration) = recording
        val voiceNote = Build.VERSION.SDK_INT >= 29
        reviewMedia(PendingMedia(file, "Hangüzenet, $duration másodperc", duration, voiceNote))
    }

    private fun prepareDocument(uri: Uri) {
        val chatId = (navigation.page as? TelegramPage.Messages)?.chatId ?: return
        val name = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) { null }
        val safeName = name.orEmpty().substringAfterLast('/').substringAfterLast('\\')
            .take(100).takeUnless { it.isBlank() || it == "." || it == ".." } ?: "fajl"
        show("A fájl előkészítése folyamatban.")
        Thread {
            var destination: File? = null
            val file = try {
                val dir = File(cacheDir, "telegram_uploads/${UUID.randomUUID()}").apply { mkdirs() }
                destination = File(dir, safeName)
                contentResolver.openInputStream(uri)?.use { source ->
                    destination.outputStream().use { target ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_UPLOAD_BYTES) throw IllegalStateException("Túl nagy fájl")
                            target.write(buffer, 0, count)
                        }
                    }
                } ?: throw IllegalStateException()
                if (destination.length() == 0L) throw IllegalStateException()
                destination
            } catch (_: Exception) { destination?.delete(); destination?.parentFile?.delete(); null }
            runOnUiThread {
                if (file == null) show("A fájlt nem sikerült beolvasni. A próba legfeljebb 200 megabájtos fájlt kezel.")
                else if ((navigation.page as? TelegramPage.Messages)?.chatId == chatId)
                    reviewMedia(PendingMedia(file, safeName))
                else { file.delete(); show("A beszélgetés megváltozott. A küldést megszakítottam.") }
            }
        }.start()
    }

    private fun reviewMedia(media: PendingMedia) {
        pendingMedia?.file?.takeIf { it != media.file }?.delete()
        pendingMedia = media
        val chatId = (navigation.page as? TelegramPage.Messages)?.chatId ?: return
        val recipient = chats[chatId]?.title ?: "kijelölt beszélgetés"
        show("Küldés ellenőrzése. Címzett: $recipient. ${media.name}. Jobbra: küldés, balra: elvetés.")
        setActions(listOf(Action("${media.name} elküldésének megerősítése") {
            val current = pendingMedia ?: return@Action
            if (pendingSendId != null || pendingUpload != null) {
                show("Az előző küldés még folyamatban van."); return@Action
            }
            val id = "telegram-media-${++pendingSendSequence}"
            val request = if (current.voice) TdlibJsonProtocol.sendVoiceNote(chatId,
                current.file.absolutePath, current.duration, id)
                else TdlibJsonProtocol.sendDocument(chatId, current.file.absolutePath, id)
            if (send(request)) {
                pendingSendId = id
                pendingUpload = current
                pendingMedia = null
                messageActions()
                show("Feltöltés folyamatban. A kézbesítés még nem igazolt.")
            }
        }, Action("Küldés elvetése") {
            pendingMedia?.file?.delete()
            pendingMedia = null
            messageActions()
            show("Küldés elvetve.")
        }))
    }

    private fun downloadFocusedAttachment() {
        val file = navigation.focusedMessage()?.attachment
        if (file == null) { show("A kijelölt üzenetben nincs menthető fájl."); return }
        if (pendingDownload != null) { show("Egy letöltés már folyamatban van."); return }
        pendingDownload = file
        playAfterDownload = false
        val id = "telegram-download-${++pendingSendSequence}"
        if (send(TdlibJsonProtocol.downloadFile(file.fileId, id)))
            show("A ${file.name} letöltése folyamatban. A mentési helyet a letöltés után kérdezem.")
        else pendingDownload = null
    }

    private fun playFocusedVoice() {
        val attachment = navigation.focusedMessage()?.attachment
        if (attachment?.voiceNote != true) { show("A kijelölt üzenet nem hangüzenet."); return }
        if (pendingDownload != null) { show("Egy letöltés már folyamatban van."); return }
        stopVoicePlayback(false)
        pendingDownload = attachment
        playAfterDownload = true
        val id = "telegram-download-${++pendingSendSequence}"
        if (send(TdlibJsonProtocol.downloadFile(attachment.fileId, id)))
            show("A hangüzenet betöltése folyamatban. Utána lejátszom.")
        else { pendingDownload = null; playAfterDownload = false }
    }

    private fun playVoiceFile(path: String) {
        val file = File(path)
        if (!file.isFile || file.length() == 0L) {
            show("A hangüzenet fájlja nem érhető el.")
            return
        }
        tts.speakThen("Hangüzenet lejátszása. Jobbra: szünet, balra: leállítás.") {
            if (destroyed || navigation.page !is TelegramPage.Messages) return@speakThen
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val focus = audio.requestAudioFocus(playbackFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                show("A hangüzenet nem indítható, mert a hangkimenet foglalt.")
                return@speakThen
            }
            try {
                voicePlayer = MediaPlayer().apply {
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                    setDataSource(file.absolutePath)
                    setOnPreparedListener { player ->
                        player.start()
                        display.text = "Hangüzenet szól. Jobbra: szünet, balra: leállítás."
                        playbackActions(false)
                    }
                    setOnCompletionListener {
                        stopVoicePlayback(false)
                        messageActions()
                        show("A hangüzenet véget ért.")
                    }
                    setOnErrorListener { _, _, _ ->
                        stopVoicePlayback(false)
                        show("A hangüzenet nem játszható le ezen a készüléken.")
                        true
                    }
                    prepareAsync()
                }
            } catch (_: Exception) {
                stopVoicePlayback(false)
                show("A hangüzenet nem játszható le ezen a készüléken.")
            }
        }
    }

    private fun playbackActions(paused: Boolean) {
        setActions(listOf(Action(if (paused) "Lejátszás folytatása" else "Lejátszás szüneteltetése") {
            val player = voicePlayer ?: return@Action
            if (paused) {
                player.start()
                playbackActions(false)
                display.text = "Hangüzenet szól. Jobbra: szünet, balra: leállítás."
            } else {
                player.pause()
                playbackActions(true)
                show("Hangüzenet szüneteltetve. Jobbra: folytatás, balra: leállítás.")
            }
        }, Action("Lejátszás leállítása") { stopVoicePlayback(true) }))
    }

    private fun stopVoicePlayback(announce: Boolean) {
        voicePlayer?.let { player ->
            try { player.setOnCompletionListener(null); player.stop() } catch (_: Exception) { }
            player.release()
        }
        voicePlayer = null
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocus(playbackFocusListener)
        if (!destroyed && announce) { messageActions(); show("Hangüzenet leállítva.") }
    }

    private fun beginMessageDictation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) dictateMessage()
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun dictateMessage() {
        if (navigation.page !is TelegramPage.Messages) return
        voiceInput.listen(
            prompt = "Mondd a Telegram-üzenetet. Kimondhatod az írásjeleket is: vessző, pont, kérdőjel.",
            speakFirst = { text, onDone -> tts.speakThen(text, onDone) },
            onResult = { spoken ->
                val message = SpeechPunctuation.apply(spoken).trim()
                if (navigation.reviewSend(message)) {
                    reviewActions()
                    show(navigation.speakCurrent())
                } else show("Nem hallottam üzenetet. Válaszd újra az üzenet diktálását.")
            },
            onError = { show("Nem értettem az üzenetet. Válaszd újra a diktálást.") }
        )
    }

    private fun reviewActions(): Unit = setActions(listOf(
        Action("Üzenet küldésének megerősítése") {
            val confirmed = navigation.confirmSend() ?: return@Action
            val id = "telegram-send-${++pendingSendSequence}"
            pendingSendId = id
            if (send(TdlibJsonProtocol.sendText(confirmed, id))) {
                show("Küldési kérés elküldve. Várakozás a Telegram visszajelzésére.")
            } else pendingSendId = null
            messageActions()
        },
        Action("Címzett és üzenet ismétlése") { show(navigation.speakCurrent()) },
        Action("Mégse, vissza az üzenetekhez") { back() }
    ))

    private fun setActions(items: List<Action>, browsingMode: Boolean = false) {
        actions = items
        actionIndex = 0
        browsing = browsingMode
    }

    private fun move(delta: Int) {
        if (browsing && authStep == TelegramLoginStep.Ready) {
            show(navigation.move(delta))
            markFocusedRead()
            return
        }
        if (actions.isNotEmpty()) {
            actionIndex = (actionIndex + delta).mod(actions.size)
            show(actions[actionIndex].label)
        }
    }

    private fun activate() {
        if (browsing && authStep == TelegramLoginStep.Ready) {
            when (navigation.page) {
                TelegramPage.Chats -> {
                    val chatId = navigation.openFocusedChat()
                    if (chatId == null) show("Nincs megnyitható beszélgetés.")
                    else {
                        historyChatId = chatId
                        messageMenuHintPending = true
                        messageActions()
                        show("Üzenetek betöltése. Fel és le: üzenetek. Jobbra: hangüzenet lejátszása vagy fájl mentése. Hosszan érintve: egyéb műveletek.")
                        send(TdlibJsonProtocol.chatHistory(chatId))
                    }
                }
                is TelegramPage.Messages -> {
                    when (navigation.focusedMessage()?.attachment?.voiceNote) {
                        true -> playFocusedVoice()
                        false -> downloadFocusedAttachment()
                        null -> openActions()
                    }
                }
                is TelegramPage.ReviewSend -> Unit
            }
            return
        }
        actions.getOrNull(actionIndex)?.run?.invoke()
    }

    private fun openActions() {
        if (!browsing || authStep != TelegramLoginStep.Ready) return
        when (navigation.page) {
            TelegramPage.Chats -> chatActions()
            is TelegramPage.Messages -> messageActions()
            is TelegramPage.ReviewSend -> return
        }
        browsing = false
        show("Műveletek. Fel és le: választás, jobbra: indítás, balra: vissza. ${actions.firstOrNull()?.label.orEmpty()}")
    }

    private fun markFocusedRead() {
        if (!foreground) return
        val message = navigation.focusedMessage() ?: return
        if (message.outgoing) return
        val key = message.chatId to message.id
        if (viewedMessages.add(key) && !TelegramSession.send(TdlibJsonProtocol.viewMessage(message.chatId, message.id))) {
            viewedMessages.remove(key)
        }
    }

    private fun announceCurrent() {
        show(if (authStep == TelegramLoginStep.Ready) navigation.speakCurrent()
            else actions.getOrNull(actionIndex)?.label ?: "Várakozás a Telegramra.")
    }

    private fun back() {
        voiceInput.cancel()
        if (voicePlayer != null) { stopVoicePlayback(true); return }
        if (pendingInviteLink != null && joinRequest == null) {
            pendingInviteLink = null
            invitePreviewRequest = null
            chatActions(); show("Csatlakozás megszakítva.")
            return
        }
        if (pendingPublicChatId != null && publicJoinRequest == null) {
            pendingPublicChatId = null
            chatActions(); show("Csatlakozás megszakítva.")
            return
        }
        if (voiceRecorder.isRecording) {
            voiceRecorder.cancel()
            messageActions()
            show("A hangfelvételt elvetettem.")
            return
        }
        if (pendingMedia != null) {
            pendingMedia?.file?.delete()
            pendingMedia = null
            messageActions()
            show("A küldést elvetettem.")
            return
        }
        if (!browsing && navigation.page !is TelegramPage.ReviewSend) {
            browsing = true
            show(navigation.speakCurrent())
            markFocusedRead()
            return
        }
        when (navigation.page) {
            is TelegramPage.ReviewSend -> { navigation.back(); messageActions(); show("Küldés megszakítva.") }
            is TelegramPage.Messages -> { navigation.back(); chatActions(); show(navigation.speakCurrent()) }
            TelegramPage.Chats -> {
                returnToLauncher()
            }
        }
    }

    private fun returnToLauncher() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }

    private fun send(request: String): Boolean {
        if (request.isEmpty()) return true
        if (TelegramSession.send(request)) return true
        show("Nincs Telegram-kapcsolat. A művelet nem ment el.")
        return false
    }

    private fun show(message: String) {
        display.text = message
        try { tts.speak(message) } catch (_: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        val chatId = (navigation.page as? TelegramPage.Messages)?.chatId
        if (chatId != null && authStep == TelegramLoginStep.Ready) {
            historyChatId = chatId
            TelegramSession.send(TdlibJsonProtocol.chatHistory(chatId))
            if (browsing) markFocusedRead()
        }
    }

    override fun onPause() {
        foreground = false
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        stopVoicePlayback(false)
        TelegramSession.remove(sessionObserver)
        try { voiceInput.destroy() } catch (_: Exception) { }
        voiceRecorder.cancel()
        try { tts.stop(); tts.shutdown() } catch (_: Exception) { }
        try { sounds.release() } catch (_: Exception) { }
        super.onDestroy()
    }

    companion object {
        private const val MAX_UPLOAD_BYTES = 200L * 1024L * 1024L
        fun start(context: Context) {
            context.startActivity(Intent(context, TelegramActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
    }
}
