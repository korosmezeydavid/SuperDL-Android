package com.superdl.launcher.macro

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.screenreader.ScreenReaderService
import com.superdl.launcher.tts.TtsManager

/**
 * MŰVELETSOROK — a lista, az indítás és a törlés.
 *
 * A felvétel NEM innen indul: azt a képernyőolvasóban kell tudni elindítani
 * (balra majd fel), mert a felvétel egy MÁSIK alkalmazás közepén kezdődik.
 * Ha innen indulna, a menübe lépéssel már elhagytad volna azt a képernyőt,
 * amit fel akarsz venni.
 */
class TaskRouteActivity : Activity() {

    private lateinit var tts: TtsManager
    private lateinit var gestureListener: SwipeGestureListener
    private lateinit var label: TextView

    private var routes: List<TaskRoute> = emptyList()

    /** Melyik műveletsor jött letöltött csomagból — azt nem lehet törölni, csak elvetni. */
    private var sharedIds: Set<String> = emptySet()

    private var index = 0

    private enum class Stage { LIST, ACTION, CONFIRM_DELETE, CONFIRM_SHARE }

    private var stage = Stage.LIST
    private var actionIndex = 0

    private fun actionsFor(route: TaskRoute): List<String> =
        if (route.id in sharedIds) {
            listOf("Indítás", "Lépések felolvasása", "Megosztás", "Elvetés")
        } else {
            listOf("Indítás", "Lépések felolvasása", "Megosztás", "Törlés")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(32, 32, 32, 32)
        }
        label = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(label)
        setContentView(root)

