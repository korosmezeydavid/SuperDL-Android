package com.superdl.launcher.screenreader

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.superdl.launcher.gestures.SwipeGestureListener
import com.superdl.launcher.report.BugReportSender
import com.superdl.launcher.tts.TtsManager

/**
 * ELNEVEZÉSEK BEKÜLDÉSE — a jóváhagyás, és a három küldési út.
 *
 * MIÉRT UGYANAZ A SZEMLÉLET, MINT A HIBAJELENTŐNÉL: a tesztelő nem konfigurál
 * semmit. Nem ad meg címet, nem állít be fiókot, nem választ formátumot. Csak
 * MEGHALLGATJA, mit küldene, és jóváhagyja.
 *
 * MIÉRT HÁROM ÚT: mert a tesztelők nagyon eltérő tudásúak. Van, aki soha nem
 * állított be e-mailt, és van, aki hálózat nélküli helyen próbálja. Egyetlen
 * úttal a beküldések fele elveszne — és pont az veszne el, ami a legértékesebb.
 *
 * A LEGFONTOSABB, AMIT EZ AZ ABLAK CSINÁL: KIMONDJA, MI MEGY EL.
 * Nem apró betűben, nem beállításban — hanem elsőként, hangosan, mielőtt
 * bármit jóváhagynál. Ha egy vak felhasználó nem tudja pontosan, mit küld el
 * magáról, akkor a beküldés nem önkéntes, hanem kockázat.
 */
class LabelSubmitActivity : Activity() {

    private lateinit var tts: TtsManager
    private lateinit var gestureListener: SwipeGestureListener
    private lateinit var label: TextView

    private var bundle: LabelSharing.Bundle? = null

    private enum class Stage { CONFIRM, ROUTE }

    private var stage = Stage.CONFIRM
    private var routeIndex = 0

    private val routes = listOf(
        "Levelezővel" to "Megnyílik a telefonon beállított levelező, készen kitöltve.",
        "Mentés fájlba" to "A telefonra mentjük. Hálózat nélkül is működik, később elküldhető.",
        "Megosztás" to "Üzenetküldővel — WhatsApp, Messenger, ahogy megszoktad."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(32, 32, 32, 32)
        }
        label = TextView(this).apply {
            textSize = 22f
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

        bundle = LabelSharing.build(this)
        val b = bundle
        if (b == null) {
            label.text = "Nincs mit beküldeni"
            tts.speak(
                "Nincs beküldhető elnevezésed. Csak azok küldhetők be, amikhez " +
                    "ujjlenyomat is tartozik — a régebbi nevekhez ez hiányzik, " +
                    "de ha átnevezed őket, elkészül."
            )
            label.postDelayed({ finish() }, 9000L)
            return
        }

        label.text = "${b.labelCount} elnevezés\n${b.appCount} alkalmazásból"
        tts.speak("Elnevezések beküldése. ${b.summary}")
        tts.speakAdd("Jobbra: küldés. Balra: mégsem.")
    }

    private fun onUp() {
        when (stage) {
            Stage.CONFIRM -> repeatSummary()
            Stage.ROUTE -> moveRoute(-1)
        }
    }

    private fun onDown() {
        when (stage) {
            Stage.CONFIRM -> repeatSummary()
            Stage.ROUTE -> moveRoute(+1)
        }
    }

    /**
     * MIÉRT ISMÉTELHETŐ: egy hosszú felsorolást elsőre nem lehet megjegyezni,
     * és aki nem biztos benne, mit küld, az inkább nem küld semmit. Egy
     * söprés, és újra elhangzik.
     */
    private fun repeatSummary() {
        val b = bundle ?: return
        tts.speak("${b.summary} Jobbra: küldés. Balra: mégsem.")
    }

    private fun moveRoute(delta: Int) {
        routeIndex = (routeIndex + delta + routes.size) % routes.size
        val r = routes[routeIndex]
        label.text = r.first
        tts.speak("${r.first}. ${r.second}")
    }

    private fun onRight() {
        when (stage) {
            Stage.CONFIRM -> {
                stage = Stage.ROUTE
                routeIndex = 0
                val r = routes[routeIndex]
                label.text = r.first
                tts.speak("Hogyan küldjük? Fel-le választás, jobbra indítás, balra vissza.")
                tts.speakAdd("${r.first}. ${r.second}")
            }
            Stage.ROUTE -> send()
        }
    }

    private fun onLeft() {
        when (stage) {
            Stage.CONFIRM -> {
                tts.speak("Nem küldtem el semmit.")
                label.postDelayed({ finish() }, 1500L)
            }
            Stage.ROUTE -> {
                stage = Stage.CONFIRM
                val b = bundle
                label.text = "${b?.labelCount} elnevezés\n${b?.appCount} alkalmazásból"
                tts.speak("Vissza. Jobbra: küldés. Balra: mégsem.")
            }
        }
    }

    private fun send() {
        val b = bundle ?: return
        val subject = "SuperDL elnevezések — ${b.labelCount} darab"
        val ok = when (routeIndex) {
            0 -> BugReportSender.sendWithMailApp(this, b.json, subject)
            1 -> {
                val file = BugReportSender.saveToFile(this, b.json)
                if (file != null) {
                    tts.speak("Elmentve: ${file.name}. A WiFi portálról letöltheted.")
                }
                file != null
            }
            else -> BugReportSender.share(this, b.json, subject)
        }

        if (ok) {
            LabelSharing.markSent(this)
            if (routeIndex != 1) tts.speak("Kész. Köszönöm.")
            label.postDelayed({ finish() }, 2500L)
        } else {
            tts.speak("Ez az út most nem működik. Próbálj másikat: fel-le választás.")
            stage = Stage.ROUTE
        }
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
