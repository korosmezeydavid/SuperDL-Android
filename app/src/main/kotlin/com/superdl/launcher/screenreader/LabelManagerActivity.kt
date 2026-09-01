package com.superdl.launcher.screenreader

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.tts.TtsManager

/**
 * CÍMKEKEZELŐ — a saját ÉS a közösségi elnevezések egy listában.
 *
 * MIÉRT KELL: elnevezni eddig is lehetett, de a nevek után SEMMIT nem lehetett
 * velük kezdeni. Nem lehetett megnézni, mid van; nem lehetett egy elrontott
 * nevet megkeresni és kijavítani; nem lehetett törölni. A címke így nem
 * vagyontárgy volt, hanem zsákutca: ami egyszer bement, az bent maradt.
 *
 * MIÉRT EGY LISTÁBAN a sajátok és a letöltöttek: mert a felhasználó nem
 * "sajátot" vagy "közösségit" keres, hanem EGY NEVET, amit hallott. Két lista
 * azt jelentené, hogy előbb ki kell találnia, melyikben van. A lista viszont
 * MEGMONDJA mindegyikről, honnan való — a bizalomhoz az kell, hogy tudd, ki
 * adta a nevet.
 *
 * HÁROM SZAKASZ, mindegyik ugyanazzal a négy iránnyal:
 *   1. LISTA — fel-le lépkedés, jobbra megnyitás, balra kilépés.
 *   2. MŰVELET — a sajátnál átnevezés/részletek/törlés, a közösséginél
 *      saját név adása/részletek/elvetés.
 *   3. MEGERŐSÍTÉS — jobbra igen, balra nem. A képernyőolvasó megszokott
 *      kérdés-válasza; nem kell új mozdulatot tanulni.
 */
class LabelManagerActivity : Activity() {

    /**
     * Egy sor a listában — saját vagy közösségi.
     * Egy közös alak, hogy a lépkedés ne tudjon a kettő közt elrontani semmit.
     */
    private data class Row(
        val key: String,
        val label: String,
        val packageName: String,
        val community: Boolean,
        /** Saját: azonosítóhoz kötött-e. Közösségi: van-e ujjlenyomata. */
        val stable: Boolean,
        val note: String,
        val packName: String,
        val origin: String,
        val discarded: Boolean
    )

    private lateinit var tts: TtsManager
    private lateinit var gestureListener: SwipeGestureListener
    private lateinit var label: TextView

    private var rows: List<Row> = emptyList()
    private var index = 0

    private enum class Stage { LIST, ACTION, CONFIRM }

    private var stage = Stage.LIST
    private var actionIndex = 0

    /** A megerősítésre váró művelet, hogy a kérdés és a tett ne csússzon szét. */
    private var pendingAction = ""

