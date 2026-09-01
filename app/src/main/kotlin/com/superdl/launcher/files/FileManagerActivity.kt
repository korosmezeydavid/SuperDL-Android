package com.superdl.launcher.files

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
import com.superdl.launcher.voice.VoiceInput
import java.io.File

/**
 * Vak-barát fájlkezelő, végig a megszokott fa-logikával:
 *   fel/le: lépkedés, jobbra: művelet-menü, balra: vissza.
 *
 * KÉPERNYŐK (mindig pontosan egy aktív):
 *  BROWSE – mappák és fájlok listája (mappák elöl, a végén a "Menü" sor)
 *  ACTION – EGY elemre vonatkozó műveletek (fájlra ÉS mappára is)
 *  MENU   – az egész mappára vonatkozó csoportos műveletek
 *  SELECT – kijelölési mód: jobbra kijelöl, balra kilép
 *  OPS    – mit csináljunk a kijelöltekkel (törlés, áthelyezés, másolás)
 *  DEST   – célmappa választása, kétlépcsős megerősítéssel
 *
 * MIÉRT KAP A MAPPA IS MŰVELET-MENÜT: eddig a jobbra söprés azonnal belépett
 * a mappába, tehát magát a mappát nem lehetett törölni, átnevezni, mozgatni —
 * csak a benne lévő fájlokat, egyesével. A menü ELSŐ pontja a belépés, tehát
 * a megszokott mozdulat (jobbra, jobbra) ugyanoda visz.
 *
 * MIÉRT KELL A CSOPORTOS MŰVELET: hatszáz képet egyesével törölni nem
 * kényelmetlen, hanem lehetetlen.
 */
class FileManagerActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvHint: TextView
    private lateinit var tts: TtsManager
    private lateinit var sounds: SoundFeedback
    private lateinit var gestureListener: SwipeGestureListener
    private lateinit var voiceInput: VoiceInput

    private enum class Screen { BROWSE, ACTION, MENU, SELECT, OPS, DEST }

    private var screen = Screen.BROWSE

    private var currentDir: File = FileManagerHelper.rootDir()
    private var items: List<FileItem> = emptyList()
    private var index = 0

    // Egy elem művelet-menüje
    private var actionIndex = 0
    private var actionTarget: FileItem? = null

    // Mappa-menü (csoportos műveletek belépője)
    private var menuIndex = 0

    // Kijelölés
    private val selected = LinkedHashSet<String>()
    private var opsIndex = 0

    // Vágólap: amit mozgatunk vagy másolunk (egyetlen elem is ide kerül)
    private var pending: List<File> = emptyList()
    private var pendingIsMove = false

    // Célmappa-választó
    private var destFolders: List<File> = emptyList()
    private var destIndex = 0
    private var destConfirm: File? = null

    // Megerősítések
    private var pendingDelete: FileItem? = null
    private var pendingBatchDelete = false
    private var pendingWipeDir: File? = null
    private var pendingStorageRequest = false

    // Keresés
    private var inSearchResults = false

    private enum class FileAction(val label: String) {
        ENTER("Belépés a mappába"),
        OPEN("Megnyitás"),
        READ_TEXT("Felolvasás"),
        ZIP_INFO("Mi van a csomagban"),
        UNZIP("Kicsomagolás"),
        ZIP("Tömörítés"),
        DETAILS("Adatok"),
        RENAME("Átnevezés"),
        COPY("Másolás máshova"),
        MOVE("Áthelyezés máshova"),
        SEARCH("Keresés ebben a mappában"),
        DELETE("Törlés")
    }

    /** Az egész mappára vonatkozó műveletek — a "Menü" sorból nyílnak. */
    private enum class FolderMenuAction(val label: String) {
        SELECT_START("Kijelölés indítása"),
        SELECT_ALL("Összes kijelölése ebben a mappában"),
        WIPE("Mappa tartalmának törlése"),
        SEARCH("Keresés ebben a mappában"),
        INFO("Mappa adatai")
    }

    /** Amit a kijelölt elemekkel lehet csinálni. */
    private enum class BatchOp(val label: String) {
        DELETE("Kijelöltek törlése"),
        MOVE("Kijelöltek áthelyezése"),
        COPY("Kijelöltek másolása"),
        CANCEL("Kijelölés elvetése")
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
        voiceInput = VoiceInput(this)

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

        val startPath = intent.getStringExtra(EXTRA_START_DIR)
        currentDir = if (!startPath.isNullOrBlank()) File(startPath) else FileManagerHelper.rootDir()

        tts.runWhenReady {
            tts.speak(
                "Fájlkezelő. ${FileManagerHelper.freeSpaceText(this)} " +
                    "Söpörj fel-le a fájlok között, jobbra a műveletekhez, balra vissza. " +
                    "A lista végén a Menü: onnan lehet többet egyszerre kijelölni."
            )
            loadDir(currentDir, announce = true)
        }
    }

    // ==================== Böngészés ====================

    private fun loadDir(dir: File, announce: Boolean = true) {
        currentDir = dir
        items = FileManagerHelper.listDir(dir, includeMenu = true)
        index = 0
        screen = Screen.BROWSE
        selected.clear()
        inSearchResults = false
        updateDisplay()
        if (announce) {
            val name = if (dir == FileManagerHelper.rootDir()) "Fő tárhely" else dir.name
            val count = items.count { it.isReal }
            if (count == 0) {
                tts.speak("$name. Ez a mappa üres. Balra söprés a visszalépéshez.")
            } else {
                tts.speak("$name, $count elem. ${items[0].speakPreview(this)}")
            }
        }
    }

    /** Frissítés a jelenlegi mappára úgy, hogy a kijelölés megmarad. */
    private fun refreshKeepingSelection() {
        items = FileManagerHelper.listDir(currentDir, includeMenu = true)
        // Ami időközben eltűnt, azt a kijelölésből is kivesszük.
        val living = items.filter { it.isReal }.map { it.file.absolutePath }.toSet()
        selected.retainAll(living)
        if (index >= items.size) index = (items.size - 1).coerceAtLeast(0)
        updateDisplay()
    }

    private fun navigate(delta: Int) {
        // Bármelyik függő kérdés elévül, ha ellépsz róla — vakon ez a legfőbb
        // védelem a véletlen törlés és a rossz helyre másolás ellen.
        if (dropPendingOnMove()) return

        when (screen) {
            Screen.ACTION -> {
                val actions = availableActions()
                if (actions.isEmpty()) return
                actionIndex = (actionIndex + delta + actions.size) % actions.size
                updateDisplay()
                tts.speak(actions[actionIndex].label)
            }
            Screen.MENU -> {
                val actions = availableMenuActions()
                menuIndex = (menuIndex + delta + actions.size) % actions.size
                updateDisplay()
                tts.speak(actions[menuIndex].label)
            }
            Screen.OPS -> {
                val ops = BatchOp.entries
                opsIndex = (opsIndex + delta + ops.size) % ops.size
                updateDisplay()
                tts.speak(ops[opsIndex].label)
            }
            Screen.DEST -> {
                if (destFolders.isEmpty()) return
                destIndex = (destIndex + delta + destFolders.size) % destFolders.size
                updateDisplay()
                tts.speak(FileManagerHelper.speakFolder(destFolders[destIndex]))
            }
            Screen.SELECT -> navigateSelect(delta)
            Screen.BROWSE -> {
                if (items.isEmpty()) {
                    tts.speak("Ez a mappa üres.")
                    return
                }
                index = (index + delta + items.size) % items.size
                updateDisplay()
                tts.speak(items[index].speakPreview(this))
            }
        }
    }

    /**
     * Egy fel-le söprés minden függő kérdést elvet. Igazat ad vissza, ha
     * ténylegesen volt mit elvetni — akkor a söprés MÁST nem csinál.
     */
    private fun dropPendingOnMove(): Boolean {
        if (destConfirm != null) {
            destConfirm = null
            updateDisplay()
            tts.speak("Rendben, nem oda. Lépkedj tovább a mappák között.")
            return true
        }
        if (pendingDelete != null || pendingBatchDelete || pendingWipeDir != null) {
            pendingDelete = null
            pendingBatchDelete = false
            pendingWipeDir = null
            updateDisplay()
            tts.speak("Törlés megszakítva.")
            return true
        }
        pendingStorageRequest = false
        return false
    }

    private fun activate() {
        // ENGEDÉLY-FELAJÁNLÁS: ha az imént mondtuk el, mi hiányzik, a jobbra
        // söprés megnyitja a beállítást. Ez mindent megelőz — enélkül a
        // felhasználó a magyarázat után újra ugyanabba a falba futna.
        if (pendingStorageRequest) {
            pendingStorageRequest = false
            if (StorageAccess.openSettings(this)) {
                tts.speak(
                    "Megnyitottam a beállítást. Kapcsold be az összes fájl kezelését, " +
                        "aztán söpörj balra a visszatéréshez."
                )
            } else {
                tts.speak(
                    "A beállítást nem sikerült megnyitni. Kézzel így találod meg: " +
                        "Beállítások, Alkalmazások, Super DL, Összes fájl kezelése."
                )
            }
            return
        }
        // Megerősítésre váró törlések.
        pendingDelete?.let { doSingleDelete(it); return }
        if (pendingBatchDelete) { doBatchDelete(); return }
        pendingWipeDir?.let { doWipeDir(it); return }
        destConfirm?.let { doPasteInto(it); return }

        when (screen) {
            Screen.ACTION -> availableActions().getOrNull(actionIndex)?.let { performAction(it) }
            Screen.MENU -> availableMenuActions().getOrNull(menuIndex)?.let { performMenuAction(it) }
            Screen.OPS -> performBatchOp(BatchOp.entries[opsIndex])
            Screen.DEST -> askDestination()
            Screen.SELECT -> toggleSelection()
            Screen.BROWSE -> {
                if (items.isEmpty()) return
                val item = items[index]
                when {
                    item.isParent -> loadDir(item.file)
                    item.isMenu -> enterFolderMenu()
                    else -> enterActionMode(item)
                }
            }
        }
    }

    private fun goBack() {
        // Függő kérdésnél a balra söprés a mégse.
        if (destConfirm != null) {
            destConfirm = null
            updateDisplay()
            tts.speak("Mégse.")
            return
        }
        if (pendingDelete != null || pendingBatchDelete || pendingWipeDir != null) {
            pendingDelete = null
            pendingBatchDelete = false
            pendingWipeDir = null
            updateDisplay()
            tts.speak("Törlés megszakítva.")
            return
        }
        when (screen) {
            Screen.ACTION, Screen.MENU -> {
                screen = Screen.BROWSE
                actionTarget = null
                updateDisplay()
                tts.speak("Vissza a fájlokhoz. ${items.getOrNull(index)?.speakPreview(this) ?: ""}")
            }
            Screen.SELECT -> finishSelection()
            Screen.OPS -> {
                // Vissza a kijelöléshez: a munka ne vesszen el egy félresöprés miatt.
                screen = Screen.SELECT
                updateDisplay()
                tts.speak("Vissza a kijelöléshez. ${selected.size} elem kijelölve.")
            }
            Screen.DEST -> {
                screen = Screen.OPS
                pending = emptyList()
                updateDisplay()
                tts.speak("Célmappa választás megszakítva.")
            }
            Screen.BROWSE -> {
                if (inSearchResults) {
                    inSearchResults = false
                    loadDir(currentDir)
                    tts.speak("Vissza a mappához.")
                    return
                }
                val parent = currentDir.parentFile
                if (currentDir == FileManagerHelper.rootDir() || parent == null || !parent.canRead()) {
                    tts.speak("Fájlkezelő bezárva.")
                    finish()
                    return
                }
                loadDir(parent)
            }
        }
    }

    // ==================== Egy elem művelet-menüje ====================

    private fun enterActionMode(item: FileItem) {
        actionTarget = item
        screen = Screen.ACTION
        actionIndex = 0
        updateDisplay()
        val actions = availableActions()
        val what = if (item.isDirectory) "mappa" else "fájl"
        tts.speak("${item.name}, $what. Műveletek. ${actions.firstOrNull()?.label ?: ""}")
    }

    /** A művelet-lista a helyzethez igazodik. */
    private fun availableActions(): List<FileAction> {
        val target = actionTarget ?: return emptyList()
        val list = mutableListOf<FileAction>()
        if (target.isDirectory) {
            // A BELÉPÉS AZ ELSŐ: aki eddig jobbra söpört a mappán, az most
            // jobbra-jobbra söpör, és ugyanott köt ki.
            list.add(FileAction.ENTER)
        } else {
            // TÖMÖRÍTETT FÁJLNÁL a megnyitás értelmetlen lenne — a
            // kicsomagolás AZ a művelet, amit a felhasználó akar.
            if (ZipHelper.isZip(target.file)) {
                list.add(FileAction.UNZIP)
                list.add(FileAction.ZIP_INFO)
            } else if (ZipHelper.isUnsupportedArchive(target.file)) {
                list.add(FileAction.ZIP_INFO)
            } else {
                list.add(FileAction.OPEN)
                if (FileKind.of(target.file) == FileKind.TEXT) list.add(FileAction.READ_TEXT)
            }
        }
        if (!ZipHelper.isZip(target.file)) list.add(FileAction.ZIP)
        list.add(FileAction.DETAILS)
        list.add(FileAction.RENAME)
        list.add(FileAction.COPY)
        list.add(FileAction.MOVE)
        if (!inSearchResults) list.add(FileAction.SEARCH)
        list.add(FileAction.DELETE)
        return list
    }

    /**
     * ÍRÁSI MŰVELET ELŐTT: megvan-e a teljes fájlhozzáférés?
     *
     * MIÉRT ITT, ÉS NEM A MŰVELET UTÁN: a hiba enélkül NÉMA. A törlés lefut,
     * a fájl marad, és a felhasználó nem tudja, mi történt. Így viszont a
     * program MEGMONDJA, mi hiányzik és mi a teendő.
     */
    private fun requireStorageAccess(): Boolean {
        if (StorageAccess.hasFullAccess()) return true
        pendingStorageRequest = true
        screen = Screen.BROWSE
        updateDisplay()
        sounds.play(SoundType.ACTION_ERROR)
        tts.speak(StorageAccess.EXPLANATION)
        return false
    }

    private fun performAction(action: FileAction) {
        val target = actionTarget ?: return
        val writes = action in setOf(
            FileAction.DELETE, FileAction.MOVE, FileAction.ZIP, FileAction.UNZIP,
            FileAction.RENAME
        )
        if (writes && !requireStorageAccess()) return

        when (action) {
            FileAction.ENTER -> loadDir(target.file)
            FileAction.OPEN -> openFile(target.file)
            FileAction.READ_TEXT -> readTextFile(target.file)
            FileAction.ZIP_INFO -> tts.speak(ZipHelper.describe(target.file))
            FileAction.UNZIP -> runZipTask(kicsomagol = true, file = target.file)
            FileAction.ZIP -> runZipTask(kicsomagol = false, file = target.file)
            FileAction.DETAILS -> tts.speak(target.speakDetails(this))
            FileAction.RENAME -> startRename(target)
            FileAction.COPY -> startDestinationPick(listOf(target.file), move = false)
            FileAction.MOVE -> startDestinationPick(listOf(target.file), move = true)
            FileAction.SEARCH -> startSearch()
            FileAction.DELETE -> {
                pendingDelete = target
                val warn = if (target.isDirectory) {
                    "Ez a mappa a teljes tartalmával együtt törlődik. "
                } else {
                    ""
                }
                tts.speak(
                    "Biztosan törlöd? ${target.name}. $warn" +
                        "Söpörj jobbra a törléshez, balra a mégsehez."
                )
            }
        }
    }

    private fun doSingleDelete(item: FileItem) {
        pendingDelete = null
        val path = item.file.absolutePath
        val ok = FileManagerHelper.delete(item.file)
        screen = Screen.BROWSE
        actionTarget = null
        if (ok) {
            scanPaths(listOf(path))
            sounds.play(SoundType.ACTION_OK)
            tts.speak("${item.name} törölve.")
            loadDir(currentDir, announce = false)
        } else {
            sounds.play(SoundType.ACTION_ERROR)
            tts.speak("A törlés nem sikerült. Lehet, hogy nincs jogosultság ehhez a fájlhoz.")
        }
        updateDisplay()
    }

    // ==================== Mappa-menü (csoportos műveletek) ====================

    private fun enterFolderMenu() {
        screen = Screen.MENU
        menuIndex = 0
        updateDisplay()
        val dirName = if (currentDir == FileManagerHelper.rootDir()) "Fő tárhely" else currentDir.name
        tts.speak(
            "Menü: $dirName. Csoportos műveletek. " +
                "${availableMenuActions().first().label}"
        )
    }

    private fun availableMenuActions(): List<FolderMenuAction> = FolderMenuAction.entries

    private fun performMenuAction(action: FolderMenuAction) {
        when (action) {
            FolderMenuAction.SELECT_START -> startSelection(selectAll = false)
            FolderMenuAction.SELECT_ALL -> startSelection(selectAll = true)
            FolderMenuAction.WIPE -> {
                if (!requireStorageAccess()) return
                val count = items.count { it.isReal }
                if (count == 0) {
                    tts.speak("Ez a mappa már üres.")
                    return
                }
                pendingWipeDir = currentDir
                tts.speak(
                    "Biztosan törlöd a mappa teljes tartalmát? " +
                        "${currentDir.name}, $count elem, az almappákkal együtt. " +
                        "Ez nem visszavonható. Söpörj jobbra a törléshez, balra a mégsehez."
                )
            }
            FolderMenuAction.SEARCH -> startSearch()
            FolderMenuAction.INFO -> {
                val count = items.count { it.isReal }
                val size = android.text.format.Formatter.formatShortFileSize(
                    this, FileManagerHelper.folderSize(currentDir)
                )
                tts.speak(
                    "${FileManagerHelper.speakFolder(currentDir)}. $count elem. " +
                        "Teljes méret: $size. ${FileManagerHelper.freeSpaceText(this)}"
                )
            }
        }
    }

    private fun doWipeDir(dir: File) {
        pendingWipeDir = null
        val victims = items.filter { it.isReal }.map { it.file }
        var ok = 0
        var failed = 0
        val paths = mutableListOf<String>()
        victims.forEach { f ->
            paths.add(f.absolutePath)
            if (FileManagerHelper.delete(f)) ok++ else failed++
        }
        scanPaths(paths)
        screen = Screen.BROWSE
        if (failed == 0) {
            sounds.play(SoundType.ACTION_OK)
            tts.speak("$ok elem törölve. A mappa üres.")
        } else {
            sounds.play(SoundType.ACTION_ERROR)
            tts.speak("$ok elem törölve, $failed nem sikerült. Azokhoz nincs jogosultság.")
        }
        loadDir(currentDir, announce = false)
    }

    // ==================== Kijelölési mód ====================

    private fun startSelection(selectAll: Boolean) {
        val real = items.filter { it.isReal }
        if (real.isEmpty()) {
            tts.speak("Ebben a mappában nincs mit kijelölni.")
            return
        }
        selected.clear()
        if (selectAll) real.forEach { selected.add(it.file.absolutePath) }
        screen = Screen.SELECT
        // Az első VALÓDI elemre állunk (a "vissza a szülőbe" sort átugorjuk).
        index = items.indexOfFirst { it.isReal }.coerceAtLeast(0)
        updateDisplay()
        if (selectAll) {
            tts.speak(
                "Mind a ${selected.size} elem kijelölve. " +
                    "Jobbra söpréssel vehetsz vissza egyet-egyet, balra söpréssel jönnek a műveletek."
            )
        } else {
            tts.speak(
                "Kijelölés indítva. Söpörj fel-le a fájlok között, jobbra kijelölés, " +
                    "még egy jobbra visszavonja. Ha megvagy, söpörj balra: jönnek a műveletek. " +
                    "${speakSelectItem(items[index])}"
            )
        }
    }

    private fun navigateSelect(delta: Int) {
        val real = items.filter { it.isReal }
        if (real.isEmpty()) return
        var next = index
        // A navigációs sorokat (szülő, Menü) kijelölési módban átugorjuk.
        do {
            next = (next + delta + items.size) % items.size
        } while (!items[next].isReal)
        index = next
        updateDisplay()
        tts.speak(speakSelectItem(items[index]))
    }

    private fun speakSelectItem(item: FileItem): String {
        val mark = if (item.file.absolutePath in selected) "kijelölve. " else ""
        return "$mark${item.speakPreview(this)}"
    }

    private fun toggleSelection() {
        val item = items.getOrNull(index) ?: return
        if (!item.isReal) return
        val path = item.file.absolutePath
        if (selected.remove(path)) {
            sounds.play(SoundType.SWIPE_LEFT)
            tts.speak("Kijelölés visszavonva: ${item.name}. Összesen ${selected.size}.")
        } else {
            selected.add(path)
            sounds.play(SoundType.ACTION_OK)
            tts.speak("Kijelölve: ${item.name}. Összesen ${selected.size}.")
        }
        updateDisplay()
    }

    private fun finishSelection() {
        if (selected.isEmpty()) {
            screen = Screen.BROWSE
            updateDisplay()
            tts.speak("Nem jelöltél ki semmit. Vissza a fájlokhoz.")
            return
        }
        screen = Screen.OPS
        opsIndex = 0
        updateDisplay()
        tts.speak(
            "${selected.size} elem kijelölve. Mit csináljak velük? " +
                "Söpörj fel-le a műveletek között, jobbra indítás. " +
                "${BatchOp.entries[0].label}"
        )
    }

    private fun selectedFiles(): List<File> = selected.map { File(it) }.filter { it.exists() }

    private fun performBatchOp(op: BatchOp) {
        val files = selectedFiles()
        if (files.isEmpty() && op != BatchOp.CANCEL) {
            tts.speak("A kijelölt elemek már nincsenek meg.")
            loadDir(currentDir, announce = false)
            return
        }
        when (op) {
            BatchOp.CANCEL -> {
                selected.clear()
                screen = Screen.BROWSE
                updateDisplay()
                tts.speak("Kijelölés elvetve. Vissza a fájlokhoz.")
            }
            BatchOp.DELETE -> {
                if (!requireStorageAccess()) return
                pendingBatchDelete = true
                tts.speak(
                    "Biztosan törlöd? ${files.size} elem, az almappák teljes tartalmával együtt. " +
                        "Ez nem visszavonható. Söpörj jobbra a törléshez, balra a mégsehez."
                )
            }
            BatchOp.MOVE -> {
                if (!requireStorageAccess()) return
                startDestinationPick(files, move = true)
            }
            BatchOp.COPY -> {
                if (!requireStorageAccess()) return
                startDestinationPick(files, move = false)
            }
        }
    }

    private fun doBatchDelete() {
        pendingBatchDelete = false
        val files = selectedFiles()
        var ok = 0
        var failed = 0
        val paths = mutableListOf<String>()
        files.forEach { f ->
            paths.add(f.absolutePath)
            if (FileManagerHelper.delete(f)) ok++ else failed++
        }
        scanPaths(paths)
        selected.clear()
        screen = Screen.BROWSE
        if (failed == 0) {
            sounds.play(SoundType.ACTION_OK)
            tts.speak("$ok elem törölve.")
        } else {
            sounds.play(SoundType.ACTION_ERROR)
            tts.speak("$ok elem törölve, $failed nem sikerült.")
        }
        loadDir(currentDir, announce = false)
    }

    // ==================== Célmappa választása ====================

    /**
     * A CÉLMAPPA-VÁLASZTÓ. Lapos lista: minden sor KIMONDJA a teljes helyét,
     * tehát nem lehet félreérteni, hova kerülnek a fájlok.
     *
     * A BIZTOSÍTÉK: egy jobbra söprés csak MEGKÉRDEZI ("Ide akarod
     * beilleszteni? Csengőhangok"), és csak a MÁSODIK jobbra söprés hajtja
     * végre. Egy fel- vagy le-söprés elveti a kérdést.
     */
    private fun startDestinationPick(files: List<File>, move: Boolean) {
        pending = files
        pendingIsMove = move
        destConfirm = null
        tts.speak("Célmappa keresése. Egy pillanat.")
        Thread {
            val folders = FileManagerHelper.destinationFolders(currentDir)
            runOnUiThread {
                if (folders.isEmpty()) {
                    tts.speak("Nem találtam célmappát.")
                    return@runOnUiThread
                }
                destFolders = folders
                destIndex = 0
                screen = Screen.DEST
                updateDisplay()
                val what = if (move) "áthelyezés" else "másolás"
                tts.speak(
                    "${pending.size} elem $what. Hova tegyem? " +
                        "Söpörj fel-le a mappák között, jobbra a kérdéshez. " +
                        "${FileManagerHelper.speakFolder(folders[0])}"
                )
            }
        }.start()
    }

    private fun askDestination() {
        val dir = destFolders.getOrNull(destIndex) ?: return
        destConfirm = dir
        updateDisplay()
        val what = if (pendingIsMove) "áthelyezzem" else "másoljam"
        tts.speak(
            "Ide $what? ${FileManagerHelper.speakFolder(dir)}. " +
                "Söpörj újra jobbra az igenhez. Fel vagy le söprés: mégse."
        )
    }

    private fun doPasteInto(dir: File) {
        destConfirm = null
        val files = pending
        if (files.isEmpty()) {
            screen = Screen.BROWSE
            updateDisplay()
            return
        }
        val what = if (pendingIsMove) "Áthelyezés" else "Másolás"
        tts.speak("$what folyamatban. ${files.size} elem. Egy pillanat.")
        Thread {
            var ok = 0
            var failed = 0
            val paths = mutableListOf<String>()
            files.forEach { f ->
                // Önmagába nem mozgatunk: az a fájl elvesztésével járna.
                val inside = try {
                    dir.canonicalPath == f.canonicalPath ||
                        dir.canonicalPath.startsWith(f.canonicalPath + File.separator)
                } catch (_: Exception) {
                    false
                }
                if (inside) {
                    failed++
                    return@forEach
                }
                val done = if (pendingIsMove) {
                    FileManagerHelper.moveTo(f, dir)
                } else {
                    FileManagerHelper.copyTo(f, dir)
                }
                if (done) {
                    ok++
                    paths.add(f.absolutePath)
                    paths.add(File(dir, f.name).absolutePath)
                } else {
                    failed++
                }
            }
            runOnUiThread {
                scanPaths(paths)
                pending = emptyList()
                selected.clear()
                screen = Screen.BROWSE
                if (failed == 0) {
                    sounds.play(SoundType.ACTION_OK)
                    tts.speak("$ok elem ${if (pendingIsMove) "áthelyezve" else "másolva"} ide: ${FileManagerHelper.speakFolder(dir)}.")
                } else {
                    sounds.play(SoundType.ACTION_ERROR)
                    tts.speak("$ok sikerült, $failed nem. A sikertelenek a helyükön maradtak.")
                }
                loadDir(currentDir, announce = false)
            }
        }.start()
    }

    /** A médiatár értesítése, hogy a törölt vagy új fájlokról tudjon. */
    private fun scanPaths(paths: List<String>) {
        if (paths.isEmpty()) return
        try {
            android.media.MediaScannerConnection.scanFile(
                this, paths.toTypedArray(), null, null
            )
        } catch (_: Exception) {
        }
    }

    // ==================== Átnevezés ====================

    /**
     * ÁTNEVEZÉS DIKTÁLÁSSAL.
     *
     * A KITERJESZTÉST MEGTARTJUK. Ha valaki a "nyaralas.jpg" fájlt átnevezi
     * "tengerpart"-ra, akkor "tengerpart.jpg" lesz belőle — nem "tengerpart".
     * Kiterjesztés nélkül a telefon nem tudná, mi az a fájl, és a kép többé
     * nem nyílna meg. Vakon ez észrevehetetlen hiba lenne.
     *
     * MAPPÁNÁL nincs kiterjesztés, ott a diktált név a teljes név.
     */
    private fun startRename(target: FileItem) {
        screen = Screen.BROWSE
        updateDisplay()
        val what = if (target.isDirectory) "mappa" else "fájl"
        voiceInput.listen(
            prompt = "Mi legyen az új neve? Mostani neve: ${target.name}. " +
                if (target.isDirectory) "Mondd az új $what nevet."
                else "A kiterjesztést nem kell mondanod, azt megtartom.",
            speakFirst = { text, onDone -> tts.speakThen(text, onDone) },
            onResult = { spoken -> applyRename(target, spoken) },
            onError = {
                tts.speak("Az átnevezés megszakítva.")
                updateDisplay()
            }
        )
    }

    private fun applyRename(target: FileItem, spoken: String) {
        val wanted = spoken.trim()
        if (wanted.isBlank()) {
            tts.speak("Nem értettem a nevet. Az átnevezés megszakítva.")
            updateDisplay()
            return
        }
        val oldExt = if (target.isDirectory) "" else target.file.extension
        // Ha a diktált név MÁR tartalmazza a helyes kiterjesztést, nem tesszük rá kétszer.
        val newName = when {
            oldExt.isBlank() -> wanted
            wanted.endsWith(".$oldExt", ignoreCase = true) -> wanted
            else -> "$wanted.$oldExt"
        }
        if (newName.equals(target.name, ignoreCase = true)) {
            tts.speak("Ez ugyanaz a név. Nem változott semmi.")
            updateDisplay()
            return
        }
        if (java.io.File(target.file.parentFile, newName).exists()) {
            tts.speak("Ilyen nevű elem már van ebben a mappában. Válassz másik nevet.")
            updateDisplay()
            return
        }
        val oldPath = target.file.absolutePath
        val ok = FileManagerHelper.rename(target.file, newName)
        if (ok) {
            scanPaths(listOf(oldPath, java.io.File(target.file.parentFile, newName).absolutePath))
            sounds.play(SoundType.ACTION_OK)
            tts.speak("Átnevezve: $newName.")
            loadDir(currentDir, announce = false)
        } else {
            sounds.play(SoundType.ACTION_ERROR)
            tts.speak(
                "Az átnevezés nem sikerült. Lehet, hogy a név nem megengedett " +
                    "karaktert tartalmaz, vagy nincs jogosultság ehhez a mappához."
            )
        }
        updateDisplay()
    }

    // ==================== Keresés ====================

    private fun startSearch() {
        screen = Screen.BROWSE
        val dirName = if (currentDir == FileManagerHelper.rootDir()) "a fő tárhelyen" else "itt: ${currentDir.name}"
        voiceInput.listen(
            prompt = "Mit keresel $dirName? Mondd a fájl nevét vagy egy részletét.",
            speakFirst = { text, onDone -> tts.speakThen(text, onDone) },
            onResult = { spoken ->
                val query = spoken.trim()
                if (query.isBlank()) {
                    tts.speak("Nem értettem. A keresés megszakítva.")
                    updateDisplay()
                    return@listen
                }
                runSearch(query)
            },
            onError = {
                tts.speak("A keresés megszakítva.")
                updateDisplay()
            }
        )
    }

    private fun runSearch(query: String) {
        tts.speak("Keresés: $query. Egy pillanat.")
        // A keresés lassú lehet nagy mappákban, ezért háttérszálon fut.
        Thread {
            val results = FileManagerHelper.search(currentDir, query)
            runOnUiThread {
                if (results.isEmpty()) {
                    inSearchResults = false
                    tts.speak("Nincs találat erre: $query. Visszatérek a mappához.")
                    updateDisplay()
                    return@runOnUiThread
                }
                inSearchResults = true
                items = results
                index = 0
                screen = Screen.BROWSE
                selected.clear()
                updateDisplay()
                tts.speak(
                    "${results.size} találat erre: $query. " +
                        "Söpörj fel-le a találatok között, balra a visszatéréshez. " +
                        "${results[0].speakPreview(this)}"
                )
            }
        }.start()
    }

    // ==================== Megnyitás ====================

    private fun openFile(file: File) {
        // SAJÁT LEJÁTSZÓ A HANG- ÉS VIDEÓFÁJLOKHOZ.
        //
        // MIÉRT: eddig minden fájlt ÁTADTUNK egy másik alkalmazásnak. Egy friss
        // telepítésű telefonon viszont gyakran nincs médialejátszó, és a
        // felhasználó annyit hallott, hogy „nincs hozzá alkalmazás" — vagyis a
        // saját zenéjét nem tudta meghallgatni a saját telefonján.
        val kind = FileKind.of(file)
        if (kind == FileKind.AUDIO || kind == FileKind.VIDEO) {
            try {
                startActivity(
                    Intent(this, com.superdl.launcher.media.LocalMediaPlayerActivity::class.java).apply {
                        putExtra(
                            com.superdl.launcher.media.LocalMediaPlayerActivity.EXTRA_PATH,
                            file.absolutePath
                        )
                        putExtra(
                            com.superdl.launcher.media.LocalMediaPlayerActivity.EXTRA_TITLE,
                            file.name
                        )
                    }
                )
                return
            } catch (_: Exception) {
            }
        }

        // KÖNYV: PDF, ePub, mobi és társaik a SAJÁT könyvolvasónkba mennek.
        if (kind == FileKind.DOCUMENT) {
            try {
                startActivity(
                    Intent(this, com.superdl.launcher.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(
                            com.superdl.launcher.MainActivity.EXTRA_OPEN_BOOK_PATH,
                            file.absolutePath
                        )
                    }
                )
                finish()
                return
            } catch (_: Exception) {
            }
        }

        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val mime = when (FileKind.of(file)) {
                FileKind.AUDIO -> "audio/*"
                FileKind.IMAGE -> "image/*"
                FileKind.VIDEO -> "video/*"
                FileKind.TEXT -> "text/plain"
                FileKind.DOCUMENT -> "application/pdf"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            tts.speak(
                "Ezt a fájlt nem tudom megnyitni: nincs hozzá alkalmazás a telefonon. " +
                    "A ${FileKind.of(file).hungarianName} megnyitásához külön program kell. " +
                    "Szövegfájlt a Felolvasás ponttal tudsz meghallgatni."
            )
        }
    }

    /**
     * TÖMÖRÍTÉS ÉS KICSOMAGOLÁS — háttérszálon, hangos visszajelzéssel.
     *
     * MIÉRT HÁTTÉRSZÁLON: egy nagy csomag másodpercekig tart. Ha a fő szálon
     * futna, a képernyőolvasó is megállna vele együtt — a felhasználó számára
     * a telefon egyszerűen "meghalna", és nem tudná, várjon-e vagy sem.
     */
    private fun runZipTask(kicsomagol: Boolean, file: File) {
        screen = Screen.BROWSE
        updateDisplay()
        tts.speak(
            if (kicsomagol) "Kicsomagolás indul: ${file.name}. Ez eltarthat egy ideig."
            else "Tömörítés indul: ${file.name}. Ez eltarthat egy ideig."
        )
        Thread {
            val result = if (kicsomagol) ZipHelper.extract(file) else ZipHelper.compress(file)
            runOnUiThread {
                if (result.ok) {
                    sounds.play(SoundType.ACTION_OK)
                    loadDir(currentDir, announce = false)
                    updateDisplay()
                } else {
                    sounds.play(SoundType.ACTION_ERROR)
                }
                tts.speak(result.message)
            }
        }.start()
    }

    private fun readTextFile(file: File) {
        try {
            if (file.length() > 200_000) {
                tts.speak("Ez a szövegfájl túl nagy a felolvasáshoz.")
                return
            }
            val text = file.readText().trim()
            if (text.isBlank()) {
                tts.speak("A fájl üres.")
                return
            }
            tts.speak(text.take(3000))
        } catch (_: Exception) {
            tts.speak("Nem sikerült elolvasni a fájlt.")
        }
    }

    // ==================== Kijelző ====================

    private fun updateDisplay() {
        val dirName = if (currentDir == FileManagerHelper.rootDir()) "Fő tárhely" else currentDir.name
        when (screen) {
            Screen.ACTION -> {
                val actions = availableActions()
                tvTitle.text = actions.getOrNull(actionIndex)?.label ?: ""
                tvPosition.text = actionTarget?.name ?: ""
                tvHint.text = "⬆⬇ műveletek  •  ➡ indít  •  ⬅ vissza"
            }
            Screen.MENU -> {
                val actions = availableMenuActions()
                tvTitle.text = actions.getOrNull(menuIndex)?.label ?: ""
                tvPosition.text = "Menü  •  $dirName"
                tvHint.text = "⬆⬇ műveletek  •  ➡ indít  •  ⬅ vissza"
            }
            Screen.SELECT -> {
                val item = items.getOrNull(index)
                val mark = if (item != null && item.file.absolutePath in selected) "✔ " else ""
                tvTitle.text = mark + (item?.name ?: "")
                tvPosition.text = "Kijelölés  •  ${selected.size} kijelölve"
                tvHint.text = "⬆⬇ elemek  •  ➡ kijelöl  •  ⬅ kész"
            }
            Screen.OPS -> {
                tvTitle.text = BatchOp.entries[opsIndex].label
                tvPosition.text = "Csoportos művelet  •  ${selected.size} elem"
                tvHint.text = "⬆⬇ műveletek  •  ➡ indít  •  ⬅ vissza"
            }
            Screen.DEST -> {
                val dir = destFolders.getOrNull(destIndex)
                tvTitle.text = dir?.name ?: ""
                tvPosition.text = if (destConfirm != null) {
                    "Ide tegyem?  •  ${dir?.name.orEmpty()}"
                } else {
                    "Célmappa  •  ${destIndex + 1} / ${destFolders.size}"
                }
                tvHint.text = if (destConfirm != null) {
                    "➡ igen  •  ⬆⬇ mégse"
                } else {
                    "⬆⬇ mappák  •  ➡ ide  •  ⬅ vissza"
                }
            }
            Screen.BROWSE -> {
                val item = items.getOrNull(index)
                tvTitle.text = item?.name ?: "Üres mappa"
                tvPosition.text = if (items.isEmpty()) dirName else "$dirName  •  ${index + 1} / ${items.size}"
                tvHint.text = "⬆⬇ fájlok  •  ➡ műveletek  •  ⬅ vissza"
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
        tts.shutdown()
        sounds.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_START_DIR = "start_dir"
    }
}