        tts = TtsManager(this)
        gestureListener = SwipeGestureListener(
            context = this,
            onSwipeUp = { onUp() },
            onSwipeDown = { onDown() },
            onSwipeRight = { onRight() },
            onSwipeLeft = { onLeft() }
        )
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        buildList()
        stage = Stage.LIST
        if (routes.isEmpty()) {
            label.text = "Nincs műveletsor"
            tts.speak(
                "Még nincs egyetlen műveletsor sem. Felvenni a képernyőolvasóban tudsz: " +
                    "menj be abba az alkalmazásba, ahol a munka történik, és söpörj " +
                    "balra majd fel. Csináld végig a lépéseket, aztán megint balra majd fel."
            )
            return
        }
        if (index >= routes.size) index = routes.size - 1
        updateText()
        tts.speak("Műveletsorok. ${routes.size} darab. Fel-le lépkedés, jobbra megnyitás, balra kilépés.")
        tts.speakAdd(speakCurrent())
    }

    /**
     * A LISTA: a sajátok, majd a letöltöttek.
     *
     * A letöltöttek MÖGÉ kerülnek, mert a sajátjait mindenki előbb keresi —
     * és mert amit magad vettél fel, arról tudod, mit csinál.
     */
    private fun buildList() {
        val own = TaskRouteStore.all(this)
        val ownIds = own.map { it.id }.toSet()
        val shared = RoutePackStore.all(this)
            .filter { it.id !in ownIds && !RoutePackStore.isDiscarded(this, it.id) }
        sharedIds = shared.map { it.id }.toSet()
        routes = own + shared
    }

    private fun current(): TaskRoute? = routes.getOrNull(index)

    private fun appNameOf(packageName: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) {
        "ismeretlen alkalmazás"
    }

    private fun speakCurrent(): String {
        val r = current() ?: return "A lista üres."
        val source = if (r.id in sharedIds) " Letöltött műveletsor." else ""
        return "${r.name}. ${r.steps.size} lépés, ${appNameOf(r.startPackage)}.$source"
    }

    private fun updateText() {
        val r = current()
        label.text = if (r == null) {
            "Nincs műveletsor"
        } else {
            "${r.name}\n${r.steps.size} lépés\n${index + 1} / ${routes.size}"
        }
    }

    private fun onUp() {
        when (stage) {
            Stage.LIST -> move(-1)
            Stage.ACTION -> moveAction(-1)
            else -> tts.speak("Jobbra igen, balra nem.")
        }
    }

    private fun onDown() {
        when (stage) {
            Stage.LIST -> move(+1)
            Stage.ACTION -> moveAction(+1)
            else -> tts.speak("Jobbra igen, balra nem.")
        }
    }

    private fun onRight() {
        when (stage) {
            Stage.LIST -> openActions()
            Stage.ACTION -> runAction()
            Stage.CONFIRM_DELETE -> doDelete()
            Stage.CONFIRM_SHARE -> doShare()
        }
    }

    private fun onLeft() {
        when (stage) {
            Stage.LIST -> {
                tts.speak("Kilépés.")
                finish()
            }
            Stage.ACTION -> {
                stage = Stage.LIST
                tts.speak("Vissza a listához. ${speakCurrent()}")
            }
            Stage.CONFIRM_DELETE -> {
                stage = Stage.ACTION
                tts.speak("Mégsem törlöm.")
            }
            Stage.CONFIRM_SHARE -> {
                stage = Stage.ACTION
                tts.speak("Nem küldtem el.")
            }
        }
    }

    private fun move(delta: Int) {
        if (routes.isEmpty()) return
        index = (index + delta + routes.size) % routes.size
        updateText()
        tts.speak(speakCurrent())
    }

    private fun moveAction(delta: Int) {
        val r = current() ?: return
        val list = actionsFor(r)
        actionIndex = (actionIndex + delta + list.size) % list.size
        tts.speak(list[actionIndex])
    }

    private fun openActions() {
        val r = current() ?: return
        stage = Stage.ACTION
        actionIndex = 0
        tts.speak("${r.name}. Fel-le választás, jobbra indítás, balra vissza.")
        tts.speakAdd(actionsFor(r)[actionIndex])
    }

    private fun runAction() {
        val r = current() ?: return
        when (actionIndex) {
            0 -> play(r)
            1 -> speakSteps(r)
            2 -> askShare(r)
            3 -> {
                stage = Stage.CONFIRM_DELETE
                if (r.id in sharedIds) {
                    tts.speak(
                        "Elveted ezt a letöltött műveletsort: ${r.name}? " +
                            "Eltűnik a listádból. Jobbra igen, balra nem."
                    )
                } else {
                    tts.speak("Biztosan törlöd ezt: ${r.name}? Jobbra igen, balra nem.")
                }
            }
        }
    }

    /**
     * MEGOSZTÁS — de csak azután, hogy MEGHALLGATTAD, mi megy el.
     *
     * A lépések annak a NEVÉT viszik magukkal, amit megnyomtál, és az a név a
     * képernyőről származik. Egy banki alkalmazásban ez lehet egy összeg, egy
     * üzenetküldőben egy ismerős neve. A program nem tudja megmondani, melyik
     * az — de fel tudja olvasni, hogy TE eldönthesd.
     */
    private fun askShare(r: TaskRoute) {
        stage = Stage.CONFIRM_SHARE
        tts.speak("${RoutePackStore.exportSummary(r)} Elküldöd? Jobbra igen, balra nem.")
    }

    private fun doShare() {
        val r = current() ?: return
        stage = Stage.ACTION
        val ok = com.superdl.launcher.report.BugReportSender.share(
            this,
            RoutePackStore.export(r),
            "SuperDL műveletsor: ${r.name}"
        )
        if (!ok) tts.speak("A megosztás most nem működik.")
    }

    /**
     * INDÍTÁS.
     *
     * A lejátszást a KÉPERNYŐOLVASÓ végzi, mert csak ő tud más alkalmazásokban
     * gombot nyomni. Ezért előbb bezárjuk ezt az ablakot — különben a
     * műveletsor a saját menünkben kezdene el kapkodni.
     */
    private fun play(route: TaskRoute) {
        val service = ScreenReaderService.live
        if (service == null) {
            tts.speak(
                "A műveletsorhoz a képernyőolvasónak futnia kell. " +
                    "Kapcsold be a Képernyőolvasó menüpontban, és próbáld újra."
            )
            return
        }
        tts.speak("Indítom: ${route.name}.")
        label.postDelayed({
            finish()
            service.startRoute(route)
        }, 1600L)
    }

    /**
     * A LÉPÉSEK FELOLVASÁSA — mielőtt bárki elindítaná.
     *
     * MIÉRT KELL: egy műveletsor a te nevedben nyomkod gombokat. Ha nem tudod
     * pontosan, mit fog csinálni, akkor nem te irányítod, hanem bízol benne.
     * A bizalomhoz először ellenőrizhetőség kell.
     */
    private fun speakSteps(route: TaskRoute) {
        val text = route.steps.mapIndexed { i, s -> "${i + 1}. ${s.speak()}" }
            .joinToString(". ")
        tts.speak("${route.name}, ${route.steps.size} lépés. $text")
    }

    private fun doDelete() {
        val r = current() ?: return
        val wasShared = r.id in sharedIds
        // A LETÖLTÖTTET nem töröljük, csak ELVETJÜK: a csomag a katalógusból
        // jön, és a következő frissítéssel újra megérkezne. Az elvetés viszont
        // a te döntésed, és megmarad.
        if (wasShared) RoutePackStore.discard(this, r.id) else TaskRouteStore.remove(this, r.id)
        buildList()
        stage = Stage.LIST
        val done = if (wasShared) "${r.name} elvetve." else "${r.name} törölve."
        if (routes.isEmpty()) {
            index = 0
            updateText()
            tts.speak("$done Nem maradt műveletsor.")
            return
        }
        if (index >= routes.size) index = routes.size - 1
        updateText()
        tts.speak("$done ${speakCurrent()}")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestureListener.detector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun onDestroy() {
        try {
            tts.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