    private fun actionsFor(row: Row): List<String> = if (row.community) {
        listOf("Saját név adása", "Részletek", if (row.discarded) "Visszakérés" else "Elvetés")
    } else {
        listOf("Átnevezés", "Részletek", "Törlés")
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

    /**
     * A lista MINDEN visszatéréskor újratöltődik.
     *
     * MIÉRT: az átnevezés egy másik ablakban történik. Ha nem töltenénk újra, a
     * visszaérkező felhasználó a RÉGI nevet hallaná, és azt hinné, nem mentődött
     * el. A csendes hazugság rosszabb, mint a hangos hiba.
     */
    override fun onResume() {
        super.onResume()
        reload()
    }

    /** A lista összeállítása. A hívó dönti el, mondjunk-e hozzá bevezetőt. */
    private fun buildRows() {
        val own = ScreenReaderLabels.allLabels(this).map {
            Row(it.key, it.label, it.packageName, false, it.stable, "", "", "", false)
        }
        // A közösségi címke KIMARAD, ha ugyanarra a kulcsra van saját neved.
        // Nem elrejtés: egyszerűen nincs mit kezdeni vele, mert a sajátod
        // úgyis veri. Két azonos sor csak összezavarna.
        val ownKeys = own.map { it.key }.toSet()
        val shared = LabelPackStore.allLabels(this)
            .filter { it.key !in ownKeys }
            .map {
                Row(
                    it.key, it.label, it.packageName, true,
                    it.fingerprint != null, it.note, it.packName, it.origin,
                    LabelPackStore.isDiscarded(this, it.key)
                )
            }
        rows = (own + shared).sortedWith(
            compareBy({ it.packageName }, { it.label.lowercase() })
        )
    }

    private fun reload() {
        val ownCount = ScreenReaderLabels.allLabels(this).size
        buildRows()
        stage = Stage.LIST

        if (rows.isEmpty()) {
            label.text = "Nincs elnevezés"
            tts.speak(
                "Nincs egyetlen elnevezés sem. Egy névtelen gombot a képernyőolvasóban " +
                    "tudsz elnevezni: három ujjal háromszor koppintasz rajta. Kész " +
                    "elnevezés-csomagokat pedig a Modulboltból tölthetsz le."
            )
            return
        }
        if (index >= rows.size) index = rows.size - 1
        updateText()

        val sharedCount = rows.size - ownCount
        val summary = when {
            sharedCount <= 0 -> "$ownCount saját."
            ownCount == 0 -> "$sharedCount letöltött."
            else -> "$ownCount saját, $sharedCount letöltött."
        }
        tts.speak("Elnevezések. $summary Fel-le lépkedés, jobbra megnyitás, balra kilépés.")
        tts.speakAdd(speakCurrent())
    }

    /** Az alkalmazás EMBERI neve, ha kideríthető. */
    private fun appNameOf(packageName: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) {
        // Az alkalmazás azóta törölve lett. A címke marad — ha visszateszik,
        // újra érvényes lesz. Ezt meg is mondjuk, hogy ne tűnjön hibának.
        "$packageName, nincs telepítve"
    }

    private fun current(): Row? = rows.getOrNull(index)

    private fun speakCurrent(): String {
        val r = current() ?: return "A lista üres."
        val where = appNameOf(r.packageName)
        val source = when {
            r.discarded -> " Letöltött név, elvetve — nem mondom ki."
            r.community -> " Letöltött név a ${r.packName} csomagból."
            else -> ""
        }
        // A bizonytalan azonosítást KIMONDJUK. Aki tudja, hogy egy név
        // elmozdulhatott, az ellenőrzi — aki nem tudja, az téved.
        val certainty = if (!r.community && !r.stable) {
            " Ez a név a gomb helyéhez van kötve, frissítés után elmozdulhat."
        } else {
            ""
        }
        return "${r.label}. $where.$source$certainty"
    }

    private fun updateText() {
        val r = current()
        label.text = if (r == null) {
            "Nincs elnevezés"
        } else {
            val tag = when {
                r.discarded -> " (elvetve)"
                r.community -> " (letöltött)"
                else -> ""
            }
            "${r.label}$tag\n${appNameOf(r.packageName)}\n${index + 1} / ${rows.size}"
        }
    }

    // ── Gesztusok ───────────────────────────────────────────────────────────

    private fun onUp() {
        when (stage) {
            Stage.LIST -> move(-1)
            Stage.ACTION -> moveAction(-1)
            Stage.CONFIRM -> tts.speak("Jobbra igen, balra nem.")
        }
    }

    private fun onDown() {
        when (stage) {
            Stage.LIST -> move(+1)
            Stage.ACTION -> moveAction(+1)
            Stage.CONFIRM -> tts.speak("Jobbra igen, balra nem.")
        }
    }

    private fun onRight() {
        when (stage) {
            Stage.LIST -> openActions()
            Stage.ACTION -> runAction()
            Stage.CONFIRM -> confirmYes()
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
            Stage.CONFIRM -> {
                stage = Stage.ACTION
                pendingAction = ""
                tts.speak("Mégsem.")
            }
        }
    }

    private fun move(delta: Int) {
        if (rows.isEmpty()) return
        index = (index + delta + rows.size) % rows.size
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
        val list = actionsFor(r)
        tts.speak("${r.label}. Mit csináljunk vele? Fel-le választás, jobbra indítás, balra vissza.")
        tts.speakAdd(list[actionIndex])
    }

    private fun runAction() {
        val r = current() ?: return
        when (actionIndex) {
            0 -> openRename(r)
            1 -> speakDetails(r)
            2 -> askConfirm(r)
        }
    }

    private fun askConfirm(r: Row) {
        stage = Stage.CONFIRM
        when {
            r.community && r.discarded -> {
                pendingAction = "visszaker"
                tts.speak("Visszakéred ezt a letöltött nevet: ${r.label}? Jobbra igen, balra nem.")
            }
            r.community -> {
                pendingAction = "elvet"
                tts.speak(
                    "Elveted ezt a letöltött nevet: ${r.label}? Ezután nem mondom ki. " +
                        "Jobbra igen, balra nem."
                )
            }
            else -> {
                pendingAction = "torol"
                tts.speak("Biztosan törlöd ezt a nevet: ${r.label}? Jobbra igen, balra nem.")
            }
        }
    }

    /**
     * ÁTNEVEZÉS, illetve közösséginél SAJÁT NÉV ADÁSA.
     *
     * Ugyanaz az ablak mindkettőre, és ugyanaz a kulcs — ezért lesz a beírt
     * névből azonnal SAJÁT címke, ami a vasszabály szerint veri a közösségit.
     * Nem kell külön "felülírás" fogalom: aki nevet ad, az felülír.
     */
    private fun openRename(r: Row) {
        try {
            startActivity(
                Intent(this, LabelInputActivity::class.java).apply {
                    putExtra(LabelInputActivity.EXTRA_KEY, r.key)
                    putExtra(LabelInputActivity.EXTRA_WHAT, appNameOf(r.packageName))
                }
            )
            stage = Stage.LIST
        } catch (_: Exception) {
            tts.speak("Az átnevező ablak nem nyílt meg.")
        }
    }

    /**
     * RÉSZLETEK: a felhasználónak joga van tudni, MIRE épül a név, és KI adta.
     *
     * Nem technikai kíváncsiság kérdése: aki tudja, hogy egy név bizonytalan
     * azonosításon áll vagy idegentől jött, az ellenőrzi, mielőtt megnyom
     * valamit.
     */
    private fun speakDetails(r: Row) {
        val parts = mutableListOf("${r.label}.", "${appNameOf(r.packageName)}.")
        if (r.community) {
            parts.add("Letöltött név a ${r.packName} csomagból.")
            when (r.origin) {
                "szerkeszto" -> parts.add("Szerkesztő hagyta jóvá.")
                "harman" -> parts.add("Három ember mondta ugyanezt.")
                else -> parts.add("Az eredete nincs feltüntetve.")
            }
            if (r.note.isNotBlank()) parts.add("Megjegyzés: ${r.note}.")
            parts.add(
                if (r.stable) {
                    "Ujjlenyomat tartozik hozzá, ezért akkor is megtalálom, ha az elem elmozdult."
                } else {
                    "Ujjlenyomat nem tartozik hozzá, ezért csak pontos egyezésnél találom meg."
                }
            )
            parts.add("Ha saját nevet adsz neki, a tiéd lesz az érvényes.")
        } else {
            parts.add(
                if (r.stable) {
                    "Saját név, az elem belső azonosítójához kötve. Ez a legerősebb: az alkalmazás frissítése után is jellemzően megtalálja."
                } else {
                    "Saját név. Ennek az elemnek nincs belső azonosítója, ezért a név a gomb helyéhez van kötve, és elmozdulhat."
                }
            )
            parts.add(
                if (ScreenReaderLabels.hasFingerprint(this, r.key)) {
                    "Ujjlenyomat is tartozik hozzá, ezért ha elmozdul, meg tudom találni — de ilyenkor kimondom, hogy csak valószínű."
                } else {
                    "Ujjlenyomat nem tartozik hozzá, mert még a régi módon készült. Ha átnevezed, mostantól lesz."
                }
            )
        }
        tts.speak(parts.joinToString(" "))
    }

    private fun confirmYes() {
        val r = current() ?: return
        when (pendingAction) {
            "torol" -> ScreenReaderLabels.removeByKey(this, r.key)
            "elvet" -> LabelPackStore.discard(this, r.key)
            "visszaker" -> LabelPackStore.undiscard(this, r.key)
            else -> return
        }
        val done = when (pendingAction) {
            "torol" -> "${r.label} törölve."
            "elvet" -> "${r.label} elvetve. Ezután nem mondom ki."
            else -> "${r.label} visszakérve."
        }
        pendingAction = ""
        stage = Stage.LIST
        buildRows()
        if (rows.isEmpty()) {
            index = 0
            updateText()
            tts.speak("$done Nem maradt egyetlen elnevezés sem.")
            return
        }
        if (index >= rows.size) index = rows.size - 1
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
