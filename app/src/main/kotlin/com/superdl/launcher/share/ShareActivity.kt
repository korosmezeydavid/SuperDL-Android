package com.superdl.launcher.share

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.superdl.launcher.R
import com.superdl.launcher.feedback.SoundFeedback
import com.superdl.launcher.feedback.SoundType
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager
import java.io.File

/**
 * FÁJL- ÉS MAPPAMEGOSZTÁS.
 *
 * Ugyanaz a négy mozdulat, mint mindenütt: fel-le lépkedés, jobbra indít,
 * balra vissza.
 *
 * A HÁROM ÚT, és ebben a sorrendben NŐ A KOCKÁZAT — ezért is ez a sorrend:
 *
 *  1. Küldés másik programmal — a fájl a telefonon marad, a másik alkalmazás
 *     dolgozik vele (levél, csevegőprogram, Bluetooth).
 *  2. Feltöltés ideiglenes tárhelyre — a fájl FELKERÜL a netre, és mindenki
 *     letöltheti, akinek megvan a link. Ezt a program minden egyes feltöltés
 *     előtt KIMONDJA. Nem a súgóban: itt, hangosan, mielőtt elindul.
 *  3. (Készül) Küldés kóddal, gépről gépre.
 *
 * MIÉRT KÜLÖN ABLAK ÉS NEM A FŐMENÜ ÁLLAPOTGÉPE: a megosztásnak saját,
 * több lépcsős menete van (út, tárhely, megerősítés, haladás), és a fájl
 * onnan jön, ahol a fájlok vannak — a fájlkezelőből.
 */
class ShareActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "share_file_path"
        const val EXTRA_MODE = "share_mode"
        const val MODE_FILE = "file"
        const val MODE_HISTORY = "history"
        const val MODE_RECEIVE = "receive"

        /** Fájl fogadása kóddal — ehhez nem kell előre kiválasztott fájl. */
        fun openReceive(context: Context) {
            context.startActivity(
                Intent(context, ShareActivity::class.java).apply {
                    putExtra(EXTRA_MODE, MODE_RECEIVE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        fun shareFile(context: Context, file: File) {
            context.startActivity(
                Intent(context, ShareActivity::class.java).apply {
                    putExtra(EXTRA_MODE, MODE_FILE)
                    putExtra(EXTRA_FILE_PATH, file.absolutePath)
                }
            )
        }

        fun openHistory(context: Context) {
            context.startActivity(
                Intent(context, ShareActivity::class.java).apply {
                    putExtra(EXTRA_MODE, MODE_HISTORY)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private enum class Screen {
        ROUTE, PROVIDER, CONFIRM, PROGRESS, HISTORY, HIST_ACTIONS,
        P2P_CODE, P2P_PROGRESS, P2P_RECV
    }

    /**
     * A SORREND SZÁNDÉKOS: lefelé haladva NŐ a kockázat.
     * A rendszer megosztásánál a fájl a telefonon marad; a kóddal küldésnél
     * csak a másik fél kapja meg; a feltöltésnél bárki, akinek megvan a link.
     */
    private enum class Route(val label: String) {
        SYSTEM("Küldés másik programmal"),
        P2P("Küldés kóddal a másik gépre"),
        CLOUD("Feltöltés ideiglenes tárhelyre"),
        HISTORY("Megosztási előzmények")
    }

    private enum class CodeAction(val label: String) {
        REPEAT("Mondd újra a kódot"),
        SPELL("Kód betűzve"),
        CLIP("Kód a vágólapra"),
        SEND_SMS("Kód küldése üzenetben")
    }

    private enum class RecvAction(val label: String) {
        PASTE("Kód beillesztése a vágólapról"),
        TYPE("Kód beírása billentyűzettel")
    }

    private enum class HistAction(val label: String) {
        CLIP("Link a vágólapra"),
        SEND("Link megosztása"),
        SPELL("Link felolvasása betűzve"),
        DELETE_REMOTE("Törlés a tárhelyről"),
        DELETE_ROW("Sor törlése a listából"),
        PURGE("Lejártak eltakarítása")
    }

    private lateinit var tvTitle: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvHint: TextView
    private lateinit var tts: TtsManager
    private lateinit var sounds: SoundFeedback
    private lateinit var gestureListener: SwipeGestureListener

    private var screen = Screen.ROUTE
    private var target: File? = null

    private var routes: List<Route> = emptyList()
    private var routeIndex = 0

    private var providers: List<CloudTarget> = emptyList()
    private var providerIndex = 0

    private var history: List<ShareEntry> = emptyList()
    private var histIndex = 0
    private var histActions: List<HistAction> = emptyList()
    private var histActionIndex = 0

    private var percent = 0

    private var codeActions: List<CodeAction> = CodeAction.entries.toList()
    private var codeActionIndex = 0
    private var wormholeCode = ""

    private var recvActions: List<RecvAction> = RecvAction.entries.toList()
    private var recvActionIndex = 0

    /** A billentyűzetes kódbevitel válasza. */
    private val codeInput = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val code = result.data?.getStringExtra(WormholeCodeInputActivity.EXTRA_CODE)
        if (result.resultCode == android.app.Activity.RESULT_OK && !code.isNullOrBlank()) {
            startReceive(code)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_player)
        applyImmersive()

        tvTitle = findViewById(R.id.tvPlayerTitle)
        tvPosition = findViewById(R.id.tvPlayerPosition)
        tvStatus = findViewById(R.id.tvPlayerStatus)
        tvHint = findViewById(R.id.tvPlayerHint)
        tvStatus.text = ""

        tts = TtsManager(this)
        sounds = SoundFeedback(this)

        gestureListener = SwipeGestureListener(
            context = this,
            onSwipeUp = { sounds.play(SoundType.SWIPE_UP); navigate(-1) },
            onSwipeDown = { sounds.play(SoundType.SWIPE_DOWN); navigate(+1) },
            onSwipeRight = { sounds.play(SoundType.SWIPE_RIGHT); activate() },
            onSwipeLeft = { sounds.play(SoundType.SWIPE_LEFT); goBack() }
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })

        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        target = path?.let { File(it) }?.takeIf { it.isFile }
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FILE

        tts.runWhenReady {
            when {
                mode == MODE_RECEIVE -> startReceiveMenu()
                mode == MODE_HISTORY || target == null ->
                    startHistory(announceEmptyExit = mode == MODE_HISTORY)
                else -> startRoutes()
            }
        }
    }

    // ==================== Az utak ====================

    private fun startRoutes() {
        val f = target ?: return
        routes = Route.entries.toList()
        routeIndex = 0
        screen = Screen.ROUTE
        updateDisplay()
        tts.speak(
            "Megosztás: ${f.name}, ${CloudTargets.sizeText(f.length())}. " +
                "Söpörj fel-le a lehetőségek közt, jobbra a választáshoz. " +
                "${routes[0].label}."
        )
    }

    private fun onRouteChosen(route: Route) {
        when (route) {
            Route.SYSTEM -> shareWithSystemApp()
            Route.P2P -> startWormholeSend()
            Route.CLOUD -> startProviderPick()
            Route.HISTORY -> startHistory(announceEmptyExit = false)
        }
    }

    // ==================== Küldés kóddal (gépről gépre) ====================

    /**
     * A KÓD HÁROM ÚTON MEGY ÁT.
     *
     * A magic-wormhole kódja egy szám és két ANGOL szó — például
     * „7-crossover-clockwork". Ezt telefonba bemondani nehéz, ezért a
     * program egyszerre kimondja, BETŰZI, a vágólapra teszi, és felajánlja,
     * hogy üzenetben elküldi. A vágólap és az üzenet a legbiztosabb út.
     */
    private fun startWormholeSend() {
        val f = target ?: return
        if (WormholeService.isRunning) {
            sounds.play(SoundType.ACTION_ERROR)
            tts.speak("Már fut egy átvitel: ${WormholeService.busyWith}. Várd meg, vagy szakítsd meg.")
            return
        }
        wormholeCode = ""
        percent = 0
        codeActionIndex = 0
        screen = Screen.P2P_CODE
        updateDisplay()
        WormholeService.listener = wormholeListener
        WormholeService.send(this, f)
        tts.speak(
            "Küldés indul: ${f.name}. Mindjárt megkapom a kódot. A másik gépen " +
                "válaszd a fogadást, és írd be ugyanezt a kódot. A fájl végpontok " +
                "között titkosítva megy át."
        )
    }

    private fun onCodeAction(action: CodeAction) {
        if (wormholeCode.isBlank()) {
            tts.speak("Még nincs kód. Egy pillanat.")
            return
        }
        when (action) {
            CodeAction.REPEAT -> tts.speak("A kód: $wormholeCode")
            CodeAction.SPELL -> tts.speak("A kód betűzve: ${spellCode(wormholeCode)}")
            CodeAction.CLIP -> {
                copyToClipboard(wormholeCode)
                sounds.play(SoundType.ACTION_OK)
                tts.speak("A kód a vágólapon van.")
            }
            CodeAction.SEND_SMS -> sendCodeInMessage()
        }
    }

    private fun sendCodeInMessage() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "SuperDL fájlküldés. A fogadó kód: $wormholeCode")
        }
        try {
            tts.speak("Válaszd ki, mivel küldöd a kódot.")
            startActivity(Intent.createChooser(intent, "Kód küldése"))
        } catch (_: Exception) {
            copyToClipboard(wormholeCode)
            sounds.play(SoundType.ACTION_ERROR)
            tts.speak("Nincs alkalmazás a küldéshez, ezért a kódot a vágólapra tettem.")
        }
    }

    // ==================== Fogadás kóddal ====================

    private fun startReceiveMenu() {
        recvActionIndex = 0
        screen = Screen.P2P_RECV
        updateDisplay()
        tts.speak(
            "Fájl fogadása kóddal. Kérd el a küldőtől a kódot — a legegyszerűbb, " +
                "ha üzenetben átküldi, mert akkor csak beilleszted. " +
                "${recvActions[0].label}."
        )
    }

    private fun onRecvAction(action: RecvAction) {
        when (action) {
            RecvAction.PASTE -> {
                val text = readClipboard()
                if (text.isBlank()) {
                    sounds.play(SoundType.ACTION_ERROR)
                    tts.speak(
                        "A vágólap üres. Nyisd meg az üzenetet, másold ki a kódot, " +
                            "és gyere vissza ide — vagy írd be billentyűzettel."
                    )
                } else {
                    tts.speak("Beillesztett kód: ${spellCode(text)}")
                    startReceive(text)
                }
            }
            RecvAction.TYPE -> codeInput.launch(
                Intent(this, WormholeCodeInputActivity::class.java)
            )
        }
    }

    private fun startReceive(code: String) {
        // A kódban csak szám, betű és kötőjel lehet; a vágólapról könnyen
        // beragad egy pont vagy egy idézőjel az üzenet végéről.
        val tiszta = code.trim().trim('.', ',', '"', '\'', ' ')
        if (tiszta.isBlank()) {
            tts.speak("Ez a kód üres.")
            return
        }
        if (WormholeService.isRunning) {
            sounds.play(SoundType.ACTION_ERROR)
            tts.speak("Már fut egy átvitel: ${WormholeService.busyWith}. Várd meg, vagy szakítsd meg.")
            return
        }
        percent = 0
        screen = Screen.P2P_PROGRESS
        updateDisplay()
        WormholeService.listener = wormholeListener
        WormholeService.receive(this, tiszta)
        tts.speak(
            "Fogadás indul. A fájl a SuperDL, Fogadott mappába kerül. " +
                "Jobbra söprés megmondja, hol tart; balra söprés megszakítja."
        )
    }

    private fun readClipboard(): String = try {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
    } catch (_: Exception) {
        ""
    }

    /**
     * A KÓD BETŰZVE. A kötőjel NEM elhanyagolható: a wormhole kódjában az
     * választja el a részeket, és nélküle a kód nem működik.
     */
    private fun spellCode(code: String): String = code.map { c ->
        when (c) {
            '-' -> "kötőjel"
            ' ' -> "szóköz"
            in 'A'..'Z' -> "nagy $c"
            else -> c.toString()
        }
    }.joinToString(", ")

    private val wormholeListener = object : WormholeService.Companion.Listener {
        override fun onCode(code: String) {
            runOnUiThread {
                wormholeCode = code
                copyToClipboard(code)
                updateDisplay()
                sounds.play(SoundType.ACTION_OK)
                tts.speak(
                    "A kód: $code. Betűzve: ${spellCode(code)}. A kód a vágólapon is " +
                        "ott van. Söpörj fel-le, ha újra hallanád, betűzve kéred, " +
                        "vagy üzenetben küldenéd."
                )
            }
        }

        override fun onProgress(pct: Int) {
            runOnUiThread {
                val elozoTizes = percent / 10
                percent = pct
                if (screen == Screen.P2P_CODE) screen = Screen.P2P_PROGRESS
                updateDisplay()
                if (pct / 10 > elozoTizes && pct in 10..90) tts.speak("$pct százalék.")
            }
        }

        override fun onDone(ok: Boolean, message: String) {
            runOnUiThread {
                sounds.play(if (ok) SoundType.ACTION_OK else SoundType.ACTION_ERROR)
                wormholeCode = ""
                screen = if (target != null) Screen.ROUTE else Screen.P2P_RECV
                if (screen == Screen.ROUTE) routes = Route.entries.toList()
                updateDisplay()
                tts.speak(message)
            }
        }
    }

    /**
     * A HAGYOMÁNYOS ÚT: átadjuk a rendszernek, és a felhasználó választ
     * alkalmazást (levél, csevegő, Bluetooth).
     *
     * A `catch` NEM néma: ha nincs a telefonon egyetlen alkalmazás sem, ami
     * elfogadná, azt meg kell mondani, mert a felhasználó különben azt hinné,
     * a program romlott el.
     */
    private fun shareWithSystemApp() {
        val f = target ?: return
        val uri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        } catch (e: Exception) {
            sounds.play(SoundType.ACTION_ERROR)
            tts.speak(
                "Ezt a fájlt nem tudom átadni másik alkalmazásnak. " +
                    "Ez a program hibája, kérlek jelentsd. Ok: ${e.javaClass.simpleName}."
            )
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeOf(f)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, f.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            // ELŐBB a mondat, AZTÁN a lista — de a lista nem várja meg a
            // beszédet: a felhasználó haladjon a saját ütemében.
            tts.speak("Válaszd ki, melyik programmal küldöd.")
            startActivity(Intent.createChooser(intent, "Fájl megosztása"))
        } catch (_: Exception) {
            sounds.play(SoundType.ACTION_ERROR)
            tts.speak(
                "Nincs a telefonon olyan alkalmazás, ami elfogadná ezt a fájlt. " +
                    "Próbáld az ideiglenes tárhelyet: onnan linket kapsz, amit bárhova beilleszthetsz."
            )
        }
    }

    private fun mimeOf(file: File): String {
        val ext = file.extension.lowercase()
        return when (ext) {
            "mp3", "m4a", "aac", "wav", "ogg", "opus", "flac" -> "audio/*"
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "image/*"
            "mp4", "mkv", "avi", "mov", "webm" -> "video/*"
            "txt", "md", "csv", "log" -> "text/plain"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> "*/*"
        }
    }

    // ==================== Ideiglenes tárhely ====================

    /**
     * ELŐRE SZŰRÜNK MÉRETRE. Vakon a legrosszabb élmény az, ha valaki
     * végigvár egy háromperces feltöltést, és a végén derül ki, hogy a fájl
     * eleve túl nagy volt ehhez a tárhelyhez.
     */
    private fun startProviderPick() {
        val f = target ?: return
        providers = CloudTargets.eligible(f.length())
        providerIndex = 0
        if (providers.isEmpty()) {
            sounds.play(SoundType.ACTION_ERROR)
            tts.speak(
                "Ez a fájl minden ingyenes tárhelyhez túl nagy: " +
                    "${CloudTargets.sizeText(f.length())}. Tömörítsd, darabold, " +
                    "vagy küldd USB-vel, illetve a WiFi portállal."
            )
            return
        }
        screen = Screen.PROVIDER
        updateDisplay()
        tts.speak(
            "Tárhely választása ${providers.size} lehetőségből. " +
                "${providers[0].spokenName}. ${providers[0].note}"
        )
    }

    /**
     * A KÖTELEZŐ MONDAT.
     *
     * Ez az egyetlen hely az egész megosztásban, ahol a program megáll és
     * kérdez — és azért áll meg, mert innentől a fájl kikerül a kezünkből.
     * Aki ezt egyszer meghallgatta, az tudni fogja, mit NE töltsön fel.
     */
    private fun askConfirm() {
        val f = target ?: return
        val p = providers.getOrNull(providerIndex) ?: return
        screen = Screen.CONFIRM
        updateDisplay()
        val egyszeri = if (p.oneTimeDownload) {
            " Ezt a fájlt ráadásul csak EGYSZER lehet letölteni: aki elsőként megnyitja, " +
                "annál marad, másnak már nem fog működni."
        } else {
            ""
        }
        tts.speak(
            "${f.name}, ${CloudTargets.sizeText(f.length())}. Feltöltöm ide: ${p.spokenName}. " +
                "Figyelem: aki megkapja a linket, letöltheti a fájlt, mert nem lesz rajta jelszó. " +
                "Iratot, orvosi papírt, jelszót ne ezen az úton küldj. " +
                "${CloudTargets.lifetimeText(p.lifetimeHours)} él.$egyszeri " +
                "Söpörj jobbra a feltöltéshez, balra a mégsehez."
        )
    }

    private fun startUpload() {
        val f = target ?: return
        val p = providers.getOrNull(providerIndex) ?: return
        percent = 0
        screen = Screen.PROGRESS
        updateDisplay()
        ShareUploadService.listener = uploadListener
        ShareUploadService.start(this, f, p)
        tts.speak(
            "Feltöltés indul. Söpörj jobbra, ha tudni akarod, hol tart; " +
                "balra a megszakításhoz."
        )
    }

    /**
     * A HALADÁS BEMONDÁSA — tíz százalékonként.
     *
     * MIÉRT NEM SŰRŰBBEN: minden bemondás félbevágja az előzőt. Percenként
     * húsz „harminckét százalék" nem tájékoztatás, hanem zaj, és a
     * felhasználó kikapcsolná az egészet.
     */
    private val uploadListener = object : ShareUploadService.Companion.Listener {
        override fun onProgress(pct: Int) {
            runOnUiThread {
                val elozoTizes = percent / 10
                percent = pct
                updateDisplay()
                if (pct / 10 > elozoTizes && pct in 10..90) {
                    tts.speak("$pct százalék.")
                }
            }
        }

        override fun onDone(result: CloudUploader.Result, entry: ShareEntry?) {
            runOnUiThread {
                percent = if (result.ok) 100 else percent
                sounds.play(if (result.ok) SoundType.ACTION_OK else SoundType.ACTION_ERROR)
                if (result.ok && entry != null) {
                    history = ShareHistoryStore.all(this@ShareActivity)
                    histIndex = history.indexOfFirst { it.id == entry.id }.coerceAtLeast(0)
                    screen = Screen.HISTORY
                    updateDisplay()
                    tts.speak(
                        result.message +
                            " Itt vagy a megosztási előzményekben; jobbra söpörve " +
                            "újra vágólapra teheted vagy elküldheted a linket."
                    )
                } else {
                    screen = Screen.PROVIDER
                    updateDisplay()
                    tts.speak(result.message + " Válassz másik tárhelyet, vagy söpörj balra.")
                }
            }
        }
    }

    // ==================== Előzmények ====================

    private fun startHistory(announceEmptyExit: Boolean) {
        history = ShareHistoryStore.all(this)
        histIndex = 0
        screen = Screen.HISTORY
        updateDisplay()
        if (history.isEmpty()) {
            tts.speak(
                "Még nem töltöttél fel semmit ideiglenes tárhelyre. " +
                    "Ha feltöltesz, itt fogod látni, mit hova küldtél, és meddig él még."
            )
            if (announceEmptyExit) finishAfterSpeech()
            return
        }
        tts.speak(
            "Megosztási előzmények, ${history.size} tétel. Elöl az, ami hamarabb lejár. " +
                history[0].speakLine()
        )
    }

    private fun startHistActions() {
        val e = history.getOrNull(histIndex) ?: return
        histActions = buildList {
            add(HistAction.CLIP)
            add(HistAction.SEND)
            add(HistAction.SPELL)
            if (e.providerId == "zerox" || e.providerId == "filebin") add(HistAction.DELETE_REMOTE)
            add(HistAction.DELETE_ROW)
            if (history.any { it.expired }) add(HistAction.PURGE)
        }
        histActionIndex = 0
        screen = Screen.HIST_ACTIONS
        updateDisplay()
        tts.speak("${e.fileName}. Műveletek. ${histActions[0].label}")
    }

    private fun performHistAction(action: HistAction) {
        val e = history.getOrNull(histIndex) ?: return
        when (action) {
            HistAction.CLIP -> {
                copyToClipboard(e.url)
                sounds.play(SoundType.ACTION_OK)
                tts.speak("A link a vágólapon van. ${e.remainingText()}.")
            }
            HistAction.SEND -> sendLink(e)
            HistAction.SPELL -> tts.speak("A link betűzve: ${spellUrl(e.url)}")
            HistAction.DELETE_REMOTE -> deleteRemote(e)
            HistAction.DELETE_ROW -> {
                ShareHistoryStore.remove(this, e.id)
                history = ShareHistoryStore.all(this)
                histIndex = histIndex.coerceAtMost((history.size - 1).coerceAtLeast(0))
                screen = Screen.HISTORY
                updateDisplay()
                sounds.play(SoundType.ACTION_OK)
                tts.speak(
                    "Kivettem a listából. FIGYELEM: a fájl a tárhelyen maradt, " +
                        "amíg magától le nem jár."
                )
            }
            HistAction.PURGE -> {
                val n = ShareHistoryStore.purgeExpired(this)
                history = ShareHistoryStore.all(this)
                histIndex = 0
                screen = Screen.HISTORY
                updateDisplay()
                sounds.play(SoundType.ACTION_OK)
                tts.speak(if (n > 0) "$n lejárt tétel eltakarítva." else "Nem volt lejárt tétel.")
            }
        }
    }

    private fun sendLink(e: ShareEntry) {
        val lejarat = if (e.expired) "Ez a link már lejárt." else e.remainingText()
        val szoveg = "${e.fileName}: ${e.url}  ($lejarat)"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, e.fileName)
            putExtra(Intent.EXTRA_TEXT, szoveg)
        }
        try {
            tts.speak("Válaszd ki, mivel küldöd a linket.")
            startActivity(Intent.createChooser(intent, "Link megosztása"))
        } catch (_: Exception) {
            sounds.play(SoundType.ACTION_ERROR)
            copyToClipboard(e.url)
            tts.speak("Nincs alkalmazás a küldéshez, ezért a linket a vágólapra tettem.")
        }
    }

    /**
     * TÖRLÉS A TÁRHELYRŐL — valódi visszavonás.
     *
     * Hálózaton megy, ezért háttérszálon. A várakozást KIMONDJUK: néma
     * várakozás közben a felhasználó azt hinné, semmi nem történik.
     */
    private fun deleteRemote(e: ShareEntry) {
        tts.speak("Törlöm a tárhelyről. Egy pillanat.")
        Thread {
            val result = CloudUploader.deleteRemote(e)
            runOnUiThread {
                if (result.ok) {
                    ShareHistoryStore.remove(this, e.id)
                    history = ShareHistoryStore.all(this)
                    histIndex = histIndex.coerceAtMost((history.size - 1).coerceAtLeast(0))
                    screen = Screen.HISTORY
                    sounds.play(SoundType.ACTION_OK)
                } else {
                    sounds.play(SoundType.ACTION_ERROR)
                }
                updateDisplay()
                tts.speak(result.message)
            }
        }.start()
    }

    private fun copyToClipboard(text: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("SuperDL megosztás", text))
        } catch (_: Exception) {
        }
    }

    /**
     * A LINK BETŰZVE — hogy telefonba be lehessen mondani.
     *
     * Az írásjeleknek NEVE van, mert a beszélő ezeket vagy elhallgatja, vagy
     * érthetetlenül hadarja el; márpedig egy linkben egyetlen elveszett
     * jeltől nem működik semmi.
     */
    private fun spellUrl(url: String): String = url.map { c ->
        when (c) {
            '.' -> "pont"
            '/' -> "per jel"
            ':' -> "kettőspont"
            '-' -> "kötőjel"
            '_' -> "alsó vonás"
            '?' -> "kérdőjel"
            '=' -> "egyenlőségjel"
            '&' -> "és jel"
            '#' -> "kettős kereszt"
            '~' -> "hullámvonal"
            '%' -> "százalékjel"
            '+' -> "pluszjel"
            in 'A'..'Z' -> "nagy $c"
            else -> c.toString()
        }
    }.joinToString(", ")

    // ==================== Mozdulatok ====================

    private fun navigate(delta: Int) {
        when (screen) {
            Screen.ROUTE -> {
                if (routes.isEmpty()) return
                routeIndex = (routeIndex + delta + routes.size) % routes.size
                updateDisplay()
                tts.speak(routes[routeIndex].label)
            }
            Screen.PROVIDER -> {
                if (providers.isEmpty()) return
                providerIndex = (providerIndex + delta + providers.size) % providers.size
                updateDisplay()
                val p = providers[providerIndex]
                tts.speak("${p.spokenName}. ${p.note}")
            }
            Screen.CONFIRM -> {
                // Fel-le a megerősítésnél = MÉGSE. Aki bizonytalan, az
                // lépkedni kezd — az nem „igen".
                screen = Screen.PROVIDER
                updateDisplay()
                tts.speak("Mégse. Válassz tárhelyet.")
            }
            Screen.PROGRESS -> tts.speak("Feltöltés: $percent százalék.")
            Screen.HISTORY -> {
                if (history.isEmpty()) return
                histIndex = (histIndex + delta + history.size) % history.size
                updateDisplay()
                tts.speak(history[histIndex].speakLine())
            }
            Screen.HIST_ACTIONS -> {
                if (histActions.isEmpty()) return
                histActionIndex = (histActionIndex + delta + histActions.size) % histActions.size
                updateDisplay()
                tts.speak(histActions[histActionIndex].label)
            }
            Screen.P2P_CODE -> {
                codeActionIndex = (codeActionIndex + delta + codeActions.size) % codeActions.size
                updateDisplay()
                tts.speak(codeActions[codeActionIndex].label)
            }
            Screen.P2P_RECV -> {
                recvActionIndex = (recvActionIndex + delta + recvActions.size) % recvActions.size
                updateDisplay()
                tts.speak(recvActions[recvActionIndex].label)
            }
            Screen.P2P_PROGRESS -> tts.speak("Átvitel: $percent százalék.")
        }
    }

    private fun activate() {
        when (screen) {
            Screen.ROUTE -> routes.getOrNull(routeIndex)?.let { onRouteChosen(it) }
            Screen.PROVIDER -> askConfirm()
            Screen.CONFIRM -> startUpload()
            Screen.PROGRESS -> tts.speak("Feltöltés: $percent százalék.")
            Screen.HISTORY -> if (history.isNotEmpty()) startHistActions()
            Screen.HIST_ACTIONS -> histActions.getOrNull(histActionIndex)?.let { performHistAction(it) }
            Screen.P2P_CODE -> codeActions.getOrNull(codeActionIndex)?.let { onCodeAction(it) }
            Screen.P2P_RECV -> recvActions.getOrNull(recvActionIndex)?.let { onRecvAction(it) }
            Screen.P2P_PROGRESS -> tts.speak("Átvitel: $percent százalék.")
        }
    }

    private fun goBack() {
        when (screen) {
            Screen.ROUTE -> finishWith("Megosztás bezárva.")
            Screen.PROVIDER -> if (target != null) startRoutes() else finishWith("Bezárva.")
            Screen.CONFIRM -> {
                screen = Screen.PROVIDER
                updateDisplay()
                tts.speak("Mégse. Válassz tárhelyet.")
            }
            Screen.PROGRESS -> {
                ShareUploadService.cancel(this)
                screen = Screen.PROVIDER
                updateDisplay()
                sounds.play(SoundType.ACTION_ERROR)
                tts.speak("Feltöltés megszakítva.")
            }
            Screen.HISTORY -> if (target != null) startRoutes() else finishWith("Előzmények bezárva.")
            Screen.HIST_ACTIONS -> {
                screen = Screen.HISTORY
                updateDisplay()
                tts.speak(history.getOrNull(histIndex)?.speakLine() ?: "Előzmények.")
            }
            Screen.P2P_CODE, Screen.P2P_PROGRESS -> {
                WormholeService.cancel(this)
                sounds.play(SoundType.ACTION_ERROR)
                if (target != null) {
                    startRoutes()
                } else {
                    startReceiveMenu()
                }
                tts.speak("Az átvitelt megszakítottam.")
            }
            Screen.P2P_RECV -> finishWith("Fogadás bezárva.")
        }
    }

    private fun finishWith(message: String) {
        tts.speak(message)
        finishAfterSpeech()
    }

    /** Az ablak becsukása annyi késleltetéssel, hogy a mondat elhangozhasson. */
    private fun finishAfterSpeech() {
        tvTitle.postDelayed({ finish() }, 900L)
    }

    // ==================== Kijelző ====================

    private fun updateDisplay() {
        when (screen) {
            Screen.ROUTE -> {
                tvTitle.text = routes.getOrNull(routeIndex)?.label ?: ""
                tvPosition.text = target?.name ?: ""
                tvHint.text = "⬆⬇ lehetőségek  •  ➡ indít  •  ⬅ vissza"
            }
            Screen.PROVIDER -> {
                val p = providers.getOrNull(providerIndex)
                tvTitle.text = p?.name ?: ""
                tvPosition.text = "Tárhely  •  ${providerIndex + 1} / ${providers.size}"
                tvHint.text = "⬆⬇ tárhelyek  •  ➡ tovább  •  ⬅ vissza"
            }
            Screen.CONFIRM -> {
                tvTitle.text = "Feltöltsem?"
                tvPosition.text = providers.getOrNull(providerIndex)?.name ?: ""
                tvHint.text = "➡ igen  •  ⬅ mégse"
            }
            Screen.PROGRESS -> {
                tvTitle.text = "$percent %"
                tvPosition.text = target?.name ?: ""
                tvHint.text = "➡ hol tart  •  ⬅ megszakít"
            }
            Screen.HISTORY -> {
                val e = history.getOrNull(histIndex)
                tvTitle.text = e?.fileName ?: "Nincs előzmény"
                tvPosition.text = if (history.isEmpty()) {
                    "Megosztási előzmények"
                } else {
                    "${e?.remainingText()}  •  ${histIndex + 1} / ${history.size}"
                }
                tvHint.text = "⬆⬇ tételek  •  ➡ műveletek  •  ⬅ vissza"
            }
            Screen.HIST_ACTIONS -> {
                tvTitle.text = histActions.getOrNull(histActionIndex)?.label ?: ""
                tvPosition.text = history.getOrNull(histIndex)?.fileName ?: ""
                tvHint.text = "⬆⬇ műveletek  •  ➡ indít  •  ⬅ vissza"
            }
            Screen.P2P_CODE -> {
                tvTitle.text = if (wormholeCode.isBlank()) "Kód készül…" else wormholeCode
                tvPosition.text = codeActions.getOrNull(codeActionIndex)?.label ?: ""
                tvHint.text = "⬆⬇ műveletek  •  ➡ indít  •  ⬅ megszakít"
            }
            Screen.P2P_RECV -> {
                tvTitle.text = recvActions.getOrNull(recvActionIndex)?.label ?: ""
                tvPosition.text = "Fájl fogadása kóddal"
                tvHint.text = "⬆⬇ lehetőségek  •  ➡ indít  •  ⬅ vissza"
            }
            Screen.P2P_PROGRESS -> {
                tvTitle.text = "$percent %"
                tvPosition.text = target?.name ?: "Fogadás"
                tvHint.text = "➡ hol tart  •  ⬅ megszakít"
            }
        }
    }

    private fun applyImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestureListener.detector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun onDestroy() {
        // A feltöltés TOVÁBB MEGY az ablak nélkül is — csak minket ne
        // értesítsen többé, különben egy megszűnt ablakra beszélne.
        if (ShareUploadService.listener === uploadListener) ShareUploadService.listener = null
        if (WormholeService.listener === wormholeListener) WormholeService.listener = null
        tts.shutdown()
        sounds.release()
        super.onDestroy()
    }
}
