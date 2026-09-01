package com.superdl.launcher.screenreader

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.superdl.launcher.tts.TtsManager

/**
 * SUPERDL KÉPERNYŐOLVASÓ — kezdeti változat.
 *
 * Célja, hogy a megszokott NÉGY GESZTUSSAL a KÜLSŐ alkalmazások is kezelhetők
 * legyenek: fel-le lépkedés az elemeken felolvasással, jobbra aktiválás,
 * balra vissza.
 *
 * KÉT FONTOS ELV:
 *
 * 1. CSAK KÜLSŐ ALKALMAZÁSBAN AKTÍV.
 *    A SuperDL saját felületén kikapcsol, hogy ne ütközzön a launcher saját
 *    beszédével és gesztusaival. Ezt az előtérben lévő csomagnév alapján
 *    döntjük el, és a rendszer érintés-kezelését is ennek megfelelően kérjük
 *    vagy engedjük el.
 *
 * 2. BIZTONSÁGI RETESZ.
 *    Minden művelet a ScreenReaderPrefs kapcsolóitól függ. Hiba esetén a
 *    hibaszámláló nő, és sorozatos hiba után a szolgáltatás MAGÁTÓL leáll
 *    (vészleállítás), hogy semmiképp ne tegye használhatatlanná a telefont.
 */
class ScreenReaderService : AccessibilityService() {

    private var tts: TtsManager? = null
    private var sounds: ScreenReaderSounds? = null
    private var voiceInput: com.superdl.launcher.voice.VoiceInput? = null
    private var imageReader: ScreenReaderImageReader? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var nodes: List<AccessibilityNodeInfo> = emptyList()
    private var index = 0
    private var touchModeActive = false
    private var currentPackage: String? = null

    /** Frissíteni kell-e az elemlistát a következő gesztusnál. */
    private var nodesStale = false

    /** Szünetel-e az olvasó, mert billentyűzet van a képernyőn. */
    private var keyboardSuspended = false

    /** A tanuló módból való kilépéshez: az utolsó dupla koppintás ideje. */
    private var lastTrainingDoubleTapAt = 0L

    /**
     * A képernyő változásainak ütemét figyeli.
     * Ebből tudjuk, mikor "nyugtalan" egy felület — és mikor érdemes
     * csendesebb, takarékosabb módra váltani.
     */
    private val changeMonitor = ScreenChangeMonitor()

    /**
     * Szünetel-e az olvasó, mert a telefon ZÁROLVA van.
     *
     * MIÉRT KELL: a zárolási képernyőn ott vannak az értesítések — üzenetek,
     * hívások, levelek. Ha az olvasó ott dolgozna, a telefon HANGOSAN
     * felolvasná ezeket bárkinek, aki melletted áll, még MIELŐTT feloldottad
     * volna. Ez magánszféra-sértés.
     *
     * A PIN-képernyőt NEM mi kezeljük: arra ott a külön PIN segéd szolgáltatás.
     */
    private var lockSuspended = false

    /**
     * A legutóbb felolvasott elem címkéje. A képernyő frissülése után ez alapján
     * keressük meg újra, hol álltunk — enélkül a kurzor a lista elejére ugrana
     * minden apró változásnál (óra, töltésjelző).
     */
    private var lastLabel: String? = null

    // ── TUNING: navigációs mód és olvasási részletesség ─────────────────────

    /** MIT lépkedünk végig (minden / címsor / link / gomb / mező / szöveg). */
    private var mode = NavigationMode.ALL

    /** MEKKORA egységekben olvasunk (elem / mondat / szó / betű). */
    private var granularity = ReadingGranularity.ELEMENT

    /** A módra szűrt elemlista — ezen lépked a fel-le söprés. */
    private var filtered: List<AccessibilityNodeInfo> = emptyList()

    /** Az aktuális elem szövegének darabjai és a pozíció köztük. */
    private var segments: List<String> = emptyList()
    private var segmentIndex = 0

    /** A legutóbb kimondott szöveg — az ismétléshez. */
    private var lastSpoken: String = ""

    /** Fut-e a folyamatos olvasás. */
    private var continuousReading = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        live = this
        android.util.Log.i(ScreenReaderPrefs.TAG, "Kepernyoolvaso szolgaltatas csatlakozott")
        tts = try {
            TtsManager(this)
        } catch (e: Exception) {
            ScreenReaderPrefs.reportFailure(this, "TTS indítás: ${e.message}")
            null
        }
        sounds = try {
            ScreenReaderSounds(this)
        } catch (e: Exception) {
            android.util.Log.w(ScreenReaderPrefs.TAG, "Hangok: ${e.message}")
            null
        }
        voiceInput = try {
            com.superdl.launcher.voice.VoiceInput(this)
        } catch (_: Exception) {
            null
        }
        // TANULÓ MÓD: amint a menüből elindítják, AZONNAL átvesszük az
        // érintéseket. Enélkül a SuperDL saját launcher-gesztusai maradnának
        // érvényben, és a menü reagálna a söprésekre a tanulás helyett.
        TrainingState.onModeChanged = {
            handler.post {
                setTouchExploration(shouldRunInCurrentApp())
                if (!TrainingState.isActive) {
                    // Kilépés után friss beolvasás, hogy ne régi adatokon dolgozzunk.
                    nodesStale = true
                }
            }
        }
        imageReader = try {
            ScreenReaderImageReader(this)
        } catch (_: Exception) {
            null
        }
        // A tenyérrel való némításhoz figyeljük a közelség-érzékelőt.
        startProximityWatch()
        // Induláskor NEM kérünk érintés-kezelést: csak akkor, ha külső app jön.
        setTouchExploration(false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (ScreenReaderPrefs.isEmergencyDisabled(this)) {
            setTouchExploration(false)
            return
        }

        // ZÁROLÁS-ELLENŐRZÉS MINDEN ESEMÉNYNÉL.
        // Amíg a telefon zárolva van, az olvasó NEM dolgozik — így a zárolási
        // képernyőn lévő értesítéseket (üzenetek, hívások) nem olvassa fel
        // hangosan azelőtt, hogy feloldottad volna.
        updateLockSuspension()
        if (lockSuspended) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // A csomagnevet NEM csak az eseményből vesszük: kilépéskor az
                // gyakran hiányzik vagy a rendszeré, ezért maradt bekapcsolva az
                // olvasó. A ténylegesen előtérben lévő ablak a megbízható forrás.
                val pkg = resolveForegroundPackage(event)
                if (pkg != null && pkg != currentPackage) {
                    currentPackage = pkg
                    onForegroundAppChanged(pkg)
                }
                // BILLENTYŰZET-ÜTKÖZÉS ELLEN: ha épp beviteli billentyűzet van a
                // képernyőn, ÁTADJUK neki az érintéseket. Enélkül az olvasó
                // elfogná őket, és a mátrix billentyűzet csúsztatásos írása nem
                // működne — az ablak ugyanis NEM vált a billentyűzet
                // megjelenésekor, tehát a csomagnév-figyelés erre vak.
                updateKeyboardSuspension()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // A képernyő tartalma változott. Ez NAGYON gyakran érkezik
                // (óra, töltésjelző, animáció), ezért nem ürítünk minden
                // alkalommal — csak megjelöljük, hogy frissíteni kell.
                nodesStale = true
                // És FELJEGYEZZÜK az ütemét: ebből tudjuk, mikor nyugtalan
                // a felület, és mikor kell takarékosabb módra váltani.
                changeMonitor.onContentChanged()
            }
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> {
                // FELDERÍTÉS ÉRINTÉSSEL: az ujj egy elem fölé ért.
                handleHoverEnter(event)
            }
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                // Az ujj elhagyta a kijelzőt. Ha felderítés volt, ODA UGRIK
                // a fókusz — de NEM nyomjuk meg. A megnyomás külön mozdulat.
                finishExploration()
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // ÉRKEZŐ ÉRTESÍTÉS BEMONDÁSA.
                //
                // MIÉRT KELL: eddig csak akkor tudtál az értesítésekről, ha
                // LEHÚZTAD a sávot. Ha közben más alkalmazásban dolgoztál, egy
                // üzenet vagy emlékeztető észrevétlen maradt — pedig látó
                // felhasználó ilyenkor egy pillantással látja.
                announceNotification(event)
            }
        }
    }

    /** Az utoljára bemondott értesítés — hogy ne ismételjük magunkat. */
    private var lastNotificationText: String? = null
    private var lastNotificationAt = 0L

    /**
     * Az érkező értesítés rövid bemondása.
     *
     * ÓVATOSAN: csak akkor szólunk, ha az olvasó AKTÍV, nincs hívás, és nem
     * ugyanaz érkezett újra. A saját értesítéseinket kihagyjuk (azokat a
     * SuperDL amúgy is bemondja), és a hosszú szövegeket rövidítjük.
     */
    private fun announceNotification(event: AccessibilityEvent) {
        if (!ScreenReaderPrefs.isAnnounceNotifications(this)) return
        if (!touchModeActive || lockSuspended || TrainingState.isActive) return
        // HÍVÁS KÖZBEN NEM SZÓLUNK BELE. Telefonálás alatt egy bemondott
        // értesítés zavaró, és a másik fél is hallhatja. Csörgéskor sem:
        // olyankor a hívás a fontos.
        if (ScreenReaderCallControl.isRinging(this) ||
            ScreenReaderCallControl.isInCall(this)
        ) return
        try {
            // A SAJÁT értesítéseinket nem mondjuk be újra.
            val pkg = event.packageName?.toString().orEmpty()
            if (pkg.startsWith(packageName.removeSuffix(".debug"))) return

            val text = event.text.joinToString(" ").trim()
            if (text.isBlank() || text.length < 3) return

            // Ugyanaz a szöveg 10 másodpercen belül: nem ismételjük.
            val now = System.currentTimeMillis()
            if (text == lastNotificationText && now - lastNotificationAt < 10_000L) return
            lastNotificationText = text
            lastNotificationAt = now

            val appName = appLabelOf(pkg)
            val shortText = if (text.length > 140) text.take(140) + "…" else text
            sounds?.play(ScreenReaderSounds.Sound.NOTIFICATIONS)
            // speakAdd: NEM vágjuk félbe, amit épp olvas — a végén hangzik el.
            tts?.speakAdd("$appName: $shortText")
        } catch (e: Exception) {
            android.util.Log.w(ScreenReaderPrefs.TAG, "ertesites bemondas hiba: ${e.message}")
        }
    }

    /**
     * Zárolva van-e a telefon? Ha igen, az olvasó SZÜNETEL.
     *
     * Feloldás után magától folytatja — és mivel a képernyő közben megváltozott,
     * a következő eseménynél újraolvassa az elemeket.
     */
    private fun updateLockSuspension() {
        val locked = try {
            val km = getSystemService(android.content.Context.KEYGUARD_SERVICE)
                as android.app.KeyguardManager
            km.isKeyguardLocked
        } catch (_: Exception) {
            false
        }
        // KIVÉTEL A ZÁROLÁS ALÓL: ha épp a PIN-kód beírása látszik, AKKOR
        // OLVASUNK.
        //
        // MIÉRT: a zárolási képernyőn azért hallgatunk, hogy az értesítéseket
        // ne olvassuk fel bárkinek. A PIN-billentyűzeten viszont NINCS
        // értesítés — ott viszont az van, hogy a felhasználó nem tudja
        // beírni a kódját, ha néma a telefon. Ha a PIN segéd valamiért nem
        // működik, EZ a tartalék: legalább valami beszél.
        // A PIN-KÉPERNYŐN CSAK AKKOR VESSZÜK ÁT, HA A PIN SEGÉD NEM FUT.
        //
        // JAVÍTVA (2026-08-16): eddig az olvasó AKKOR IS dolgozott a
        // PIN-képernyőn, ha a PIN segéd is futott — és ilyenkor MINDKETTŐ
        // elkapkodta a mozdulatokat egymás elől. A felhasználó ezt úgy élte
        // meg, hogy a kettő "összeakad": a PIN segéd reggel még szépen ment,
        // aztán a feloldás kezelhetetlenné vált.
        //
        // A tartalék-elv megmarad: ha a PIN segéd NINCS bekapcsolva, az
        // olvasó továbbra is beszél a PIN-képernyőn — csak akkor lép be,
        // amikor tényleg szükség van rá.
        val pinAssistActive = isPinAssistEnabled()
        val suspend = locked && (pinAssistActive || !isPinEntryVisible())
        if (suspend == lockSuspended) return
        lockSuspended = suspend
        android.util.Log.i(
            ScreenReaderPrefs.TAG,
            if (suspend) "zarolt kepernyo -> olvaso szunetel"
            else "olvaso dolgozik (feloldva vagy PIN-beiras)"
        )
        if (suspend) {
            // Zárolásnál AZONNAL abbahagyunk mindent: a folyamatos olvasást is,
            // és visszaadjuk az érintéseket a rendszernek.
            stopContinuousReading()
            try {
                tts?.stop()
            } catch (_: Exception) {
            }
            setTouchExploration(false)
            clearNodes()
            lastLabel = null
        } else {
            // Feloldás után visszavesszük az irányítást, ha külső appban vagyunk.
            nodesStale = true
            setTouchExploration(shouldRunInCurrentApp())
        }
    }

    /**
     * Fut-e a PIN SEGÉD?
     *
     * Ha igen, a zárolási képernyő AZ Ő TERÜLETE — az olvasó nem szól bele.
     * Két kisegítő szolgáltatás ugyanazon a képernyőn elkapkodná egymás elől
     * a mozdulatokat, és a feloldás kezelhetetlenné válna.
     */
    private fun isPinAssistEnabled(): Boolean = try {
        android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty().contains("KeyguardPinAccessibilityService")
    } catch (_: Exception) {
        // Ha nem tudjuk eldönteni, INKÁBB HALLGATUNK: a PIN segéd a
        // zárolási képernyő elsődleges gazdája.
        true
    }

    /**
     * Látszik-e ÉPPEN a PIN-kód (vagy jelszó, minta) beírása?
     *
     * A rendszer zárolási felületén a beviteli mező azonosítója árulkodó
     * ("pinEntry", "passwordEntry", "lockPattern"), és a számbillentyűk is
     * felismerhetők. Ha ilyet találunk, a felhasználó ÉPP a kódját írja be —
     * ott beszélnünk kell.
     */
    private fun isPinEntryVisible(): Boolean = try {
        val root = rootInActiveWindow
        if (root == null) false
        else {
            val pkg = root.packageName?.toString().orEmpty()
            // Csak a rendszer zárolási felületén vizsgálódunk.
            if (!pkg.contains("systemui") && !pkg.contains("keyguard")) {
                false
            } else {
                hasPinNode(root, 0)
            }
        }
    } catch (_: Exception) {
        false
    }

    private fun hasPinNode(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 8) return false
        try {
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            if (id.contains("pinentry") || id.contains("passwordentry") ||
                id.contains("lockpattern") || id.contains("keyguard_pin") ||
                id.contains("pin_pad") || id.contains("key0") || id.contains("numpad")
            ) return true
            if (node.isEditable && node.isPassword) return true
        } catch (_: Exception) {
        }
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            if (hasPinNode(child, depth + 1)) return true
        }
        return false
    }

    /**
     * Fut-e éppen beviteli billentyűzet? Ha igen, az olvasó elengedi az
     * érintéseket, hogy a billentyűzet kaphassa meg őket.
     */
    private fun updateKeyboardSuspension() {
        val keyboardShowing = try {
            windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        } catch (_: Exception) {
            false
        }
        if (keyboardShowing == keyboardSuspended) return
        keyboardSuspended = keyboardShowing
        android.util.Log.i(
            ScreenReaderPrefs.TAG,
            "billentyuzet ${if (keyboardShowing) "MEGJELENT -> olvaso szunetel" else "eltunt -> olvaso folytatja"}"
        )
        // Ha billentyűzet van, elengedjük az érintéseket; ha eltűnt, és külső
        // appban vagyunk, visszavesszük.
        setTouchExploration(!keyboardShowing && shouldRunInCurrentApp())
    }

    /** Kell-e most futnia az olvasónak a jelenlegi alkalmazásban? */
    private fun shouldRunInCurrentApp(): Boolean {
        if (lockSuspended) return false
        // TANULÓ MÓDBAN mindig dolgozunk — a gyakorlás a SuperDL-en belül
        // történik, és ott is kell fogadnunk a mozdulatokat.
        if (TrainingState.isActive) return ScreenReaderPrefs.isEnabled(this)
        val pkg = currentPackage ?: return false
        val own = pkg.startsWith(packageName.removeSuffix(".debug"))

        // A SAJÁT felületünkön csak akkor kapcsolunk ki, ha a SuperDL a
        // KEZDŐKÉPERNYŐ — mert olyankor a launcher saját gesztusai dolgoznak.
        //
        // Ha valaki CSAK a képernyőolvasót használja (a saját launcherével),
        // akkor a SuperDL számára is egy sima alkalmazás — olyankor itt is
        // olvasnunk kell, különben a beállításai elérhetetlenek lennének.
        if (own && isDefaultLauncher()) return false

        return pkg !in SYSTEM_PACKAGES && ScreenReaderPrefs.isEnabled(this)
    }

    /** A SuperDL a rendszer alapértelmezett kezdőképernyője? */
    private fun isDefaultLauncher(): Boolean = try {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(
            intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        resolved?.activityInfo?.packageName == packageName
    } catch (_: Exception) {
        false
    }

    /**
     * Melyik alkalmazás van TÉNYLEGESEN előtérben?
     *
     * Elsőként az aktív ablakot kérdezzük (ez akkor is helyes, ha az esemény
     * csomagneve hiányzik), és csak tartalékként használjuk az eseményt.
     */
    private fun resolveForegroundPackage(event: AccessibilityEvent): String? {
        val fromWindow = try {
            rootInActiveWindow?.packageName?.toString()
        } catch (_: Exception) {
            null
        }
        if (!fromWindow.isNullOrBlank()) return fromWindow
        return event.packageName?.toString()?.takeIf { it.isNotBlank() }
    }

    /**
     * Előtérbe került egy másik alkalmazás. Ha SAJÁT (SuperDL), kikapcsolunk;
     * ha külső, és a felhasználó engedélyezte, bekapcsolunk.
     */
    private fun onForegroundAppChanged(pkg: String) {
        val own = pkg.startsWith(packageName.removeSuffix(".debug"))
        // EGYETLEN döntési pont: a shouldRunInCurrentApp().
        //
        // JAVÍTVA: korábban KÉT külön helyen dőlt el ugyanez, és a kettő
        // ELTÉRT egymástól. Ez itt nem tudott a launcher-szabályról (hogy a
        // saját felületünkön csak akkor hallgatunk, ha MI vagyunk a
        // kezdőképernyő), sem a tanuló módról. Így előfordulhatott, hogy az
        // egyik bekapcsolta, a másik meg kikapcsolta az olvasót.
        val shouldRun = shouldRunInCurrentApp()

        android.util.Log.i(
            ScreenReaderPrefs.TAG,
            "elterben: $pkg (sajat=$own) -> olvaso ${if (shouldRun) "BE" else "KI"}"
        )

        val wasRunning = touchModeActive
        setTouchExploration(shouldRun)
        clearNodes()
        index = 0
        lastLabel = null      // új alkalmazás: a régi pozíció érvénytelen
        // ÚJ KÉPERNYŐ: a változás-figyelő is tiszta lappal indul. Enélkül egy
        // előző, nyugtalan felület "átragadna" a következőre.
        changeMonitor.reset()
        // A KIJELÖLÉS MÓD is megszűnik: a régi szövegmező már nem létezik.
        // Enélkül BERAGADNA — a fel-le söprés nem lépkedne többé elemek között.
        selectionMode = false

        when {
            shouldRun && !wasRunning -> {
                sounds?.play(ScreenReaderSounds.Sound.ON)
                // ALKALMAZÁSONKÉNTI BEÁLLÍTÁS: ott folytatjuk, ahol legutóbb
                // abbahagytuk ebben az alkalmazásban (pl. böngészőben címsorok).
                restoreAppSettings(pkg)
                tts?.speak("${appLabelOf(pkg)}. Képernyőolvasó bekapcsolva.")
                ScreenReaderPrefs.reportSuccess(this)
                // AUTOMATIKUS FELOLVASÁS: mondja el, mi van a képernyőn, hogy
                // ne kelljen vakon tapogatózni.
                if (ScreenReaderPrefs.isAutoRead(this)) {
                    handler.postDelayed({ autoReadScreen() }, 900L)
                }
            }
            // Kilépéskor is jelezzük — enélkül nem lehet tudni, hogy már nem
            // az olvasó kezeli az érintéseket.
            !shouldRun && wasRunning && own -> {
                sounds?.play(ScreenReaderSounds.Sound.OFF)
                tts?.speak("Képernyőolvasó kikapcsolva.")
            }
        }
    }

    /** Az alkalmazáshoz mentett mód és részletesség visszaállítása. */
    private fun restoreAppSettings(pkg: String) {
        val (savedMode, savedGran) = ScreenReaderPrefs.loadAppMode(this, pkg)
        mode = savedMode?.let { name ->
            NavigationMode.entries.firstOrNull { it.name == name }
        } ?: NavigationMode.ALL
        granularity = savedGran?.let { name ->
            ReadingGranularity.entries.firstOrNull { it.name == name }
        } ?: ReadingGranularity.ELEMENT
        if (mode != NavigationMode.ALL || granularity != ReadingGranularity.ELEMENT) {
            android.util.Log.i(
                ScreenReaderPrefs.TAG,
                "$pkg beallitasa visszaallitva: ${mode.name} / ${granularity.name}"
            )
        }
    }

    /** A jelenlegi mód és részletesség megjegyzése ehhez az alkalmazáshoz. */
    private fun rememberAppSettings() {
        val pkg = currentPackage ?: return
        ScreenReaderPrefs.saveAppMode(this, pkg, mode.name, granularity.name)
    }

    /**
     * AUTOMATIKUS FELOLVASÁS: új képernyőre lépve elmondja, mi van rajta —
     * a képernyő címét és az első pár elemet.
     */
    private fun autoReadScreen() {
        // Zárolt telefonon SOHA nem olvasunk fel semmit.
        if (!touchModeActive || keyboardSuspended || lockSuspended) return
        nodesStale = true
        ensureNodes()
        if (filtered.isEmpty()) return

        val title = screenTitle()
        val firstFew = filtered.take(3).mapNotNull { ScreenReaderNavigator.labelOf(it) }
        val parts = mutableListOf<String>()
        if (!title.isNullOrBlank()) parts += title
        parts += "${filtered.size} elem"
        if (firstFew.isNotEmpty()) parts += firstFew.joinToString(", ")
        say(parts.joinToString(". "))
    }

    /** A képernyő címe, ha az alkalmazás megadja. */
    private fun screenTitle(): String? = try {
        windows.firstOrNull { it.isActive }?.title?.toString()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    /** Az alkalmazás emberi neve a csomagnévből (pl. "WhatsApp"). */
    private fun appLabelOf(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        "Külső alkalmazás"
    }

    companion object {

        /**
         * A FUTÓ szolgáltatás — a menü ezen keresztül indít műveletsort.
         *
         * MIÉRT KELL: a műveletsort csak a képernyőolvasó tudja lejátszani,
         * mert csak ő tud más alkalmazásokban gombot nyomni. A menü viszont
         * egy külön ablak. Enélkül a kettő nem érné el egymást.
         */
        @Volatile
        var live: ScreenReaderService? = null
            private set
        /**
         * Ezeken NEM vesszük át az érintés-kezelést.
         *
         * A LISTA SZÁNDÉKOSAN ÜRES.
         *
         * Korábban itt volt a rendszerfelület és az engedélykérő is. Csakhogy
         * pont az ELSŐ BEÁLLÍTÁS a legnehezebb élethelyzet: valaki most kapja
         * meg a telefont, és éppen engedélyeket kell adnia — ha ott néma a
         * készülék, ott elakad, és lehet, hogy soha nem jut tovább.
         * Ezért az olvasó ezeken is dolgozik.
         *
         * A biztonsági retesz természetesen itt is él: hiba esetén leáll.
         */
        private val SYSTEM_PACKAGES = emptySet<String>()

        /**
         * A képernyőolvasó gesztusai — felolvasható súgó.
         * A menüből is lekérhető, hogy ne kelljen fejből tudni.
         */
        fun speakGestureHelp(): String = listOf(
            "A képernyőolvasó mozdulatai.",
            "EGY UJJAL — mozgás.",
            "Söprés le: következő. Söprés fel: előző.",
            "Söprés jobbra: megnyomás. Söprés balra: vissza.",
            "IGEN VAGY NEM kérdésnél: söprés jobbra IGEN, söprés balra NEM — " +
                "nem kell a gombokhoz navigálni.",
            "CSÖRGŐ HÍVÁSNÁL: söprés jobbra FOGADÁS, söprés balra ELUTASÍTÁS. " +
                "Beszélgetés közben a balra söprés leteszi.",
            "Le majd fel: görgetés lefelé. Fel majd le: görgetés felfelé.",
            "Balra majd jobbra: első elem. Jobbra majd balra: utolsó elem.",
            "Fel majd balra: kezdőképernyő. Fel majd jobbra: legutóbbi alkalmazások.",
            "Le majd balra: értesítések. Le majd jobbra: hosszan nyomás.",
            "Jobbra majd fel: HANGTÉRKÉP — másfél másodperc hang, és tudod, " +
                "milyen ez a képernyő: lista, űrlap, vagy majdnem üres.",
            "Jobbra majd le: a hangtérkép nyelvének váltása, és rögtön hallod is.",
            "Balra majd fel: MŰVELETSOR felvétele. Elindítod, végigcsinálod a " +
                "lépéseket, és ugyanezzel a mozdulattal leállítod. Utána egy " +
                "névvel elindíthatod bármikor.",
            "KÉT UJJAL — mit olvasunk.",
            "Jobbra és balra: váltás a módok között. Minden elem, címsorok, " +
                "hivatkozások, gombok, beviteli mezők, szöveg.",
            "Lefelé: folyamatos olvasás. Felfelé: az utolsó mondat megismétlése.",
            "Két ujjal DUPLA KOPPINTÁS: keresés a képernyőn — kimondod, mit keresel.",
            "HÁROM UJJAL — hogyan olvassuk.",
            "Jobbra: részletesebb. Balra: durvább. Elem, mondat, szó, betű.",
            "Lefelé: hol vagyok. Felfelé: ez a súgó.",
            "Három ujjal DUPLA KOPPINTÁS: a kép felolvasása — mi van rajta írva.",
            "Három ujjal HÁRMAS KOPPINTÁS: a névtelen elem elnevezése — kimondod, " +
                "minek hívjuk, és a program megjegyzi.",
            "FELDERÍTÉS ÉRINTÉSSEL: tartsd az ujjad HÁROM MÁSODPERCIG egy helyben, " +
                "és bekapcsol. Utána pásztázhatsz a képernyőn: ahol elem van, " +
                "bemondom. Fülhallgatóval a hang azt is megmondja, HOL vagy — " +
                "fent magasabb, lent mélyebb, és a bal-jobb fülben csúszik. " +
                "Ahol felemeled az ujjad, oda ugrik a fókusz, de NEM nyomom meg: " +
                "a megnyomás külön jobbra söprés.",
            "TENYÉRREL LETAKARVA azonnal elhallgat — tedd a kezed a telefon " +
                "tetejére. A helyed megmarad, ott folytathatod.",
            "Négy ujjal DUPLA KOPPINTÁS: a szöveg másolása a vágólapra.",
            "Négy ujjal HÁRMAS KOPPINTÁS: beillesztés a vágólapról szövegmezőbe.",
            "DUPLA KOPPINTÁS ÉS NYOMVA TARTÁS: kijelölés indítása szövegmezőben. " +
                "Utána fel-le mozogsz, és a kijelölés nő. Ugyanezzel fejezed be — " +
                "a kijelölt szöveg magától a vágólapra kerül.",
            "A lista végén magától TOVÁBBGÖRGET — nem kell külön görgetni.",
            "Két ujjal HÁRMAS KOPPINTÁS: a színek felolvasása — piros, zöld, szürke.",
            "NÉGY UJJAL — táblázatban: fel-le sorváltás, jobbra-balra oszlopváltás."
        ).joinToString(" ")
    }

    /**
     * Az érintés-kezelés átvétele a rendszertől. Csak akkor kérjük, amikor
     * tényleg dolgozunk — így a SuperDL saját felületén és kikapcsolt állapotban
     * semmit nem változtatunk a telefon megszokott működésén.
     */
    private fun setTouchExploration(on: Boolean) {
        if (touchModeActive == on) return
        try {
            val info = serviceInfo ?: return
            // FONTOS: a TÖBB UJJAS gesztusokat KÜLÖN kérni kell! Enélkül a
            // rendszer el sem küldi a két- és háromujjas söpréseket, hiába
            // kezeljük őket a kódban — ezért nem működött a mód- és a
            // részletesség-váltás. (Android 11 / API 30 felett érhető el.)
            val multiFinger =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
                } else 0

            info.flags = if (on) {
                info.flags or
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    multiFinger
            } else {
                info.flags and
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE.inv() and
                    multiFinger.inv()
            }
            serviceInfo = info
            touchModeActive = on
            android.util.Log.i(
                ScreenReaderPrefs.TAG,
                "erintes-kezeles ${if (on) "BE" else "KI"}, tobb ujjas gesztusok=${multiFinger != 0}"
            )
        } catch (e: Exception) {
            ScreenReaderPrefs.reportFailure(this, "érintés-kezelés váltás: ${e.message}")
        }
    }

    // ── GESZTUSOK ───────────────────────────────────────────────────────────

    override fun onGesture(gestureId: Int): Boolean {
        // AZONNALI KIKAPCSOLÁS: ha közben letiltották (vagy vészleállítás jött),
        // engedjük vissza az érintéseket a rendszernek. Enélkül a telefon
        // "halottnak" tűnne: az olvasó elfogná a mozdulatokat, de nem csinálna
        // semmit — egészen a következő alkalmazás-váltásig.
        if (!ScreenReaderPrefs.isEnabled(this)) {
            setTouchExploration(false)
            return false
        }
        if (!touchModeActive || keyboardSuspended || lockSuspended) return false
        // TANULÓ MÓD: itt SEMMI nem történik élesben — csak tanítunk.
        // Ezért mindent megelőz: a rossz mozdulatnak sincs következménye.
        if (TrainingState.isActive) {
            handleTrainingGesture(gestureId)
            return true
        }
        // MŰVELETSOR LEJÁTSZÁSA KÖZBEN a gesztusok mást jelentenek: jobbra
        // igen, balra megszakítás. Ez mindent megelőz — ha épp fut valami a
        // kezed helyett, a kiszállásnak kell a legkönnyebbnek lennie.
        if (handleRouteGesture(gestureId)) return true
        return try {
            when (gestureId) {
                // ── ALAP NÉGY GESZTUS ──────────────────────────────────────
                GESTURE_SWIPE_DOWN -> { move(+1); true }
                GESTURE_SWIPE_UP -> { move(-1); true }
                GESTURE_SWIPE_RIGHT -> {
                    // CSÖRGŐ HÍVÁS: a jobbra söprés FOGADJA. Ez mindent
                    // megelőz — csörgéskor nincs idő navigálni.
                    if (answerCallIfRinging()) return true
                    activateCurrent(); true
                }
                GESTURE_SWIPE_LEFT -> {
                    // CSÖRGŐ HÍVÁS: a balra söprés ELUTASÍTJA.
                    if (endCallIfActive()) return true
                    // PÁRBESZÉDNÉL a balra söprés az ELUTASÍTÓ gombot nyomja
                    // meg, nem a rendszer vissza-gombját — így a megszokott
                    // szabály él: jobbra igen, balra nem.
                    if (!rejectDialogIfPresent()) {
                        sounds?.play(ScreenReaderSounds.Sound.BACK)
                        recordStep(com.superdl.launcher.macro.TaskStep.Action.BACK, null)
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                    true
                }

                // ── GÖRGETÉS (hosszú listákhoz) ────────────────────────────
                // Le-fel: görgetés előre. Fel-le: görgetés vissza.
                GESTURE_SWIPE_DOWN_AND_UP -> { scrollPage(forward = true); true }
                GESTURE_SWIPE_UP_AND_DOWN -> { scrollPage(forward = false); true }

                // ── UGRÁS A LISTA ELEJÉRE / VÉGÉRE ────────────────────────
                GESTURE_SWIPE_LEFT_AND_RIGHT -> { jumpTo(first = true); true }
                GESTURE_SWIPE_RIGHT_AND_LEFT -> { jumpTo(first = false); true }

                // ── RENDSZERGOMBOK ────────────────────────────────────────
                GESTURE_SWIPE_UP_AND_LEFT -> { goHome(); true }
                GESTURE_SWIPE_UP_AND_RIGHT -> { openRecents(); true }
                GESTURE_SWIPE_DOWN_AND_LEFT -> { openNotifications(); true }

                // ── RÉSZLETEK / HOSSZAN NYOMÁS ────────────────────────────
                GESTURE_SWIPE_DOWN_AND_RIGHT -> { longPressCurrent(); true }

                // ── HANGTÉRKÉP: milyen ez a képernyő? ─────────────────────
                // JOBBRA-MAJD-FEL. Eddig szabadon állt, és a mozdulat
                // "kinyitás" érzetű — felfelé, kifelé a részletekből.
                GESTURE_SWIPE_RIGHT_AND_UP -> { playScreenMap(); true }
                // JOBBRA-MAJD-LE: a hangnyelv váltása. Közvetlenül a térkép
                // mellett, mert a kipróbálás közben kell váltogatni — nem
                // menüben, ahol minden váltás öt lépés.
                GESTURE_SWIPE_RIGHT_AND_DOWN -> { cycleScreenMapStyle(); true }

                // ── MŰVELETSOR FELVÉTELE ─────────────────────────────────
                // BALRA-MAJD-FEL. A felvételt ott kell tudni indítani és
                // leállítani, AHOL a munka történik — egy másik alkalmazás
                // közepén. Menüből ez nem menne: a menübe lépéssel már
                // elhagynád azt a képernyőt, amit fel akarsz venni.
                GESTURE_SWIPE_LEFT_AND_UP -> { toggleRouteRecording(); true }

                // ── KÉT UJJAL: MIT olvasunk ───────────────────────────────
                // (Android 11 felett érkeznek ilyen események; régebbin a
                //  menüből érhetők el ugyanezek a funkciók.)
                GESTURE_2_FINGER_SWIPE_RIGHT -> { switchMode(forward = true); true }
                GESTURE_2_FINGER_SWIPE_LEFT -> { switchMode(forward = false); true }
                GESTURE_2_FINGER_SWIPE_DOWN -> { startContinuousReading(); true }
                GESTURE_2_FINGER_SWIPE_UP -> { repeatLast(); true }

                // ── HÁROM UJJAL: HOGYAN olvassuk ──────────────────────────
                GESTURE_3_FINGER_SWIPE_RIGHT -> { switchGranularity(finer = true); true }
                GESTURE_3_FINGER_SWIPE_LEFT -> { switchGranularity(finer = false); true }
                GESTURE_3_FINGER_SWIPE_DOWN -> { whereAmI(); true }
                GESTURE_3_FINGER_SWIPE_UP -> { say(speakGestureHelp()); true }

                // ── KERESÉS a képernyőn (kimondod, mit keresel) ───────────
                GESTURE_2_FINGER_DOUBLE_TAP -> { searchByVoice(); true }

                // ── KÉP FELOLVASÁSA és SAJÁT ELNEVEZÉS ────────────────────
                GESTURE_3_FINGER_DOUBLE_TAP -> { readImageAtCursor(); true }
                GESTURE_3_FINGER_TRIPLE_TAP -> { labelCurrentElement(); true }

                // ── SZÍNFELISMERÉS ────────────────────────────────────────
                GESTURE_2_FINGER_TRIPLE_TAP -> { readColorsAtCursor(); true }

                // ── MÁSOLÁS, BEILLESZTÉS, KIJELÖLÉS ──────────────────────
                GESTURE_4_FINGER_DOUBLE_TAP -> { copyCurrentToClipboard(); true }
                GESTURE_4_FINGER_TRIPLE_TAP -> { pasteFromClipboard(); true }
                GESTURE_DOUBLE_TAP_AND_HOLD -> { toggleSelectionMode(); true }
                // Az EGYUJJAS dupla koppintás SZÁNDÉKOSAN szabadon marad:
                // a TalkBackben az az "aktiválás", és aki onnan jön, annak a
                // kezében van. Ha elvennénk, megszokásból csendet kapna
                // megnyomás helyett. A némítás a KÖZELSÉG-ÉRZÉKELŐN van.

                // ── TÁBLÁZAT: sor- és oszlop-navigáció ────────────────────
                GESTURE_4_FINGER_SWIPE_DOWN -> { moveInGrid(byRow = true, forward = true); true }
                GESTURE_4_FINGER_SWIPE_UP -> { moveInGrid(byRow = true, forward = false); true }
                GESTURE_4_FINGER_SWIPE_RIGHT -> { moveInGrid(byRow = false, forward = true); true }
                GESTURE_4_FINGER_SWIPE_LEFT -> { moveInGrid(byRow = false, forward = false); true }

                else -> false
            }
        } catch (e: Exception) {
            ScreenReaderPrefs.reportFailure(this, "gesztus: ${e.message}")
            false
        }
    }

    // ── MŰVELETEK ───────────────────────────────────────────────────────────

    /** Görgetés egy "képernyőnyit", majd az új tartalom első elemére állás. */
    private fun scrollPage(forward: Boolean) {
        val node = nodes.getOrNull(index)
        var ok = ScreenReaderNavigator.scroll(node, forward)
        if (!ok) {
            // Az aktuális elem szülői közt nincs görgethető: keressünk a fában.
            val root = try { rootInActiveWindow } catch (_: Exception) { null }
            val scrollable = ScreenReaderNavigator.findScrollable(root)
            ok = scrollable != null && ScreenReaderNavigator.scroll(scrollable, forward)
        }
        if (!ok) {
            sounds?.play(ScreenReaderSounds.Sound.EDGE)
            tts?.speak(if (forward) "A lista végén vagy." else "A lista elején vagy.")
            return
        }
        recordStep(
            if (forward) com.superdl.launcher.macro.TaskStep.Action.SCROLL_FORWARD
            else com.superdl.launcher.macro.TaskStep.Action.SCROLL_BACK,
            null
        )
        // A tartalom változott: friss beolvasás, és az első elemre állunk.
        clearNodes()
        index = 0
        nodesStale = true
        ensureNodes()
        // HELYZETJELZŐ: a hang magassága mutatja, hol tartunk, és a százalék is
        // elhangzik — így görgetés közben végig tudod, mennyi van még hátra.
        val percent = positionPercent()
        sounds?.play(
            if (forward) ScreenReaderSounds.Sound.SCROLL_DOWN
            else ScreenReaderSounds.Sound.SCROLL_UP
        )
        sounds?.playPercent(positionRatio())
        tts?.speak(
            if (forward) "Görgetés lefelé, $percent százalék."
            else "Görgetés felfelé, $percent százalék."
        )
        handler.postDelayed({
            ensureNodes()
            filtered.getOrNull(index)?.let {
                tts?.speakAdd(ScreenReaderNavigator.describe(it))
            }
        }, 350L)
    }

    /** Ugrás a lista első vagy utolsó elemére. */
    private fun jumpTo(first: Boolean) {
        stopContinuousReading()
        ensureNodes()
        if (filtered.isEmpty()) {
            sounds?.play(ScreenReaderSounds.Sound.EDGE)
            sayWarning("Nincs felolvasható elem.")
            return
        }
        index = if (first) 0 else filtered.lastIndex
        sounds?.play(
            if (first) ScreenReaderSounds.Sound.FIRST else ScreenReaderSounds.Sound.LAST
        )
        val node = filtered[index]
        lastLabel = ScreenReaderNavigator.labelOf(node)
        prepareSegments(node, fromEnd = false)
        say(
            (if (first) "Első elem. " else "Utolsó elem. ") +
                "${ScreenReaderNavigator.describe(node)}. ${index + 1} / ${filtered.size}"
        )
    }

    private fun goHome() {
        sounds?.play(ScreenReaderSounds.Sound.HOME)
        tts?.speak("Kezdőképernyő.")
        performGlobalAction(GLOBAL_ACTION_HOME)
        clearNodes()
        index = 0
    }

    private fun openRecents() {
        sounds?.play(ScreenReaderSounds.Sound.RECENTS)
        tts?.speak("Legutóbbi alkalmazások.")
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        clearNodes()
        index = 0
    }

    private fun openNotifications() {
        sounds?.play(ScreenReaderSounds.Sound.NOTIFICATIONS)
        tts?.speak("Értesítések.")
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        clearNodes()
        index = 0
        lastLabel = null
        // A sáv lehúzása után ELOLVASSUK az első értesítést — enélkül csak
        // annyit hallanál, hogy "Értesítések", és magadnak kellene keresgélni.
        handler.postDelayed({
            nodesStale = true
            ensureNodes()
            if (filtered.isEmpty()) {
                say("Nincs értesítés.")
            } else {
                say("${filtered.size} elem. ${ScreenReaderNavigator.describe(filtered[0])}")
            }
        }, 700L)
    }

    /** Hosszan nyomás: a rejtett lehetőségek (törlés, megosztás) előhívása. */
    private fun longPressCurrent() {
        ensureNodes()
        val node = filtered.getOrNull(index)
        if (node == null) {
            tts?.speak("Nincs kiválasztott elem.")
            return
        }
        val ok = ScreenReaderNavigator.longPress(node)
        if (ok) {
            recordStep(com.superdl.launcher.macro.TaskStep.Action.LONG_CLICK, node)
            sounds?.play(ScreenReaderSounds.Sound.LONG_PRESS)
            tts?.speak("Hosszan megnyomva.")
            clearNodes()
        } else {
            sounds?.play(ScreenReaderSounds.Sound.ERROR)
            tts?.speak("Ezen az elemen nincs hosszan nyomás.")
        }
    }

    // ── TUNING MŰVELETEK ────────────────────────────────────────────────────

    /**
     * Navigációs mód váltása. Bemondja a módot ÉS azt, hány elem van benne —
     * így rögtön tudod, van-e értelme ott lépkedni.
     */
    fun switchMode(forward: Boolean) {
        stopContinuousReading()
        mode = if (forward) NavigationMode.next(mode) else NavigationMode.previous(mode)
        rememberAppSettings()   // ehhez az alkalmazáshoz megjegyezzük
        nodesStale = true
        ensureNodes()
        index = 0
        segments = emptyList()
        segmentIndex = 0
        sounds?.play(ScreenReaderSounds.Sound.RECENTS)
        if (filtered.isEmpty()) {
            // ÜRES MÓD: ezt meg KELL mondani, különben a felhasználó azt hinné,
            // elromlott valami. Natív alkalmazásokban a "címsor" és a
            // "hivatkozás" fogalom gyakran nem is létezik.
            say("${mode.label}: nincs ilyen elem ezen a képernyőn.")
        } else {
            say("${mode.label}. ${filtered.size} darab.")
            filtered.firstOrNull()?.let {
                lastLabel = ScreenReaderNavigator.labelOf(it)
                prepareSegments(it, fromEnd = false)
                tts?.speakAdd(ScreenReaderNavigator.describe(it))
            }
        }
    }

    /** Olvasási részletesség váltása (elem > mondat > szó > betű). */
    fun switchGranularity(finer: Boolean) {
        stopContinuousReading()
        val previous = granularity
        granularity = if (finer) {
            ReadingGranularity.finer(granularity)
        } else {
            ReadingGranularity.coarser(granularity)
        }
        sounds?.play(ScreenReaderSounds.Sound.FIELD)
        if (granularity == previous) {
            say(
                if (finer) "Ez a legrészletesebb: betűnként."
                else "Ez a legdurvább: elemenként."
            )
            return
        }
        say("Olvasás: ${granularity.label}.")
        rememberAppSettings()   // ehhez az alkalmazáshoz megjegyezzük
        // Az aktuális elem szövegét újra felbontjuk az új részletességre.
        filtered.getOrNull(index)?.let { prepareSegments(it, fromEnd = false) }
    }

    /**
     * FOLYAMATOS OLVASÁS: az aktuális elemtől végigolvassa a képernyőt.
     * Bármely gesztus megállítja.
     */
    fun startContinuousReading() {
        ensureNodes()
        if (filtered.isEmpty()) {
            sayWarning("Nincs mit felolvasni.")
            return
        }
        continuousReading = true
        sounds?.play(ScreenReaderSounds.Sound.SCROLL_DOWN)
        sayInfo("Folyamatos olvasás.")
        readNextContinuous()
    }

    private fun readNextContinuous() {
        if (!continuousReading) return
        val node = filtered.getOrNull(index)
        if (node == null) {
            stopContinuousReading()
            sayInfo("A képernyő végére értem.")
            return
        }
        val text = ScreenReaderNavigator.describe(node)
        lastSpoken = text
        tts?.speakThen(text) {
            if (!continuousReading) return@speakThen
            if (index >= filtered.lastIndex) {
                stopContinuousReading()
                tts?.speak("A képernyő végére értem.")
            } else {
                index++
                readNextContinuous()
            }
        }
    }

    private fun stopContinuousReading() {
        if (!continuousReading) return
        continuousReading = false
        android.util.Log.i(ScreenReaderPrefs.TAG, "folyamatos olvasas megallitva")
    }

    /** "Hol vagyok?" — alkalmazás, mód, pozíció egy mozdulattal. */
    fun whereAmI() {
        stopContinuousReading()
        ensureNodes()
        val app = currentPackage?.let { appLabelOf(it) } ?: "ismeretlen alkalmazás"
        val position = if (filtered.isEmpty()) {
            "nincs felolvasható elem"
        } else {
            "${index + 1}. elem a ${filtered.size}-ből, ${positionPercent()} százaléknál"
        }
        sounds?.play(ScreenReaderSounds.Sound.HOME)
        say("$app. Mód: ${mode.label}. Olvasás: ${granularity.label}. $position.")
    }

    /** Az utolsó kimondott szöveg megismétlése — ha nem értetted. */
    fun repeatLast() {
        stopContinuousReading()
        if (lastSpoken.isBlank()) {
            say("Nincs mit megismételni.")
        } else {
            sounds?.play(ScreenReaderSounds.Sound.PREV)
            tts?.speak(lastSpoken)
        }
    }

    /**
     * KERESÉS A KÉPERNYŐN — kimondod, mit keresel, és odaugrik.
     *
     * Hosszú listákban (névjegyek, beállítások, weboldal) sokkal gyorsabb, mint
     * végiglépkedni. A SuperDL saját hangbevitelét használja.
     */
    fun searchByVoice() {
        stopContinuousReading()
        val vi = voiceInput ?: run {
            say("A keresés most nem érhető el.")
            return
        }
        if (!vi.isAvailable()) {
            say("A hangfelismerés nem érhető el ezen a készüléken.")
            return
        }
        sounds?.play(ScreenReaderSounds.Sound.FIELD)
        vi.listenPrompt(
            prompt = "Mit keresel a képernyőn?",
            onResult = { text -> jumpToText(text) },
            onError = {
                sounds?.play(ScreenReaderSounds.Sound.ERROR)
                say("Nem értettem.")
            }
        )
    }

    /** Az első olyan elemre ugrik, ami tartalmazza a keresett szöveget. */
    private fun jumpToText(query: String) {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) {
            say("Nem értettem, mit keresel.")
            return
        }
        // A kereséshez a TELJES listát nézzük, ne csak a szűrt módot — különben
        // a találat "eltűnne", ha épp gomb-módban vagy.
        nodesStale = true
        ensureNodes()
        val target = nodes.indexOfFirst {
            ScreenReaderNavigator.labelOf(it)?.lowercase()?.contains(needle) == true
        }
        if (target < 0) {
            sounds?.play(ScreenReaderSounds.Sound.ERROR)
            say("Nincs találat erre: $query.")
            return
        }
        // Ha a találat nem szerepel az aktuális módban, visszaváltunk MINDEN
        // elemre — így a felhasználó biztosan eljut hozzá.
        val node = nodes[target]
        if (!ScreenReaderFilter.matches(node, mode)) {
            mode = NavigationMode.ALL
            applyFilter()
            tts?.speak("Váltás minden elemre.")
        }
        index = filtered.indexOf(node).coerceAtLeast(0)
        lastLabel = ScreenReaderNavigator.labelOf(node)
        prepareSegments(node, fromEnd = false)
        sounds?.play(ScreenReaderSounds.Sound.FIRST)
        say("Találat: ${ScreenReaderNavigator.describe(node)}")
    }

    /**
     * KÉPFELOLVASÁS: mi van a kiválasztott képen?
     * A telefon lefényképezi a képernyőt, kivágja az elemet, és HELYBEN
     * elolvassa a rajta lévő szöveget — a kép nem megy sehova.
     */
    fun readImageAtCursor() {
        stopContinuousReading()
        val reader = imageReader
        if (reader == null || !reader.isAvailable()) {
            say("A képfelolvasás ezen a rendszeren nem érhető el.")
            return
        }
        ensureNodes()
        val node = filtered.getOrNull(index)
        sounds?.play(ScreenReaderSounds.Sound.FIELD)
        say("Kép olvasása.")
        reader.readImage(node) { text ->
            handler.post {
                if (text.isNullOrBlank()) {
                    sounds?.play(ScreenReaderSounds.Sound.EDGE)
                    say("Ezen a képen nincs olvasható szöveg.")
                } else {
                    sounds?.play(ScreenReaderSounds.Sound.ACTIVATE)
                    say("A képen ez olvasható: $text")
                }
            }
        }
    }

    /**
     * SAJÁT ELNEVEZÉS: a névtelen gomb elnevezése, hogy legközelebb tudd, mi az.
     * Kimondod a nevet, és a program megjegyzi — alkalmazásonként külön.
     */
    fun labelCurrentElement() {
        stopContinuousReading()
        ensureNodes()
        val node = filtered.getOrNull(index)
        if (node == null) {
            say("Nincs kiválasztott elem.")
            return
        }
        val pkg = currentPackage ?: return
        val vi = voiceInput
        if (vi == null || !vi.isAvailable()) {
            // NINCS HANGFELISMERÉS — de ettől még el lehet nevezni.
            // Korábban itt elakadt a funkció; most a billentyűzetes ablak jön.
            openLabelKeyboard(node, pkg)
            return
        }
        val existing = ScreenReaderLabels.labelFor(this, node, pkg)
        sounds?.play(ScreenReaderSounds.Sound.FIELD)
        vi.listenPrompt(
            prompt = if (existing != null) {
                "Jelenlegi neve: $existing. Mondd az új nevet."
            } else {
                "Mondd, minek nevezzem ezt az elemet."
            },
            onResult = { spoken ->
                handler.post {
                    val name = spoken.trim()
                    if (name.isBlank()) {
                        say("Nem értettem.")
                    } else if (ScreenReaderLabels.setLabel(this, node, pkg, name)) {
                        rememberFingerprint(node, pkg)
                        sounds?.play(ScreenReaderSounds.Sound.ACTIVATE)
                        say("Elmentve: $name. Mostantól így fogom nevezni.")
                        nodesStale = true
                    } else {
                        say("Ezt az elemet nem tudom megjegyezni.")
                    }
                }
            },
            onError = {
                handler.post {
                    // NEM adjuk fel: ha a hang nem ment, jöjjön a billentyűzet.
                    // Egy zajos helyen ez a különbség aközött, hogy a gomb
                    // örökre névtelen marad, vagy nem.
                    sounds?.play(ScreenReaderSounds.Sound.ERROR)
                    say("A hangos elnevezés nem sikerült. Írd be.")
                    openLabelKeyboard(node, pkg)
                }
            }
        )
    }

    /**
     * ELNEVEZÉS BILLENTYŰZETTEL — a hangos út tartaléka.
     *
     * MIÉRT ÍGY: mire az ablak megnyílik, az elem már nincs a képernyőn (a
     * saját ablakunk van elöl). Ezért a kulcsot MOST számoljuk ki, amíg az elem
     * még megvan, és azt adjuk át. Az ablak már csak a kulccsal dolgozik.
     */
    private fun openLabelKeyboard(node: AccessibilityNodeInfo, pkg: String) {
        val key = ScreenReaderLabels.keyOf(node, pkg)
        if (key == null) {
            sounds?.play(ScreenReaderSounds.Sound.ERROR)
            say("Ezt az elemet nem tudom megjegyezni.")
            return
        }
        sounds?.play(ScreenReaderSounds.Sound.FIELD)
        try {
            val intent = android.content.Intent(this, LabelInputActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(LabelInputActivity.EXTRA_KEY, key)
                putExtra(LabelInputActivity.EXTRA_WHAT, ScreenReaderNavigator.describe(node))
            }
            rememberFingerprint(node, pkg)
            startActivity(intent)
            nodesStale = true
        } catch (e: Exception) {
            sounds?.play(ScreenReaderSounds.Sound.ERROR)
            say("Az elnevező ablak nem nyílt meg.")
        }
    }

    /**
     * Az elem UJJLENYOMATÁNAK eltétele az elnevezéssel együtt.
     *
     * MIÉRT MOST: az ujjlenyomatot akkor kell rögzíteni, amikor az elem MÉG
     * A KÉPERNYŐN VAN. Utólag már nincs miből.
     */
    private fun rememberFingerprint(node: AccessibilityNodeInfo, pkg: String) {
        try {
            val key = ScreenReaderLabels.keyOf(node, pkg) ?: return
            val print = ElementFingerprint.of(node, screenWidth(), screenHeight()) ?: return
            ScreenReaderLabels.saveFingerprint(this, key, print)
        } catch (_: Exception) {
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MŰVELETSOR — felvétel és lejátszás
    // ══════════════════════════════════════════════════════════════════════

    private var playingRoute: com.superdl.launcher.macro.TaskRoute? = null
    private var playIndex = 0

    /** Igaz, amíg egy kérdésre várunk: jobbra igen, balra nem. */
    private var routeAwaitingYes = false

    val isRoutePlaying: Boolean get() = playingRoute != null

    /**
     * FELVÉTEL: egy megtett lépés eltétele.
     *
     * Csak akkor csinál bármit, ha megy a felvétel. Ez azért fontos, mert így
     * a felvétel nem külön "mód", amiben minden másképp működik — ugyanúgy
     * használod a telefont, mint mindig, csak közben figyelünk.
     */
    private fun recordStep(
        action: com.superdl.launcher.macro.TaskStep.Action,
        node: AccessibilityNodeInfo?,
        desiredChecked: Boolean? = null
    ) {
        if (!com.superdl.launcher.macro.TaskRouteStore.isRecording(this)) return
        if (playingRoute != null) return
        try {
            val pkg = currentPackage.orEmpty()
            val print = node?.let { ElementFingerprint.of(it, screenWidth(), screenHeight()) }
            com.superdl.launcher.macro.TaskRouteStore.addStep(
                com.superdl.launcher.macro.TaskStep(
                    action = action,
                    fingerprint = print?.serialize().orEmpty(),
                    label = node?.let { describeWithCustomLabel(it) }.orEmpty(),
                    packageName = pkg,
                    desiredChecked = desiredChecked
                )
            )
            sounds?.play(ScreenReaderSounds.Sound.PIP)
        } catch (_: Exception) {
        }
    }

    /**
     * FELVÉTEL INDÍTÁSA ÉS LEÁLLÍTÁSA — egy gesztussal, oda-vissza.
     *
     * MIÉRT UGYANAZ A MOZDULAT MINDKETTŐRE: mert a felvétel közben a
     * felhasználó az adott alkalmazásban dolgozik, és nem akar egy MÁSODIK
     * mozdulatot is fejben tartani. Amit elindítottál, azt ugyanazzal állítod
     * meg — ez a legkevesebb, amit meg kell jegyezni.
     */
    fun toggleRouteRecording() {
        stopContinuousReading()
        val store = com.superdl.launcher.macro.TaskRouteStore
        if (playingRoute != null) {
            say("Most épp lejátszás megy. Balra söpréssel tudod megállítani.")
            return
        }

        if (!store.isRecording(this)) {
            store.startRecording(this, currentPackage.orEmpty())
            sounds?.play(ScreenReaderSounds.Sound.ON)
            say(
                "Felvétel elindult. Csináld végig a lépéseket úgy, ahogy szoktad — " +
                    "minden megnyomást megjegyzek. Ha kész vagy, balra majd fel."
            )
            return
        }

        val count = store.recordedCount()
        if (count == 0) {
            store.cancelRecording(this)
            sounds?.play(ScreenReaderSounds.Sound.EDGE)
            say("A felvétel véget ért, de nem volt benne egyetlen lépés sem. Nem mentettem el semmit.")
            return
        }

        sounds?.play(ScreenReaderSounds.Sound.OFF)
        val vi = voiceInput
        if (vi != null && vi.isAvailable()) {
            say("$count lépés. Mondd, minek nevezzem.")
            handler.postDelayed({
                vi.listenPrompt(
                    prompt = "Mondd a műveletsor nevét.",
                    onResult = { spoken -> handler.post { finishRecording(spoken) } },
                    onError = { handler.post { finishRecording("") } }
                )
            }, 2200L)
        } else {
            finishRecording("")
        }
    }

    private fun finishRecording(name: String) {
        val store = com.superdl.launcher.macro.TaskRouteStore
        val fallback = "Műveletsor ${store.all(this).size + 1}"
        val route = store.finishRecording(this, name.trim().ifBlank { fallback })
        if (route == null) {
            say("Nem sikerült elmenteni a műveletsort.")
            return
        }
        sounds?.play(ScreenReaderSounds.Sound.FANFARE)
        say(
            "Elmentve: ${route.name}, ${route.steps.size} lépés. " +
                "A menüben, a Műveletsorok pontban tudod elindítani."
        )
    }

    /**
     * LEJÁTSZÁS INDÍTÁSA.
     *
     * Előbb megnyitjuk azt az alkalmazást, ahol a felvétel indult. Enélkül a
     * műveletsor csak akkor menne, ha a felhasználó magától pont ott áll, ahol
     * a felvételkor állt — ami épp az a teher, amit le akarunk venni róla.
     */
    fun startRoute(route: com.superdl.launcher.macro.TaskRoute) {
        stopContinuousReading()
        playingRoute = route
        playIndex = 0
        routeAwaitingYes = false
        say(
            "${route.name}. ${route.steps.size} lépés. " +
                "Balra söpréssel bármikor megállítom."
        )
        var launched = false
        if (route.startPackage.isNotBlank() && route.startPackage != packageName) {
            launched = try {
                packageManager.getLaunchIntentForPackage(route.startPackage)?.let {
                    it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                    true
                } ?: false
            } catch (_: Exception) {
                false
            }
        }
        // Az alkalmazás indulása időbe telik. Ha nem kellett indítani, akkor is
        // hagyunk időt, hogy a bevezető mondat elhangozzon.
        handler.postDelayed({ runNextStep() }, if (launched) 3000L else 2200L)
    }

    fun stopRoute(reason: String) {
        playingRoute = null
        routeAwaitingYes = false
        handler.removeCallbacks(routeRunner)
        sounds?.play(ScreenReaderSounds.Sound.EDGE)
        say(reason)
        nodesStale = true
    }

    private val routeRunner = Runnable { runNextStep() }

    /**
     * A KÖVETKEZŐ LÉPÉS.
     *
     * Minden lépés előtt ELLENŐRZÜNK. Ha nem az van a képernyőn, amit várunk,
     * a program AZONNAL MEGÁLL, és megmondja, hol tart. Soha nem nyomkod
     * tovább vakon.
     *
     * Ez a `domino` gondolat: az automatizálás vakon csak akkor bízható meg,
     * ha TUD FÉLNI. Egy műveletsor, ami mindenáron végigmegy, előbb-utóbb
     * olyat nyom meg, amit nem lehet visszacsinálni.
     */
    private fun runNextStep() {
        val route = playingRoute ?: return
        if (playIndex >= route.steps.size) {
            playingRoute = null
            sounds?.play(ScreenReaderSounds.Sound.FANFARE)
            say("${route.name}: kész, mind a ${route.steps.size} lépés megvolt.")
            nodesStale = true
            return
        }

        val step = route.steps[playIndex]

        // ── AMIT SOHA NEM CSINÁL MAGÁTÓL ──────────────────────────────────
        // Fizetés, végleges küldés, törlés. Itt MINDIG megáll és megkérdez,
        // akkor is, ha a felvételkor TE nyomtad meg. A felvétel arról szólt,
        // hogy mit szoktál csinálni — nem arról, hogy ezt most is akarod.
        if (step.needsTarget && LabelSharing.isDangerous(step.label)) {
            routeAwaitingYes = true
            sounds?.play(ScreenReaderSounds.Sound.LONG_PRESS)
            say(
                "Megállok. A következő lépés: ${step.speak()}. " +
                    "Ez visszafordíthatatlan lehet, ezért nem csinálom meg magamtól. " +
                    "Jobbra igen, balra nem."
            )
            return
        }

        performStep(step)
    }

    /** A lépés végrehajtása — a megerősítés (ha kellett) már megvan. */
    private fun performStep(step: com.superdl.launcher.macro.TaskStep) {
        val route = playingRoute ?: return

        if (!step.needsTarget) {
            say(step.speak())
            when (step.action) {
                com.superdl.launcher.macro.TaskStep.Action.BACK ->
                    performGlobalAction(GLOBAL_ACTION_BACK)
                com.superdl.launcher.macro.TaskStep.Action.HOME ->
                    performGlobalAction(GLOBAL_ACTION_HOME)
                com.superdl.launcher.macro.TaskStep.Action.SCROLL_FORWARD ->
                    scrollForRoute(true)
                com.superdl.launcher.macro.TaskStep.Action.SCROLL_BACK ->
                    scrollForRoute(false)
                else -> {}
            }
            advance()
            return
        }

        // ── AZ ELLENŐRZÉS ────────────────────────────────────────────────
        val wanted = ElementFingerprint.parse(step.fingerprint)
        if (wanted == null) {
            stopRoute(
                "Megálltam a ${playIndex + 1}. lépésnél. Ezt a lépést nem tudom " +
                    "azonosítani — valószínűleg egy régi felvételből való."
            )
            return
        }

        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        }
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0f
        for (node in ScreenReaderNavigator.collectNodes(root)) {
            val print = ElementFingerprint.of(node, screenWidth(), screenHeight()) ?: continue
            val s = ElementFingerprint.score(wanted, print)
            if (s > bestScore) {
                bestScore = s
                best = node
            }
        }

        // NEM TALÁLTAM: megállunk, és megmondjuk, hol. A felhasználó innen
        // kézzel folytathatja — tudja, meddig jutottunk.
        if (best == null || bestScore < ElementFingerprint.THRESHOLD_MAYBE) {
            stopRoute(
                "Megálltam a ${playIndex + 1}. lépésnél. Nem találom ezt: ${step.label}. " +
                    "Lehet, hogy az alkalmazás megváltozott. Innen kézzel tudod folytatni."
            )
            return
        }

        // BIZONYTALAN VAGYOK: megtaláltam valamit, de nem elég biztosan.
        // Ilyenkor sem nyomom meg magamtól — megkérdezem. A rossz gomb
        // megnyomása sokkal drágább, mint egy kérdés.
        if (bestScore < ElementFingerprint.THRESHOLD_SURE) {
            routeAwaitingYes = true
            val found = describeWithCustomLabel(best)
            say(
                "Megállok. Ezt keresem: ${step.label}. Ezt találtam: $found. " +
                    "Nem vagyok biztos benne, hogy ugyanaz. Megnyomjam? Jobbra igen, balra nem."
            )
            pendingTarget = best
            return
        }

        // ── MÁR JÓ ÁLLAPOTBAN VAN? ────────────────────────────────────────
        // Egy kapcsolónál a megnyomás azt jelenti: "változtasd meg". Ha a
        // kapcsoló MÁR abban az állapotban van, amit el akartunk érni, akkor a
        // megnyomás pont elrontaná. Ilyenkor a helyes lépés a KIHAGYÁS — és ki
        // is mondjuk, hogy ne tűnjön úgy, mintha nem történt volna semmi.
        val wantedState = step.desiredChecked
        if (wantedState != null && best.isCheckable && best.isChecked == wantedState) {
            say(
                if (wantedState) "${step.label}: már bekapcsolva, kihagyom."
                else "${step.label}: már kikapcsolva, kihagyom."
            )
            advance()
            return
        }

        say(step.speak())
        val ok = if (step.action == com.superdl.launcher.macro.TaskStep.Action.LONG_CLICK) {
            ScreenReaderNavigator.longPress(best)
        } else {
            ScreenReaderNavigator.activate(best)
        }
        if (!ok) {
            stopRoute(
                "Megálltam a ${playIndex + 1}. lépésnél. Megtaláltam, de nem sikerült " +
                    "megnyomni: ${step.label}."
            )
            return
        }
        advance()
    }

    /** A bizonytalan találat, ami megerősítésre vár. */
    private var pendingTarget: AccessibilityNodeInfo? = null

    private fun scrollForRoute(forward: Boolean) {
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        }
        val scrollable = ScreenReaderNavigator.findScrollable(root)
        if (scrollable != null) ScreenReaderNavigator.scroll(scrollable, forward)
    }

    /**
     * TOVÁBB A KÖVETKEZŐ LÉPÉSRE.
     *
     * A várakozás nem díszítés: az alkalmazásnak idő kell átrajzolni a
     * képernyőt. Ha rögtön keresnénk, még a RÉGI képernyőt látnánk, és vagy
     * rossz elemet nyomnánk meg, vagy feleslegesen megállnánk.
     */
    private fun advance() {
        playIndex++
        nodesStale = true
        clearNodes()
        handler.removeCallbacks(routeRunner)
        handler.postDelayed(routeRunner, 1400L)
    }

    /**
     * A megerősítő kérdés megválaszolása.
     * @return igaz, ha a gesztus ide tartozott, és nem kell tovább kezelni
     */
    private fun handleRouteGesture(gestureId: Int): Boolean {
        if (playingRoute == null) return false

        if (routeAwaitingYes) {
            when (gestureId) {
                GESTURE_SWIPE_RIGHT -> {
                    routeAwaitingYes = false
                    val step = playingRoute?.steps?.getOrNull(playIndex)
                    val target = pendingTarget
                    pendingTarget = null
                    if (step == null) return true
                    if (target != null) {
                        // A bizonytalan találatot most már jóváhagytad.
                        say(step.speak())
                        val ok = if (step.action ==
                            com.superdl.launcher.macro.TaskStep.Action.LONG_CLICK
                        ) {
                            ScreenReaderNavigator.longPress(target)
                        } else {
                            ScreenReaderNavigator.activate(target)
                        }
                        if (ok) advance() else stopRoute("Nem sikerült megnyomni.")
                    } else {
                        performStep(step)
                    }
                    return true
                }
                GESTURE_SWIPE_LEFT -> {
                    pendingTarget = null
                    stopRoute("Rendben, itt megálltam. Innen kézzel folytathatod.")
                    return true
                }
                else -> {
                    say("Jobbra igen, balra nem.")
                    return true
                }
            }
        }

        // LEJÁTSZÁS KÖZBEN a balra söprés MEGSZAKÍT. Ez a legfontosabb
        // gesztus itt: bármikor ki lehet szállni, egy mozdulattal.
        if (gestureId == GESTURE_SWIPE_LEFT) {
            pendingTarget = null
            stopRoute("Megszakítva a ${playIndex + 1}. lépésnél.")
            return true
        }
        return false
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HANGTÉRKÉP
    // ══════════════════════════════════════════════════════════════════════

    /**
     * MILYEN EZ A KÉPERNYŐ? Egy gesztus, másfél másodperc hang.
     *
     * Nem mondja meg, MI van rajta — arra ott a lépkedés és a felderítés.
     * Azt mondja meg, MILYEN: lista-e, űrlap-e, vagy majdnem üres egy nagy
     * gombbal. Ez a fél másodperces pillantás vak megfelelője.
     */
    fun playScreenMap() {
        stopContinuousReading()
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        }
        val map = ScreenMap.of(root, screenWidth(), screenHeight())
        if (map.isEmpty) {
            sounds?.play(ScreenReaderSounds.Sound.EDGE)
            say("Nem látok semmit ezen a képernyőn.")
            return
        }
        val style = ScreenReaderPrefs.getScreenMapStyle(this)
        val tempo = ScreenReaderPrefs.getScreenMapTempo(this)
        ScreenMapPlayer.play(map, style, tempo, sounds, handler) { text -> say(text) }
    }

    /**
     * A HANGNYELV VÁLTÁSA, és rögtön a próbája.
     *
     * MIÉRT SZÓL EGYBŐL: egy hangnyelvet nem a nevéből lehet megítélni, hanem
     * abból, ahogy szól. Ha csak bemondaná, hogy "Pásztázó", azzal semmit nem
     * mondana — az összehasonlításhoz az kell, hogy ugyanazt a képernyőt
     * halld a másik nyelven, azonnal.
     */
    fun cycleScreenMapStyle() {
        val next = ScreenReaderPrefs.cycleScreenMapStyle(this)
        say("Hangtérkép: ${ScreenMapPlayer.styleName(next)}.")
        handler.postDelayed({ playScreenMap() }, 1200L)
    }

    /**
     * SZÍNFELISMERÉS: milyen színű a kurzor alatti elem?
     * A hibaüzenet piros, a siker zöld, az inaktív szürke — ez az információ
     * eddig teljesen rejtve maradt.
     */
    fun readColorsAtCursor() {
        stopContinuousReading()
        val reader = imageReader
        if (reader == null || !reader.isAvailable()) {
            say("A színfelismerés ezen a rendszeren nem érhető el.")
            return
        }
        ensureNodes()
        val node = filtered.getOrNull(index)
        sounds?.play(ScreenReaderSounds.Sound.FIELD)
        reader.sampleColors(node) { reading ->
            handler.post {
                if (reading == null) {
                    sounds?.play(ScreenReaderSounds.Sound.ERROR)
                    say("A színt nem sikerült megállapítani.")
                } else {
                    sounds?.play(ScreenReaderSounds.Sound.ACTIVATE)
                    say(reading.speak())
                }
            }
        }
    }

    /**
     * TÁBLÁZAT-NAVIGÁCIÓ: mozgás sor vagy oszlop szerint.
     * Egy tíz oszlopos táblázatban a soronkénti lépkedés használhatatlan lenne.
     */
    fun moveInGrid(byRow: Boolean, forward: Boolean) {
        stopContinuousReading()
        ensureNodes()
        val current = filtered.getOrNull(index)
        if (current == null) {
            say("Nincs kiválasztott elem.")
            return
        }
        if (!ScreenReaderTable.isInGrid(current)) {
            sounds?.play(ScreenReaderSounds.Sound.EDGE)
            say("Ez a tartalom nem táblázat, itt a szokásos mozgás használható.")
            return
        }
        val target = ScreenReaderTable.findNeighbour(filtered, current, byRow, forward)
        if (target < 0) {
            sounds?.play(ScreenReaderSounds.Sound.EDGE)
            say(
                when {
                    byRow && forward -> "Ez az utolsó sor."
                    byRow -> "Ez az első sor."
                    forward -> "Ez az utolsó oszlop."
                    else -> "Ez az első oszlop."
                }
            )
            return
        }
        index = target
        val node = filtered[index]
        lastLabel = ScreenReaderNavigator.labelOf(node)
        prepareSegments(node, fromEnd = false)
        sounds?.play(ScreenReaderSounds.Sound.NEXT)
        say("${describeWithCustomLabel(node)}. ${ScreenReaderTable.speakPosition(node)}")
    }

    /**
     * HOL TARTUNK a listában, 0.0-tól 1.0-ig — a helyzetjelző hang magasságához.
     *
     * FONTOS: elsősorban a VALÓDI lista-pozíciót használjuk (hányadik sor a
     * hányból), nem a képernyőn látható elemek sorrendjét. Egy 200 elemű
     * listából ugyanis egyszerre csak tíz látszik — a látható sorrend alapján
     * a hang végig ugyanazt mutatná, és semmit nem érne.
     */
    private fun positionRatio(): Float {
        val node = filtered.getOrNull(index)
        if (node != null) {
            try {
                val item = node.collectionItemInfo
                val total = ScreenReaderTable.gridSizeOf(node)?.first ?: 0
                if (item != null && total > 1) {
                    return (item.rowIndex.toFloat() / (total - 1)).coerceIn(0f, 1f)
                }
            } catch (_: Exception) {
            }
        }
        // Tartalék: a képernyőn látható elemek szerinti helyzet.
        if (filtered.size <= 1) return 0f
        return index.toFloat() / (filtered.size - 1)
    }

    /** Az aktuális pozíció százalékban, felolvasáshoz. */
    private fun positionPercent(): Int = (positionRatio() * 100).toInt()

    /**
     * CSÖRGŐ HÍVÁS FOGADÁSA jobbra söpréssel.
     * @return igaz, ha csörgött a telefon és fogadtuk
     */
    private fun answerCallIfRinging(): Boolean {
        if (!ScreenReaderCallControl.isRinging(this)) return false
        val ok = ScreenReaderCallControl.answer(this)
        if (ok) {
            sounds?.play(ScreenReaderSounds.Sound.ACTIVATE)
            say("Hívás fogadva.")
        } else {
            // Ha a rendszer nem engedi, NE tettessük, hogy sikerült.
            say("A hívást nem sikerült fogadni. Használd a képernyő gombjait.")
        }
        return true
    }

    /**
     * HÍVÁS ELUTASÍTÁSA vagy LETÉTELE balra söpréssel.
     * Csörgésnél elutasít, beszélgetés közben leteszi.
     */
    private fun endCallIfActive(): Boolean {
        val ringing = ScreenReaderCallControl.isRinging(this)
        val inCall = ScreenReaderCallControl.isInCall(this)
        if (!ringing && !inCall) return false
        val ok = ScreenReaderCallControl.endCall(this)
        if (ok) {
            sounds?.play(ScreenReaderSounds.Sound.BACK)
            say(if (ringing) "Hívás elutasítva." else "Hívás vége.")
        } else {
            say("A hívást nem sikerült lezárni. Használd a képernyő gombjait.")
        }
        return true
    }

    /**
     * PÁRBESZÉD-KEZELÉS: az ELUTASÍTÓ gomb megnyomása balra söpréskor.
     * @return igaz, ha volt párbeszéd és megnyomtuk a "nem" gombot
     */
    private fun rejectDialogIfPresent(): Boolean {
        val buttons = currentDialogButtons() ?: return false
        val ok = ScreenReaderNavigator.activate(buttons.second)
        if (ok) {
            sounds?.play(ScreenReaderSounds.Sound.BACK)
            say("Nem. ${ScreenReaderNavigator.labelOf(buttons.second).orEmpty()}")
            clearNodes()
            lastLabel = null
        }
        return ok
    }

    /**
     * PÁRBESZÉD-KEZELÉS: a MEGERŐSÍTŐ gomb megnyomása jobbra söpréskor.
     * @return igaz, ha volt párbeszéd és megnyomtuk az "igen" gombot
     */
    private fun confirmDialogIfPresent(): Boolean {
        val buttons = currentDialogButtons() ?: return false
        val ok = ScreenReaderNavigator.activate(buttons.first)
        if (ok) {
            sounds?.play(ScreenReaderSounds.Sound.ACTIVATE)
            say("Igen. ${ScreenReaderNavigator.labelOf(buttons.first).orEmpty()}")
            clearNodes()
            lastLabel = null
        }
        return ok
    }

    /** A képernyőn lévő párbeszéd gombpárja, ha van ilyen. */
    private fun currentDialogButtons(): Pair<AccessibilityNodeInfo, AccessibilityNodeInfo>? {
        ensureNodes()
        return ScreenReaderNavigator.findConfirmButtons(nodes)
    }

    // ── GESZTUS-TANULÁS ─────────────────────────────────────────────────────

    /** Minden tanuló módban érkező mozdulat itt fut le. */
    private fun handleTrainingGesture(gestureId: Int) {
        // KILÉPÉS: NÉGY GYORS KOPPINTÁS bárhol a kijelzőn.
        //
        // MIÉRT ÍGY: a négy ujjas söprés a TÁBLÁZAT-navigációé, azt nem
        // vehetjük el — egy táblázat gyakorlásakor véletlenül kilépnénk.
        // A koppintgatás viszont egyik órában sem szerepel, bárhol elvégezhető
        // (nem kell megkeresni semmit), és véletlenül nem jön össze.
        //
        // A rendszer DUPLA koppintást jelez, ezért KÉT dupla koppintást
        // figyelünk gyors egymásutánban — ez a felhasználó négy koppintása.
        if (gestureId == GESTURE_DOUBLE_TAP) {
            val now = System.currentTimeMillis()
            if (now - lastTrainingDoubleTapAt < 2500L) {
                lastTrainingDoubleTapAt = 0L
                TrainingState.stop()
                sounds?.play(ScreenReaderSounds.Sound.OFF)
                say("Tanulás befejezve. A képernyőolvasó a szokásos módon működik tovább.")
                return
            }
            lastTrainingDoubleTapAt = now
            sounds?.play(ScreenReaderSounds.Sound.NEXT)
            say("Koppints még kétszer a kilépéshez.")
            return
        }
        when (TrainingState.mode) {
            TrainingState.Mode.FREE_PRACTICE -> {
                // SZABAD GYAKORLÁS: csak elmondjuk, mit csinált volna.
                sounds?.play(ScreenReaderSounds.Sound.NEXT)
                val name = ScreenReaderTraining.nameOf(gestureId)
                val effect = ScreenReaderTraining.effectOf(gestureId)
                say("$name. Élesben ezzel $effect.")
            }
            TrainingState.Mode.LESSONS -> handleLessonGesture(gestureId)
            TrainingState.Mode.EXAM -> handleExamGesture(gestureId)
            TrainingState.Mode.OFF -> {}
        }
    }

    /** ÓRÁK: csak akkor engedünk tovább, ha a kért mozdulat sikerült. */
    private fun handleLessonGesture(gestureId: Int) {
        val lessons = ScreenReaderTraining.LESSONS
        val lesson = lessons.getOrNull(TrainingState.index) ?: return

        if (gestureId != lesson.gestureId) {
            sounds?.play(ScreenReaderSounds.Sound.EDGE)
            say(
                "Ez a ${ScreenReaderTraining.nameOf(gestureId)} volt. " +
                    "Most a ${lesson.name} kellene. ${lesson.instruction}"
            )
            return
        }

        // SIKER — rövid ünneplés, aztán jöhet a következő óra.
        playFanfare()
        TrainingState.index++
        if (TrainingState.index >= lessons.size) {
            say(
                "Minden órát elvégeztél! Elena tanárnő gratulál. " +
                    "Ha készen érzed magad, jöhet a vizsga."
            )
            TrainingState.stop()
            return
        }
        val next = lessons[TrainingState.index]
        say(
            "Nagyon jó! Ezzel ${lesson.whatItDoes}. " +
                "${TrainingState.index + 1}. óra a ${lessons.size}-ből. ${next.instruction}"
        )
    }

    /** VIZSGA: a feladat CSAK A CÉLT mondja meg, a mozdulatot nem. */
    private fun handleExamGesture(gestureId: Int) {
        if (TrainingState.inCompositePhase) {
            handleCompositeGesture(gestureId)
            return
        }
        val order = TrainingState.examOrder
        val question = order.getOrNull(TrainingState.index) ?: return

        if (gestureId != question.gestureId) {
            TrainingState.errors++
            sounds?.play(ScreenReaderSounds.Sound.ERROR)
            if (checkExamFailed()) return
            // A VIZSGÁN NEM SÚGUNK: csak a feladatot ismételjük meg.
            say("Nem ez volt. ${TrainingState.errors} hiba eddig. ${question.examTask}")
            return
        }

        sounds?.play(ScreenReaderSounds.Sound.ACTIVATE)
        TrainingState.index++
        if (TrainingState.index >= order.size) {
            // Jöhet a MÁSODIK SZAKASZ: az összetett, élethelyzet-alapú feladatok.
            TrainingState.inCompositePhase = true
            TrainingState.index = 0
            TrainingState.compositeStep = 0
            say(
                "A mozdulatokkal megvagy. Most jönnek az ÖSSZETETT FELADATOK: " +
                    "több lépés egymás után, valós helyzetekben. Figyelj!"
            )
            handler.postDelayed({ announceCompositeTask() }, 3500L)
            return
        }
        say("Helyes! ${TrainingState.index + 1}. feladat. ${order[TrainingState.index].examTask}")
    }

    /** Az aktuális összetett feladat bemondása, és ha kell, az időzítő indítása. */
    private fun announceCompositeTask() {
        val task = TrainingState.compositeOrder.getOrNull(TrainingState.index)
        if (task == null) {
            finishExam()
            return
        }
        val total = TrainingState.compositeOrder.size
        say("${TrainingState.index + 1}. összetett feladat a $total-ből. ${task.prompt}")
        startCompositeTimer(task)
    }

    /**
     * IDŐZÍTŐ az összetett feladathoz.
     * Csörgő telefonnál az élet sem vár — ha lejár, az HIBA.
     */
    private fun startCompositeTimer(task: ScreenReaderTraining.CompositeTask) {
        cancelCompositeTimer()
        if (task.timeLimitSec <= 0) return
        compositeTimeout = Runnable {
            TrainingState.errors++
            sounds?.play(ScreenReaderSounds.Sound.ERROR)
            if (checkExamFailed()) return@Runnable
            say("${task.timeoutMessage} ${TrainingState.errors} hiba eddig.")
            nextCompositeTask()
        }
        handler.postDelayed(compositeTimeout!!, task.timeLimitSec * 1000L)
    }

    private fun cancelCompositeTimer() {
        compositeTimeout?.let { handler.removeCallbacks(it) }
        compositeTimeout = null
    }

    /** ÖSSZETETT FELADAT: több mozdulat, helyes SORRENDBEN. */
    private fun handleCompositeGesture(gestureId: Int) {
        val task = TrainingState.compositeOrder.getOrNull(TrainingState.index) ?: return
        val expected = task.steps.getOrNull(TrainingState.compositeStep) ?: return

        if (gestureId != expected) {
            cancelCompositeTimer()
            TrainingState.errors++
            sounds?.play(ScreenReaderSounds.Sound.ERROR)
            if (checkExamFailed()) return
            say("Nem jó lépés. ${TrainingState.errors} hiba eddig.")
            nextCompositeTask()
            return
        }

        TrainingState.compositeStep++
        if (TrainingState.compositeStep >= task.steps.size) {
            // KÉSZ a feladat.
            cancelCompositeTimer()
            sounds?.play(ScreenReaderSounds.Sound.ACTIVATE)
            say("Megvan!")
            nextCompositeTask()
            return
        }
        // Van még lépés — jelezzük, hogy jó úton jár, de NEM súgjuk meg a
        // következő mozdulatot, csak azt, hogy mit kell elérnie.
        sounds?.play(ScreenReaderSounds.Sound.NEXT)
        val hint = task.stepHints.getOrNull(TrainingState.compositeStep).orEmpty()
        say("Jó. Következő lépés: $hint.")
    }

    private fun nextCompositeTask() {
        TrainingState.index++
        TrainingState.compositeStep = 0
        if (TrainingState.index >= TrainingState.compositeOrder.size) {
            finishExam()
            return
        }
        handler.postDelayed({ announceCompositeTask() }, 1800L)
    }

    /** Elérte-e a bukás-határt? @return igaz, ha a vizsga megszakadt */
    private fun checkExamFailed(): Boolean {
        if (TrainingState.errors < ScreenReaderTraining.MAX_ERRORS) return false
        cancelCompositeTimer()
        val errors = TrainingState.errors
        TrainingState.stop()
        sounds?.play(ScreenReaderSounds.Sound.FAIL)
        say(
            "${ScreenReaderTraining.MAX_ERRORS} hiba. A vizsga megszakad. " +
                ScreenReaderTraining.teacherComment(errors)
        )
        return true
    }

    private var compositeTimeout: Runnable? = null

    /** A vizsga vége: osztályzat és Elena tanárnő szavai. */
    private fun finishExam() {
        cancelCompositeTimer()
        val errors = TrainingState.errors
        val total = ScreenReaderTraining.EXAM_LENGTH
        val grade = ScreenReaderTraining.grade(errors)
        TrainingState.stop()
        // A JEGYHEZ ILLŐ HANG: jelesnél fanfár, bukásnál lekonyuló hang.
        when {
            grade >= 4 -> sounds?.play(ScreenReaderSounds.Sound.FANFARE)
            grade == 1 -> sounds?.play(ScreenReaderSounds.Sound.FAIL)
            else -> sounds?.play(ScreenReaderSounds.Sound.ACTIVATE)
        }
        val gradeName = when (grade) {
            5 -> "jeles, ötös"
            4 -> "jó, négyes"
            3 -> "közepes, hármas"
            2 -> "elégséges, kettes"
            else -> "elégtelen, egyes"
        }
        // ELENA SAJÁT HANGJA a jegyhez, ha fel van véve. MINDEGYIK jegyhez
        // lehet külön felvétel — ami hiányzik, azt a felolvasó mondja.
        val ownVoice = ElenaVoice.play(
            this,
            when (grade) {
                5 -> "otos.wav"
                4 -> "negyes.wav"
                3 -> "harmas.wav"
                2 -> "kettes.wav"
                else -> "egyes.wav"
            }
        )
        if (ownVoice) {
            // A saját hang mondja a kommentárt — mi csak a száraz tényeket.
            say("Vizsga vége. $total feladatból $errors hiba. Osztályzat: $gradeName.")
            return
        }
        say(
            "Vizsga vége. $total feladatból $errors hibával teljesítetted. " +
                "Az osztályzatod: $gradeName. ${ScreenReaderTraining.teacherComment(errors)}"
        )
    }

    /**
     * Siker-jelzés az óra teljesítésekor: a fanfár.
     * SZÁNDÉKOSAN nem Elena hangja — ez tizenháromszor szólal meg egy tanulás
     * alatt, és egy beszélt mondat annyiszor fárasztó lenne.
     */
    private fun playFanfare() {
        sounds?.play(ScreenReaderSounds.Sound.FANFARE)
    }

    /**
     * A KAPCSOLÓ ÁLLAPOTÁNAK HANGJA.
     *
     * Csak be- vagy kikapcsolható elemeknél szólal meg (kapcsolók,
     * jelölőnégyzetek, választógombok). Bemondás nincs — a hang gyorsabb, és
     * nem szakítja félbe a felolvasást.
     */
    private fun playStateSound(node: AccessibilityNodeInfo) {
        try {
            if (!node.isCheckable) return
            sounds?.play(
                if (node.isChecked) ScreenReaderSounds.Sound.STATE_ON
                else ScreenReaderSounds.Sound.STATE_OFF
            )
        } catch (_: Exception) {
        }
    }

    /**
     * MEGNYOMÁS UTÁNI VISSZAJELZÉS kapcsolóknál.
     *
     * MIÉRT KELL: eddig megnyomás után SEMMI nem jelezte, sikerült-e —
     * le kellett söpörni egy elemet, majd vissza, hogy kiderüljön. Ez
     * fölösleges időveszteség, és bizonytalanná teszi az embert.
     *
     * A kis késleltetés azért kell, mert az alkalmazásnak idő kell, hogy
     * TÉNYLEGESEN átállítsa a kapcsolót — ha azonnal kérdeznénk, a régi
     * állapotot kapnánk vissza.
     */
    private fun announceStateAfterActivate(node: AccessibilityNodeInfo) {
        if (!node.isCheckable) return
        handler.postDelayed({
            try {
                if (ScreenReaderNavigator.refresh(node)) {
                    playStateSound(node)
                }
            } catch (_: Exception) {
            }
        }, 350L)
    }

    /**
     * ÖNMŰKÖDŐ GÖRGETÉS: a lista szélén magától továbbgörget.
     *
     * @return igaz, ha sikerült görgetni (ilyenkor NEM lépünk elemet)
     */
    private fun tryAutoScroll(forward: Boolean): Boolean {
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return false
        val ok = ScreenReaderNavigator.scroll(root, forward)
        if (!ok) return false

        sounds?.play(
            if (forward) ScreenReaderSounds.Sound.SCROLL_DOWN
            else ScreenReaderSounds.Sound.SCROLL_UP
        )
        // A tartalom kicserélődött: friss beolvasás, és a görgetés IRÁNYÁNAK
        // megfelelő végére állunk, hogy folytonos legyen az olvasás.
        nodesStale = true
        handler.postDelayed({
            ensureNodes()
            if (filtered.isEmpty()) {
                say("Nincs több elem.")
                return@postDelayed
            }
            index = if (forward) 0 else filtered.lastIndex
            val node = filtered[index]
            lastLabel = ScreenReaderNavigator.labelOf(node)
            prepareSegments(node, fromEnd = !forward)
            sounds?.playPercent(positionRatio())
            say(describeWithCustomLabel(node))
        }, 400L)
        return true
    }

    /**
     * SZÖVEG MÁSOLÁSA a vágólapra.
     *
     * MIÉRT KELL: eddig egy alkalmazásból NEM lehetett szöveget kimenteni —
     * se telefonszámot, se címet, se üzenetet. Ez a képernyőolvasók egyik
     * alapfunkciója: amit felolvas, azt tovább is lehet adni.
     */
    fun copyCurrentToClipboard() {
        stopContinuousReading()
        ensureNodes()
        val node = filtered.getOrNull(index)
        val text = node?.let { ScreenReaderNavigator.labelOf(it) }
        if (text.isNullOrBlank()) {
            sounds?.play(ScreenReaderSounds.Sound.ERROR)
            say("Ezen az elemen nincs másolható szöveg.")
            return
        }
        try {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("SuperDL", text)
            )
            sounds?.play(ScreenReaderSounds.Sound.ACTIVATE)
            say("Másolva: ${text.take(60)}")
        } catch (e: Exception) {
            sounds?.play(ScreenReaderSounds.Sound.ERROR)
            say("A másolás nem sikerült.")
        }
    }

    /**
     * GYORS ELNÉMÍTÁS — a beszéd azonnali leállítása.
     *
     * MIÉRT KELL: ha valaki megszólít, vagy meg akarsz hallgatni valamit, a
     * hosszú felolvasás közben nincs mód elhallgattatni — végig kell várni,
     * vagy elnavigálni. Ez egy mozdulattal megoldja: csend lesz, de a
     * pozíciód MEGMARAD, tehát ott folytathatod, ahol abbahagytad.
     */
    fun silenceNow() {
        stopContinuousReading()
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        sounds?.play(ScreenReaderSounds.Sound.OFF)
    }

    /**
     * KÖZELSÉG-ÉRZÉKELŐ: tenyérrel letakarva elhallgat.
     *
     * MIÉRT EZ, ÉS NEM MOZDULAT: a némítás olyasmi, amit AZONNAL kell tudni —
     * ha valaki megszólít, vagy hallani akarsz valamit. Egy mozdulathoz
     * viszont meg kell keresni a kijelzőt és pontosan söpörni. A tenyeret
     * viszont csak rá kell tenni a telefon tetejére — ezt vakon is bárki
     * megtalálja, akár zsebből előhúzás közben.
     *
     * FONTOS RÉSZLET: csak a LETAKARÁS PILLANATÁBAN némítunk, nem tartósan.
     * Így a zsebben lévő telefon (ahol az érzékelő végig takarva van) NEM
     * marad néma — csak egyszer, a betakarásnál hallgat el.
     */
    private var proximitySensor: android.hardware.Sensor? = null
    private var proximityNear = false

    private val proximityListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
            val value = event?.values?.firstOrNull() ?: return
            val maxRange = proximitySensor?.maximumRange ?: 5f
            val near = value < maxRange && value < 5f
            if (near == proximityNear) return
            proximityNear = near
            // Csak a LETAKARÁS pillanatában némítunk.
            if (near && touchModeActive && !lockSuspended) {
                silenceNow()
            }
        }

        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    private fun startProximityWatch() {
        if (proximitySensor != null) return
        try {
            val manager = getSystemService(android.content.Context.SENSOR_SERVICE)
                as android.hardware.SensorManager
            val sensor = manager.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY)
            if (sensor == null) {
                android.util.Log.i(ScreenReaderPrefs.TAG, "nincs kozelseg-erzekelo ezen a keszuleken")
                return
            }
            manager.registerListener(
                proximityListener, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL
            )
            proximitySensor = sensor
            android.util.Log.i(ScreenReaderPrefs.TAG, "kozelseg-erzekelo figyelese elindult")
        } catch (e: Exception) {
            android.util.Log.w(ScreenReaderPrefs.TAG, "kozelseg-erzekelo hiba: ${e.message}")
        }
    }

    private fun stopProximityWatch() {
        try {
            val manager = getSystemService(android.content.Context.SENSOR_SERVICE)
                as android.hardware.SensorManager
            manager.unregisterListener(proximityListener)
        } catch (_: Exception) {
        }
        proximitySensor = null
        proximityNear = false
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SZÖVEG KIJELÖLÉSE ÉS BEILLESZTÉSE
    // ══════════════════════════════════════════════════════════════════════

    /** Tart-e éppen kijelölés. */
    private var selectionMode = false

    /** Hol kezdődött a kijelölés (karakter-pozíció a mezőben). */
    private var selectionStart = 0

    /**
     * KIJELÖLÉS MÓD be- és kikapcsolása.
     *
     * MŰKÖDÉS: bekapcsolod, majd a MEGSZOKOTT fel-le söpréssel mozogsz — az
     * aktuális részletesség szerint (betű, szó, mondat). Amerre haladsz, a
     * kijelölés NŐ. Kikapcsoláskor a kijelölt szöveg MAGÁTÓL a vágólapra kerül.
     *
     * MIÉRT ÍGY, ÉS NEM UJJAK RAJTATARTÁSÁVAL: a képernyőolvasó a rendszertől
     * KÉSZ mozdulatokat kap ("két ujjal jobbra söpörtek"), nem folyamatos
     * ujjkövetést — nem tudja, hogy egy ujjad épp rajta van-e a kijelzőn.
     * Ez a megoldás viszont UGYANAZT az élményt adja, és a megszokott
     * mozdulatokat használja: nem kell újat tanulni.
     */
    fun toggleSelectionMode() {
        stopContinuousReading()
        ensureNodes()
        val node = filtered.getOrNull(index)

        if (selectionMode) {
            // KIKAPCSOLÁS: a kijelölt szöveg a vágólapra kerül.
            selectionMode = false
            val copied = copySelection(node)
            if (copied != null) {
                sounds?.play(ScreenReaderSounds.Sound.ACTIVATE)
                say("Kijelölés vége. Vágólapra másolva: ${copied.take(60)}")
            } else {
                sounds?.play(ScreenReaderSounds.Sound.OFF)
                say("Kijelölés vége. Nem volt kijelölt szöveg.")
            }
            return
        }

        // BEKAPCSOLÁS — csak szerkeszthető mezőben van értelme.
        if (node == null || !node.isEditable) {
            sounds?.play(ScreenReaderSounds.Sound.EDGE)
            say(
                "Ez a szöveg nem jelölhető ki részletenként. " +
                    "Négy ujjal duplán koppintva viszont az egészet lemásolhatod."
            )
            return
        }
        selectionMode = true
        selectionStart = try {
            node.textSelectionEnd.coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
        sounds?.play(ScreenReaderSounds.Sound.ON)
        say(
            "Kijelölés elindult. Mozogj fel-le a ${granularity.label} szerint. " +
                "A befejezéshez ismételd meg ezt a mozdulatot."
        )
    }

    /** A kijelölt szöveg vágólapra másolása. */
    private fun copySelection(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        return try {
            if (!ScreenReaderNavigator.refresh(node)) return null
            val start = node.textSelectionStart
            val end = node.textSelectionEnd
            val full = node.text?.toString() ?: return null
            if (start < 0 || end < 0 || start == end) return null
            val from = minOf(start, end).coerceIn(0, full.length)
            val to = maxOf(start, end).coerceIn(0, full.length)
            val selected = full.substring(from, to)
            if (selected.isBlank()) return null
            node.performAction(AccessibilityNodeInfo.ACTION_COPY)
            selected
        } catch (e: Exception) {
            android.util.Log.w(ScreenReaderPrefs.TAG, "kijeloles masolas hiba: ${e.message}")
            null
        }
    }

    /**
     * MOZGÁS KIJELÖLÉS KÖZBEN: a kijelölés NŐ, ahogy haladsz.
     * @return igaz, ha a kijelölés kezelte a mozdulatot
     */
    private fun moveWithSelection(forward: Boolean): Boolean {
        if (!selectionMode) return false
        val node = filtered.getOrNull(index) ?: return false
        return try {
            val granularityFlag = when (granularity) {
                ReadingGranularity.CHARACTER ->
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
                ReadingGranularity.WORD ->
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                else ->
                    AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
            }
            val args = android.os.Bundle().apply {
                putInt(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
                    granularityFlag
                )
                // EZ A KULCS: a mozgás KITERJESZTI a kijelölést.
                putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, true)
            }
            val action = if (forward) {
                AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
            } else {
                AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
            }
            val ok = node.performAction(action, args)
            if (ok) {
                sounds?.play(ScreenReaderSounds.Sound.NEXT)
                // Bemondjuk, MENNYI van kijelölve — így tudod, hol tartasz.
                ScreenReaderNavigator.refresh(node)
                val len = kotlin.math.abs(node.textSelectionEnd - node.textSelectionStart)
                say("$len karakter kijelölve.")
            }
            ok
        } catch (e: Exception) {
            android.util.Log.w(ScreenReaderPrefs.TAG, "kijeloles mozgas hiba: ${e.message}")
            false
        }
    }

    /**
     * BEILLESZTÉS a vágólapról a kiválasztott szövegmezőbe.
     *
     * A másolás párja: amit egy alkalmazásból kimentettél, azt egy másikba
     * be is tudod tenni — telefonszámot, címet, jelszót.
     */
    fun pasteFromClipboard() {
        stopContinuousReading()
        ensureNodes()
        val node = filtered.getOrNull(index)
        if (node == null || !node.isEditable) {
            sounds?.play(ScreenReaderSounds.Sound.EDGE)
            say("Ide nem lehet beilleszteni. Állj egy szövegmezőre.")
            return
        }
        try {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) {
                sounds?.play(ScreenReaderSounds.Sound.EDGE)
                say("A vágólap üres.")
                return
            }
            val text = clip.getItemAt(0).coerceToText(this).toString()
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (ok) {
                sounds?.play(ScreenReaderSounds.Sound.ACTIVATE)
                say("Beillesztve: ${text.take(60)}")
            } else {
                sounds?.play(ScreenReaderSounds.Sound.ERROR)
                say("A beillesztés nem sikerült.")
            }
        } catch (e: Exception) {
            sounds?.play(ScreenReaderSounds.Sound.ERROR)
            say("A beillesztés nem sikerült.")
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FELDERÍTÉS ÉRINTÉSSEL
    // ══════════════════════════════════════════════════════════════════════

    private val explorer = TouchExplorer()

    /**
     * Az ujj egy elem fölé ért.
     *
     * KÉT ÁLLAPOT VAN:
     *  - Ha a felderítés MÉG NEM fut: csak mérjük, mennyi ideje áll egy
     *    helyben az ujj. Három másodperc után bekapcsol.
     *  - Ha MÁR FUT: bemondjuk az elemet, és megszólal a térbeli hang.
     */
    private fun handleHoverEnter(event: AccessibilityEvent) {
        if (!touchModeActive || lockSuspended || TrainingState.isActive) return
        if (!ScreenReaderPrefs.isTouchExploreEnabled(this)) return

        val node = try {
            event.source
        } catch (_: Exception) {
            null
        }
        val isNew = explorer.onHoverEnter(node)

        if (!explorer.active) {
            // MÉG NEM FUT. Ha az ujj ugyanazon az elemen marad a beállított
            // ideig, bekapcsoljuk. A késleltetett ellenőrzés azért kell, mert
            // a rendszer nem szól, ha az ujj NEM mozdul — új esemény nélkül
            // nekünk kell utánanéznünk.
            if (isNew) {
                // A beállított időt MINDEN alkalommal átvesszük: így a
                // menüben elvégzett változtatás azonnal érvényes.
                explorer.holdToStartMs = ScreenReaderPrefs.getExploreHoldMs(this)
                handler.removeCallbacks(exploreStartCheck)
                handler.postDelayed(exploreStartCheck, explorer.holdToStartMs + 100L)
            }
            return
        }

        // MÁR FUT: csak akkor szólunk, ha ÚJ elemre értünk — különben
        // ugyanazt ismételgetnénk, amíg rajta tartod az ujjad.
        if (!isNew) return
        announceExploredNode(node)
    }

    /** A hosszú nyomás ellenőrzése — ezt hívja a késleltetett vizsgálat. */
    private val exploreStartCheck = Runnable {
        if (explorer.shouldStart()) {
            explorer.start()
            sounds?.play(ScreenReaderSounds.Sound.ON)
            say("Felderítés. Húzd az ujjad a képernyőn.")
            // Az elemet is bemondjuk, amin épp áll — hogy ne kelljen elmozdulni.
            handler.postDelayed({ announceExploredNode(explorer.currentNode()) }, 900L)
        }
    }

    /**
     * Az ujj alatti elem bemondása, TÉRBELI hanggal.
     *
     * A hang megmondja, HOL vagy a képernyőn: a magassága a függőleges
     * helyzetet, a bal-jobb aránya a vízszinteset. Fülhallgatóval így néhány
     * pásztázás után megjegyezhető a képernyő térképe.
     */
    private fun announceExploredNode(node: AccessibilityNodeInfo?) {
        val position = TouchExplorer.screenPositionOf(node, screenWidth(), screenHeight())

        if (node == null) {
            // ÜRES TERÜLET: halk kattanás, hogy tudd, nem hallgattunk el.
            position?.let { sounds?.playEmptySpot(it.first, it.second) }
            return
        }

        val label = describeWithCustomLabel(node)
        if (label.isBlank()) {
            position?.let { sounds?.playEmptySpot(it.first, it.second) }
            return
        }

        if (explorer.mayPlaySound() && position != null) {
            sounds?.playAtScreenPosition(position.first, position.second)
        }
        // FÉLBESZAKÍTÓ bemondás: ha gyorsan mozogsz, ne torlódjon fel öt elem
        // neve — mindig az AKTUÁLIS szóljon.
        say(label)
    }

    /**
     * A felderítés vége: az ujj elhagyta a kijelzőt.
     *
     * A FÓKUSZ ODA UGRIK, ahol felemelted — de NEM nyomjuk meg.
     * Vakon a felengedés helye bizonytalan; ha azonnal aktiválna, véletlenül
     * törölnél, küldenél vagy vásárolnál. A megnyomás külön mozdulat.
     */
    private fun finishExploration() {
        handler.removeCallbacks(exploreStartCheck)
        if (!explorer.active) {
            explorer.onTouchEnd()
            return
        }
        val node = explorer.currentNode()
        explorer.stop()

        if (node == null) {
            sounds?.play(ScreenReaderSounds.Sound.EDGE)
            say("Nincs itt elem. A fókusz nem változott.")
            return
        }

        // A fókuszt az elemlistában is átállítjuk, hogy a fel-le söprés
        // innen folytatódjon.
        ensureNodes()
        val found = filtered.indexOfFirst { candidate ->
            try {
                val a = android.graphics.Rect()
                val b = android.graphics.Rect()
                candidate.getBoundsInScreen(a)
                node.getBoundsInScreen(b)
                a == b
            } catch (_: Exception) {
                false
            }
        }
        if (found >= 0) {
            index = found
            lastLabel = ScreenReaderNavigator.labelOf(filtered[found])
        }

        sounds?.play(ScreenReaderSounds.Sound.ACTIVATE)
        say("Fókusz: ${describeWithCustomLabel(node)}. Jobbra söprés a megnyomáshoz.")
    }

    private fun screenWidth(): Int = try {
        resources.displayMetrics.widthPixels
    } catch (_: Exception) {
        1080
    }

    private fun screenHeight(): Int = try {
        resources.displayMetrics.heightPixels
    } catch (_: Exception) {
        1920
    }

    private fun ensureNodes() {
        // Csak akkor olvasunk újra, ha üres a lista VAGY jelezték, hogy elavult.
        if (nodes.isNotEmpty() && !nodesStale) return

        // TORLÓDÁS-GÁTLÁS: nyugtalan képernyőn (élő adás, csevegő, töltésjelző)
        // felesleges másodpercenként tízszer újraépíteni a teljes elemfát —
        // a felhasználó úgysem tud olyan gyorsan olvasni, a processzort és az
        // akkumulátort viszont eszi. Ilyenkor a MEGLÉVŐ listával dolgozunk
        // tovább, amíg le nem telik a rövid várakozás.
        if (nodes.isNotEmpty() && !changeMonitor.mayRebuild()) return

        // A HELYÜNK MEGJEGYZÉSE, mielőtt eldobnánk a listát.
        // Nem sorszámmal, hanem MAGÁVAL AZ ELEMMEL — így egy átrendeződő
        // listában (csevegő, levelező) sem ugrik el a fókusz.
        val anchor = FocusAnchor.of(filtered.getOrNull(index), index)

        clearNodes()
        val root = try {
            rootInActiveWindow
        } catch (_: Exception) {
            null
        }
        nodes = ScreenReaderNavigator.collectNodes(root)
        nodesStale = false
        changeMonitor.onRebuilt()
        applyFilter()

        // A HELY VISSZAKERESÉSE a horgony alapján, négy szinten.
        val found = FocusAnchor.findIn(anchor, filtered)
        if (found >= 0) {
            index = found
            return
        }
        index = index.coerceIn(0, (filtered.size - 1).coerceAtLeast(0))
    }

    /** A módra szűrt lista előállítása a teljes elemlistából. */
    private fun applyFilter() {
        filtered = if (mode == NavigationMode.ALL) {
            nodes
        } else {
            nodes.filter { ScreenReaderFilter.matches(it, mode) }
        }
    }

    /**
     * Kimondás ÉS megjegyzés — az utolsó mondat így bármikor megismételhető.
     */
    private fun say(text: String) {
        lastSpoken = text
        tts?.speak(text)
    }

    /**
     * A PROGRAM SAJÁT ÜZENETE — más hangszínnel, mint a képernyő tartalma.
     *
     * Így a felhasználó egy pillanat alatt tudja, hogy amit hall, NEM az
     * alkalmazásból jött, hanem a SuperDL mondja neki. Enélkül a szövegből
     * kellene kitalálnia — és ez félreértésekhez vezet ("a bank azt mondta,
     * hogy nincs hálózat" — nem, a program mondta).
     */
    private fun sayInfo(text: String) {
        lastSpoken = text
        tts?.speak(text, com.superdl.launcher.tts.SpeechRole.SYSTEM)
    }

    /**
     * FIGYELMEZTETÉS — mélyebb, lassabb hang.
     * A mélyebb hang ösztönösen komolyabbnak hat, a lassúbb tempó időt ad
     * felfogni. Hibáknál és "nem sikerült" üzeneteknél használjuk.
     */
    private fun sayWarning(text: String) {
        lastSpoken = text
        tts?.speak(text, com.superdl.launcher.tts.SpeechRole.WARNING)
    }

    /**
     * Az elemlista ürítése — a rendszer-objektumokat EL KELL ENGEDNI.
     *
     * Enélkül minden képernyőváltásnál ottmaradtak a régi elemek a memóriában
     * (300 elem képernyőnként), ami hosszú használat mellett lassuláshoz
     * vezetett volna.
     */
    private fun clearNodes() {
        if (nodes.isEmpty()) return
        ScreenReaderNavigator.recycleAll(nodes)
        nodes = emptyList()
        // A SZŰRT LISTÁT IS ÜRÍTENI KELL.
        //
        // JAVÍTVA: a szűrt lista UGYANAZOKRA az elemekre mutatott, amiket az
        // imént felszabadítottunk. Ha utána bárki hozzányúlt (mert a kód
        // ebből dolgozik), az már FELSZABADÍTOTT elem lett volna — ez
        // kiszámíthatatlan viselkedést és összeomlást okozhat.
        filtered = emptyList()
    }

    private fun move(delta: Int) {
        stopContinuousReading()
        // KIJELÖLÉS közben a fel-le mozgás a kijelölést NÖVELI, nem elemet vált.
        if (moveWithSelection(delta > 0)) return

        // NYUGTALAN KÉPERNYŐ: ha a felület folyamatosan frissül (élő adás,
        // csevegő, töltésjelző), EGYSZER megmondjuk. Enélkül a felhasználó azt
        // hinné, a program akadozik vagy elromlott — pedig csak becsületesen
        // követi, ami a képernyőn történik.
        if (changeMonitor.shouldAnnounceRestless()) {
            say(
                "Ez a képernyő folyamatosan frissül, ezért csendesebb módra váltottam. " +
                    "A léptetés működik."
            )
        }
        ensureNodes()
        if (filtered.isEmpty()) {
            sounds?.play(ScreenReaderSounds.Sound.EDGE)
            say(
                if (mode == NavigationMode.ALL) "Nincs felolvasható elem ezen a képernyőn."
                else "Ebben a módban nincs elem: ${mode.label}."
            )
            return
        }

        // RÉSZLETESSÉG: ha nem elem-szinten olvasunk, előbb a jelenlegi elem
        // szövegén belül lépkedünk, és csak a végén megyünk tovább.
        if (granularity != ReadingGranularity.ELEMENT && segments.isNotEmpty()) {
            val nextSeg = segmentIndex + delta
            if (nextSeg in segments.indices) {
                segmentIndex = nextSeg
                speakSegment()
                return
            }
            // A szöveg végére (vagy elejére) értünk: jöhet a következő elem.
        }

        // ÖNMŰKÖDŐ GÖRGETÉS a lista szélén.
        //
        // MIÉRT: eddig a lista végén KÖRBEUGROTT az elejére — pedig a
        // képernyőn kívül még volt tartalom. A felhasználónak kézzel kellett
        // görgetnie, ami külön mozdulat, és könnyű elfelejteni. Egy hosszú
        // listában (üzenetek, névjegyek, weboldal) ez a leggyakoribb bosszúság.
        //
        // Most, ha a szélén továbblépnél, ELŐBB megpróbálunk görgetni. Csak ha
        // az sem megy — tehát tényleg a tartalom vége —, akkor jelezzük a szélt.
        val atEdge = (delta > 0 && index == filtered.lastIndex) ||
            (delta < 0 && index == 0)
        if (atEdge && tryAutoScroll(delta > 0)) return

        index = (index + delta + filtered.size) % filtered.size
        val node = filtered[index]

        // Ha az elem közben eltűnt (az alkalmazás átrajzolta a képernyőt),
        // frissen olvassuk be a listát, és onnan lépünk tovább.
        if (!ScreenReaderNavigator.refresh(node)) {
            nodesStale = true
            ensureNodes()
            if (filtered.isEmpty()) {
                sounds?.play(ScreenReaderSounds.Sound.EDGE)
                say("A képernyő megváltozott, nincs felolvasható elem.")
                return
            }
            index = index.coerceIn(0, filtered.lastIndex)
        }

        // A mozgás-hang mellett a VÉKONY PITTYEGÉS mutatja a helyzetet:
        // két oktávot fog át a lista eleje és vége között.
        sounds?.play(
            if (delta > 0) ScreenReaderSounds.Sound.NEXT else ScreenReaderSounds.Sound.PREV
        )
        sounds?.playPercent(positionRatio())
        val current = filtered[index]
        lastLabel = ScreenReaderNavigator.labelOf(current)
        prepareSegments(current, fromEnd = delta < 0)
        // KAPCSOLÓ ÁLLAPOTA HANGGAL: ha az elem be- vagy kikapcsolható,
        // rögtön hallod, MELYIK állapotban van — nem kell megvárni a
        // felolvasás végét.
        playStateSound(current)

        if (granularity == ReadingGranularity.ELEMENT || segments.size <= 1) {
            val counter = if (ScreenReaderPrefs.isSpeakCounter(this)) {
                ". ${index + 1} / ${filtered.size}"
            } else ""
            say("${describeWithCustomLabel(current)}$counter")
        } else {
            speakSegment()
        }
        ScreenReaderPrefs.reportSuccess(this)
    }

    /**
     * Az elem leírása, a SAJÁT ELNEVEZÉST előnyben részesítve.
     * Ha egyszer elnevezted, az a név a legjobb — jobb, mint bármi, amit a
     * program kitalálhatna.
     */
    private fun describeWithCustomLabel(node: AccessibilityNodeInfo): String {
        val pkg = currentPackage
        if (pkg != null) {
            val extra = if (node.isCheckable) {
                if (node.isChecked) ", bekapcsolva" else ", kikapcsolva"
            } else ""

            // 1. PONTOS TALÁLAT: ugyanaz a kulcs. Ez a régi, bevált út —
            //    változatlanul ez az első.
            ScreenReaderLabels.labelFor(this, node, pkg)?.let { custom ->
                return "$custom$extra"
            }

            // 2. UJJLENYOMAT: a kulcs nem talált, de lehet, hogy csak
            //    elmozdult az elem. Itt eddig egyszerűen elveszett a név.
            //
            //    A LÉNYEG A BIZONYTALANSÁG KIMONDÁSA: ha az egyezés nem elég
            //    erős, a program NEM állítja, hanem valószínűsíti. A "gomb"
            //    is ott marad a mondatban, hogy tudd, ez nem biztos tudás.
            //    Inkább hallgat, mint téveszt — de ha sejt valamit, azt
            //    sejtésként mondja el, nem hallgatja el.
            val match = try {
                ScreenReaderLabels.matchByFingerprint(
                    this, node, pkg, screenWidth(), screenHeight()
                )
            } catch (_: Exception) {
                null
            }
            if (match != null) return speakMatch(match, node, extra)

            // 3. KÖZÖSSÉGI CÍMKE. VASSZABÁLY: ez az UTOLSÓ út — a saját
            //    címkéd mindig veri a közösét, mert a fentiek előbb futnak le.
            //    Amit valaki más adott, azt csak akkor halljuk, ha nekünk
            //    magunknak nincs jobb nevünk rá.
            val shared = try {
                LabelPackStore.match(
                    this, node, pkg,
                    ScreenReaderLabels.keyOf(node, pkg),
                    screenWidth(), screenHeight()
                )
            } catch (_: Exception) {
                null
            }
            if (shared != null) return speakMatch(shared, node, extra)
        }
        return ScreenReaderNavigator.describe(node)
    }

    /**
     * A találat kimondása — a BIZONYTALANSÁGGAL együtt.
     *
     * Ha az egyezés nem elég erős, a program nem állít, hanem valószínűsít, és
     * ott hagyja az eredeti leírást is, hogy legyen mihez viszonyítani.
     * Inkább hallgat, mint téveszt — de ha sejt valamit, azt sejtésként mondja
     * el, nem hallgatja el.
     */
    private fun speakMatch(
        match: ScreenReaderLabels.Match,
        node: AccessibilityNodeInfo,
        extra: String
    ): String = if (match.sure) {
        "${match.label}$extra"
    } else {
        "valószínűleg ${match.label}$extra, ${ScreenReaderNavigator.describe(node)}"
    }

    /** Az aktuális elem szövegének felbontása a kért részletességre. */
    private fun prepareSegments(node: AccessibilityNodeInfo, fromEnd: Boolean) {
        val text = ScreenReaderNavigator.labelOf(node).orEmpty()
        segments = ScreenReaderFilter.segments(text, granularity)
        segmentIndex = if (fromEnd && segments.isNotEmpty()) segments.lastIndex else 0
    }

    /** A szöveg aktuális darabjának kimondása. */
    private fun speakSegment() {
        val seg = segments.getOrNull(segmentIndex) ?: return
        sounds?.play(ScreenReaderSounds.Sound.NEXT)
        val spoken = if (granularity == ReadingGranularity.CHARACTER) {
            ScreenReaderFilter.speakCharacter(seg, ScreenReaderPrefs.isPhonetic(this))
        } else {
            seg
        }
        say(spoken)
    }

    private fun activateCurrent() {
        stopContinuousReading()
        // PÁRBESZÉDNÉL a jobbra söprés a MEGERŐSÍTŐ gombot nyomja meg —
        // nem kell odanavigálni, és nem lehet véletlenül a rosszat választani.
        if (confirmDialogIfPresent()) return
        ensureNodes()
        val node = filtered.getOrNull(index)
        if (node == null) {
            sounds?.play(ScreenReaderSounds.Sound.ERROR)
            tts?.speak("Nincs kiválasztott elem.")
            return
        }
        // SZÖVEGMEZŐ: nem "megnyomni" kell, hanem beleállni — ilyenkor jön elő a
        // billentyűzet, amivel be lehet írni (a rendszer hangbevitele is onnan
        // érhető el). A jelenlegi tartalmat felolvassuk, hogy tudd, mi van benne.
        if (node.isEditable) {
            val focused = try {
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (_: Exception) {
                false
            }
            if (focused) {
                sounds?.play(ScreenReaderSounds.Sound.FIELD)
                tts?.speak("Szövegmező kiválasztva. ${ScreenReaderNavigator.describeFieldContent(node)}")
            } else {
                sounds?.play(ScreenReaderSounds.Sound.ERROR)
                tts?.speak("A szövegmező nem választható ki.")
            }
            return
        }

        // A KAPCSOLÓ ÁLLAPOTÁT A MEGNYOMÁS ELŐTT olvassuk ki: utána már az új
        // állapotot látnánk, és nem tudnánk, mit AKART a felhasználó.
        val wantedState = if (node.isCheckable) !node.isChecked else null

        val ok = ScreenReaderNavigator.activate(node)
        if (ok) {
            recordStep(com.superdl.launcher.macro.TaskStep.Action.CLICK, node, wantedState)
            sounds?.play(ScreenReaderSounds.Sound.ACTIVATE)
            // KAPCSOLÓNÁL azonnal jelezzük az ÚJ állapotot — eddig ehhez
            // le kellett söpörni és visszalépni.
            announceStateAfterActivate(node)
        } else {
            sounds?.play(ScreenReaderSounds.Sound.ERROR)
            tts?.speak("Ez az elem nem nyomható meg.")
        }
        // KAPCSOLÓNÁL a képernyő NEM változik, csak az állapot billen át —
        // ezért az elemlistát MEGTARTJUK, csak elavultnak jelöljük.
        // (Ha itt kiürítenénk, az elem hivatkozása is megszűnne, és a
        // késleltetett állapot-ellenőrzés nem működne.)
        if (node.isCheckable) {
            nodesStale = true
        } else {
            clearNodes()
            lastLabel = null
        }
    }

    override fun onInterrupt() {
        // A rendszer megszakította a felolvasást.
    }

    override fun onDestroy() {
        if (live === this) live = null
        setTouchExploration(false)
        stopProximityWatch()
        handler.removeCallbacksAndMessages(null)
        clearNodes()
        try {
            sounds?.release()
        } catch (_: Exception) {
        }
        sounds = null
        try {
            imageReader?.release()
        } catch (_: Exception) {
        }
        imageReader = null
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        super.onDestroy()
    }
}
