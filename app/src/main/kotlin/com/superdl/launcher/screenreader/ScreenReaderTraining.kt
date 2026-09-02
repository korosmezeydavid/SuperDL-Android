package com.superdl.launcher.screenreader

import android.accessibilityservice.AccessibilityService
import com.superdl.launcher.gestures.GestureOrientation

/**
 * GESZTUS-TANULÁS — Elena tanárnő órái.
 *
 * HÁROM MÓD:
 *  1. SZABAD GYAKORLÁS — tét nélkül, a gép megmondja, mit csináltál volna.
 *  2. ÓRÁK — az utasítás MEGMONDJA a mozdulatot, mert itt még tanulunk.
 *  3. VIZSGA — a feladat CSAK A CÉLT mondja meg, a mozdulatot NEM.
 *     A végén ÖSSZETETT feladatok jönnek: több lépéses élethelyzetek.
 */
object ScreenReaderTraining {

    /**
     * Egy tanóra.
     * @param instruction ÓRÁN: megmondja a mozdulatot is
     * @param examTask VIZSGÁN: csak a CÉLT mondja meg, a mozdulatot nem
     */
    data class Lesson(
        val gestureId: Int,
        val name: String,
        val whatItDoes: String,
        val instruction: String,
        val examTask: String
    )

    /**
     * AZ ÓRÁK, ELFORGATÁSSAL EGYÜTT.
     *
     * MIÉRT NEM EGYSZERŰ LISTA: a `RAW_LESSONS` az ALAP kezeléshez íródott —
     * ott a lefelé söprés a következő elem. Elforgatott módban ez nem igaz,
     * és a tanulás közben hazudni a legrosszabb, amit tehetünk: aki most
     * ismerkedik a mozdulatokkal, a hibát MAGÁRA VESZI, nem a programra.
     *
     * Ezért a négy alapmozdulat óráját a jelenlegi kezelésből építjük fel:
     * a CÉL marad ugyanaz („lépj a következő elemre"), a MOZDULAT az, ami
     * követi az elforgatást. A vizsga válasza is így stimmel — különben a
     * helyes mozdulatot hibának néznénk.
     */
    val LESSONS: List<Lesson> get() = RAW_LESSONS.map { orientAware(it) }

    private fun orientAware(lesson: Lesson): Lesson {
        val logical = when (lesson.gestureId) {
            AccessibilityService.GESTURE_SWIPE_DOWN -> GestureOrientation.Logical.NEXT
            AccessibilityService.GESTURE_SWIPE_UP -> GestureOrientation.Logical.PREVIOUS
            AccessibilityService.GESTURE_SWIPE_RIGHT -> GestureOrientation.Logical.ENTER
            AccessibilityService.GESTURE_SWIPE_LEFT -> GestureOrientation.Logical.BACK
            // Az összetett mozdulatok (le-majd-fel, két ujjal…) nem
            // navigálnak, hanem külön funkciót indítanak — azokat nem
            // forgatjuk el, tehát az órájuk is változatlan.
            else -> return lesson
        }
        val physical = GestureOrientation.physicalOf(logical)
        if (physical == normalPhysicalOf(logical)) return lesson

        return lesson.copy(
            gestureId = gestureIdOf(physical),
            name = "söprés " + directionWord(physical),
            instruction = instructionFor(logical, directionWord(physical).uppercase())
        )
    }

    private fun normalPhysicalOf(logical: GestureOrientation.Logical) = when (logical) {
        GestureOrientation.Logical.NEXT -> GestureOrientation.Physical.DOWN
        GestureOrientation.Logical.PREVIOUS -> GestureOrientation.Physical.UP
        GestureOrientation.Logical.ENTER -> GestureOrientation.Physical.RIGHT
        GestureOrientation.Logical.BACK -> GestureOrientation.Physical.LEFT
    }

    private fun gestureIdOf(physical: GestureOrientation.Physical) = when (physical) {
        GestureOrientation.Physical.UP -> AccessibilityService.GESTURE_SWIPE_UP
        GestureOrientation.Physical.DOWN -> AccessibilityService.GESTURE_SWIPE_DOWN
        GestureOrientation.Physical.LEFT -> AccessibilityService.GESTURE_SWIPE_LEFT
        GestureOrientation.Physical.RIGHT -> AccessibilityService.GESTURE_SWIPE_RIGHT
    }

    private fun directionWord(physical: GestureOrientation.Physical) = when (physical) {
        GestureOrientation.Physical.UP -> "felfelé"
        GestureOrientation.Physical.DOWN -> "lefelé"
        GestureOrientation.Physical.LEFT -> "balra"
        GestureOrientation.Physical.RIGHT -> "jobbra"
    }

    private fun instructionFor(logical: GestureOrientation.Logical, dir: String) = when (logical) {
        GestureOrientation.Logical.NEXT ->
            "Ez egy hosszú lista. Söpörj $dir, hogy a következő elemre lépj."
        GestureOrientation.Logical.PREVIOUS ->
            "Most vissza. Söpörj $dir az előző elemhez."
        GestureOrientation.Logical.ENTER ->
            "Találtál egy gombot. Söpörj $dir, hogy megnyomd."
        GestureOrientation.Logical.BACK ->
            "Meggondoltad magad. Söpörj $dir a visszalépéshez."
    }

    private val RAW_LESSONS: List<Lesson> = listOf(
        // ── ALAPOK ──────────────────────────────────────────────────────────
        Lesson(
            AccessibilityService.GESTURE_SWIPE_DOWN, "söprés lefelé",
            "a következő elemre lép",
            "Ez egy hosszú lista. Söpörj LEFELÉ, hogy a következő elemre lépj.",
            "Lépj a KÖVETKEZŐ elemre."
        ),
        Lesson(
            AccessibilityService.GESTURE_SWIPE_UP, "söprés felfelé",
            "az előző elemre lép",
            "Most vissza. Söpörj FELFELÉ az előző elemhez.",
            "Lépj vissza az ELŐZŐ elemre."
        ),
        Lesson(
            AccessibilityService.GESTURE_SWIPE_RIGHT, "söprés jobbra",
            "megnyomja az elemet, igen-nem kérdésnél IGEN, csörgő hívásnál FOGADÁS",
            "Találtál egy gombot. Söpörj JOBBRA, hogy megnyomd.",
            "Nyomd meg a kiválasztott gombot."
        ),
        Lesson(
            AccessibilityService.GESTURE_SWIPE_LEFT, "söprés balra",
            "visszalép, igen-nem kérdésnél NEM, csörgő hívásnál ELUTASÍTÁS",
            "Meggondoltad magad. Söpörj BALRA a visszalépéshez.",
            "Lépj vissza."
        ),
        // ── GÖRGETÉS ÉS UGRÁS ───────────────────────────────────────────────
        Lesson(
            AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP, "le majd fel",
            "görget lefelé egy képernyőnyit",
            "A lista nem fér ki. Söpörj LE, majd anélkül hogy felemelnéd, FEL.",
            "Görgess LEFELÉ egy képernyőnyit."
        ),
        Lesson(
            AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN, "fel majd le",
            "görget felfelé",
            "Most vissza felfelé. Söpörj FEL, majd LE.",
            "Görgess FELFELÉ."
        ),
        Lesson(
            AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT, "balra majd jobbra",
            "a lista első elemére ugrik",
            "Ugorj a lista elejére: söpörj BALRA, majd JOBBRA.",
            "Ugorj a lista ELSŐ elemére."
        ),
        Lesson(
            AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT, "jobbra majd balra",
            "a lista utolsó elemére ugrik",
            "Most a lista végére: söpörj JOBBRA, majd BALRA.",
            "Ugorj a lista UTOLSÓ elemére."
        ),
        // ── RENDSZER-MŰVELETEK ──────────────────────────────────────────────
        Lesson(
            AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT, "fel majd balra",
            "kilép a kezdőképernyőre",
            "Haza akarsz menni. Söpörj FEL, majd BALRA.",
            "Menj a KEZDŐKÉPERNYŐRE."
        ),
        Lesson(
            AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT, "fel majd jobbra",
            "megnyitja a legutóbbi alkalmazásokat",
            "Vissza akarsz lépni egy másik alkalmazásba. Söpörj FEL, majd JOBBRA.",
            "Nyisd meg a LEGUTÓBBI ALKALMAZÁSOKAT."
        ),
        Lesson(
            AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT, "le majd balra",
            "lehúzza az értesítéseket",
            "Nézzük meg az értesítéseket: söpörj LE, majd BALRA.",
            "Húzd le az ÉRTESÍTÉSEKET."
        ),
        Lesson(
            AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT, "le majd jobbra",
            "hosszan megnyomja az elemet, előhozza a rejtett lehetőségeket",
            "Egy elemnek rejtett lehetőségei vannak. Söpörj LE, majd JOBBRA.",
            "Nyomd meg HOSSZAN a kiválasztott elemet."
        ),
        // ── KÉT UJJAL: MIT olvasunk ─────────────────────────────────────────
        Lesson(
            AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT, "két ujjal jobbra",
            "a következő navigációs módra vált: minden elem, címsorok, hivatkozások, gombok",
            "Most jönnek a KÉT UJJAS mozdulatok. Söpörj KÉT UJJAL JOBBRA — módot váltasz.",
            "Válts a KÖVETKEZŐ navigációs módra."
        ),
        Lesson(
            AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT, "két ujjal balra",
            "az előző navigációs módra vált",
            "Vissza az előző módra: KÉT UJJAL BALRA.",
            "Válts az ELŐZŐ navigációs módra."
        ),
        Lesson(
            AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN, "két ujjal lefelé",
            "folyamatosan felolvassa az egész képernyőt",
            "Olvastasd fel az egészet: KÉT UJJAL LEFELÉ.",
            "Olvastasd fel FOLYAMATOSAN az egész képernyőt."
        ),
        Lesson(
            AccessibilityService.GESTURE_2_FINGER_SWIPE_UP, "két ujjal felfelé",
            "megismétli az utolsó mondatot",
            "Nem értetted? KÉT UJJAL FELFELÉ, és megismétli.",
            "Ismételtesd meg az UTOLSÓ MONDATOT."
        ),
        Lesson(
            AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP, "két ujjal dupla koppintás",
            "keresést indít a képernyőn, hanggal",
            "Keresni akarsz valamit a képernyőn. KOPPINTS KÉT UJJAL, KÉTSZER.",
            "Keress valamit a képernyőn, hanggal."
        ),
        Lesson(
            AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP, "két ujjal hármas koppintás",
            "felolvassa a színeket: piros hibaüzenet, zöld megerősítés",
            "Tudni akarod, milyen színű. KOPPINTS KÉT UJJAL, HÁROMSZOR.",
            "Tudd meg, MILYEN SZÍNŰ az elem."
        ),
        // ── HÁROM UJJAL: HOGYAN olvassuk ────────────────────────────────────
        Lesson(
            AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT, "három ujjal jobbra",
            "részletesebb olvasásra vált: elem, mondat, szó, betű",
            "Most a HÁROM UJJAS mozdulatok. Söpörj HÁROM UJJAL JOBBRA.",
            "Válts RÉSZLETESEBB olvasásra."
        ),
        Lesson(
            AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT, "három ujjal balra",
            "durvább olvasásra vált",
            "Vissza durvábbra: HÁROM UJJAL BALRA.",
            "Válts DURVÁBB olvasásra."
        ),
        Lesson(
            AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN, "három ujjal lefelé",
            "megmondja, hol vagy: alkalmazás, mód, pozíció",
            "Elvesztél? Kérdezd meg: HÁROM UJJAL LEFELÉ.",
            "Kérdezd meg, HOL VAGY."
        ),
        Lesson(
            AccessibilityService.GESTURE_3_FINGER_SWIPE_UP, "három ujjal felfelé",
            "felolvassa a súgót",
            "Elfelejtetted a mozdulatokat? HÁROM UJJAL FELFELÉ a súgóhoz.",
            "Hallgasd meg a SÚGÓT."
        ),
        Lesson(
            AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP, "három ujjal dupla koppintás",
            "felolvassa, mi van a képen",
            "Egy kép van előtted, felirat nélkül. KOPPINTS HÁROM UJJAL, KÉTSZER.",
            "Olvastasd fel, MI VAN A KÉPEN."
        ),
        Lesson(
            AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP, "három ujjal hármas koppintás",
            "elnevezi a névtelen elemet, amit te mondasz be",
            "Egy gombnak nincs neve. KOPPINTS HÁROM UJJAL, HÁROMSZOR, és elnevezheted.",
            "Nevezd el a névtelen gombot."
        ),
        // ── NÉGY UJJAL: TÁBLÁZAT ────────────────────────────────────────────
        Lesson(
            AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN, "négy ujjal lefelé",
            "táblázatban egy sorral lejjebb lép",
            "Táblázatban vagy. NÉGY UJJAL LEFELÉ: egy sorral lejjebb.",
            "Táblázatban lépj egy SORRAL LEJJEBB."
        ),
        Lesson(
            AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT, "négy ujjal jobbra",
            "táblázatban egy oszloppal jobbra lép",
            "Most oszlopot váltunk: NÉGY UJJAL JOBBRA.",
            "Táblázatban lépj egy OSZLOPPAL JOBBRA."
        )
    )

    // ── ÖSSZETETT FELADATOK (a vizsga vége) ─────────────────────────────────

    /**
     * Egy összetett feladat: TÖBB mozdulat egymás után, élethelyzetbe ágyazva.
     *
     * MIÉRT KELL: egyetlen mozdulat ismerete még nem használható tudás. Az
     * igazi kérdés, hogy valaki egy VALÓS helyzetben — csörög a telefon,
     * keresni kell valamit, egy névtelen gombot el kell nevezni — tudja-e,
     * mit csináljon, és milyen SORRENDBEN.
     *
     * @param steps a helyes mozdulatok sorrendben
     * @param timeLimitSec ha nagyobb nullánál, ennyi idő alatt kell megoldani
     *        (pl. csörgő telefonnál — ott az élet sem vár)
     */
    data class CompositeTask(
        val prompt: String,
        val steps: List<Int>,
        val stepHints: List<String>,
        val timeLimitSec: Int = 0,
        val timeoutMessage: String = ""
    )

    val COMPOSITE_TASKS: List<CompositeTask> = listOf(
        CompositeTask(
            prompt = "Csörög a telefonod! Fogadd a hívást — de siess, tíz másodperced van.",
            steps = listOf(AccessibilityService.GESTURE_SWIPE_RIGHT),
            stepHints = listOf("fogadás"),
            timeLimitSec = 10,
            timeoutMessage = "Lekésted a hívást. Ez egy hiba."
        ),
        CompositeTask(
            prompt = "Megint csörög, de most nem érsz rá. Utasítsd el a hívást.",
            steps = listOf(AccessibilityService.GESTURE_SWIPE_LEFT),
            stepHints = listOf("elutasítás"),
            timeLimitSec = 10,
            timeoutMessage = "Elkésted. A hívó feladta."
        ),
        CompositeTask(
            prompt = "Egy hosszú listában vagy, és a lista legelső eleme kell. " +
                "Ugorj oda, majd nyomd meg.",
            steps = listOf(
                AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT,
                AccessibilityService.GESTURE_SWIPE_RIGHT
            ),
            stepHints = listOf("ugrás az elsőre", "megnyomás")
        ),
        CompositeTask(
            prompt = "Egy weboldalon vagy. Válts a gombokra mint navigációs módra, " +
                "majd lépj a következő gombra, és nyomd meg.",
            steps = listOf(
                AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT,
                AccessibilityService.GESTURE_SWIPE_DOWN,
                AccessibilityService.GESTURE_SWIPE_RIGHT
            ),
            stepHints = listOf("módváltás", "következő elem", "megnyomás")
        ),
        CompositeTask(
            prompt = "Találtál egy gombot, aminek nincs neve. Nevezd el, " +
                "hogy legközelebb tudd, mi az.",
            steps = listOf(AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP),
            stepHints = listOf("elnevezés")
        ),
        CompositeTask(
            prompt = "Nem tudod, hol vagy a telefonon. Derítsd ki, majd hallgasd " +
                "meg a súgót is.",
            steps = listOf(
                AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN,
                AccessibilityService.GESTURE_3_FINGER_SWIPE_UP
            ),
            stepHints = listOf("hol vagyok", "súgó")
        ),
        CompositeTask(
            prompt = "Egy hosszú cikket kaptál. Olvastasd fel az egészet, " +
                "majd ismételtesd meg az utolsó mondatot.",
            steps = listOf(
                AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN,
                AccessibilityService.GESTURE_2_FINGER_SWIPE_UP
            ),
            stepHints = listOf("folyamatos olvasás", "ismétlés")
        ),
        CompositeTask(
            prompt = "Egy jelszót kell betűznöd. Válts részletesebb olvasásra kétszer, " +
                "hogy betűnként haladj.",
            steps = listOf(
                AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT,
                AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT
            ),
            stepHints = listOf("részletesebb", "még részletesebb")
        )
    )

    /** A vizsga teljes hossza: minden mozdulat + minden összetett feladat. */
    val EXAM_LENGTH: Int get() = LESSONS.size + COMPOSITE_TASKS.size

    /** Minden gesztus emberi neve — a szabad gyakorláshoz. */
    fun nameOf(gestureId: Int): String = when (gestureId) {
        AccessibilityService.GESTURE_SWIPE_DOWN -> "söprés lefelé"
        AccessibilityService.GESTURE_SWIPE_UP -> "söprés felfelé"
        AccessibilityService.GESTURE_SWIPE_RIGHT -> "söprés jobbra"
        AccessibilityService.GESTURE_SWIPE_LEFT -> "söprés balra"
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP -> "le majd fel"
        AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN -> "fel majd le"
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT -> "balra majd jobbra"
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT -> "jobbra majd balra"
        AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT -> "fel majd balra"
        AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT -> "fel majd jobbra"
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT -> "le majd balra"
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT -> "le majd jobbra"
        AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT -> "két ujjal jobbra"
        AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT -> "két ujjal balra"
        AccessibilityService.GESTURE_2_FINGER_SWIPE_UP -> "két ujjal felfelé"
        AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN -> "két ujjal lefelé"
        AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP -> "két ujjal dupla koppintás"
        AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP -> "két ujjal hármas koppintás"
        AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT -> "három ujjal jobbra"
        AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT -> "három ujjal balra"
        AccessibilityService.GESTURE_3_FINGER_SWIPE_UP -> "három ujjal felfelé"
        AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN -> "három ujjal lefelé"
        AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP -> "három ujjal dupla koppintás"
        AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP -> "három ujjal hármas koppintás"
        AccessibilityService.GESTURE_4_FINGER_SWIPE_UP -> "négy ujjal felfelé"
        AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN -> "négy ujjal lefelé"
        AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT -> "négy ujjal balra"
        AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT -> "négy ujjal jobbra"
        AccessibilityService.GESTURE_DOUBLE_TAP -> "dupla koppintás"
        else -> "ismeretlen mozdulat"
    }

    /** Mit csinálna ez a mozdulat élesben — a szabad gyakorláshoz. */
    fun effectOf(gestureId: Int): String =
        LESSONS.firstOrNull { it.gestureId == gestureId }?.let { "ezzel ${it.whatItDoes}" }
            ?: when (gestureId) {
                AccessibilityService.GESTURE_4_FINGER_SWIPE_UP ->
                    "táblázatban egy sorral feljebb lépnél"
                AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT ->
                    "táblázatban egy oszloppal balra lépnél"
                else -> "ezzel a mozdulattal nem történne semmi"
            }

    // ── ELENA TANÁRNŐ OSZTÁLYZATAI ─────────────────────────────────────────

    /**
     * Osztályzat a hibák száma alapján.
     *
     * ÚJRASZÁMOLVA a bővített vizsgához: 26 mozdulat + 8 összetett feladat =
     * 34 feladat. A korábbi 13 feladatos rendszer arányait tartjuk meg, tehát
     * a küszöbök is arányosan nőttek — nem lett se könnyebb, se nehezebb.
     *
     *   0-2 hiba  -> jeles      (a feladatok 94 százaléka hibátlan)
     *   3-5       -> jó
     *   6-9       -> közepes
     *   10-13     -> elégséges
     *   14 hibától a vizsga MEGSZAKAD
     */
    fun grade(errors: Int): Int = when {
        errors <= 2 -> 5
        errors <= 5 -> 4
        errors <= 9 -> 3
        errors <= 13 -> 2
        else -> 1
    }

    /** Elena tanárnő szavai — a jegyhez illő hangnemben. */
    fun teacherComment(errors: Int): String = when (grade(errors)) {
        5 -> "Jeles! Ez kifogástalan volt. Elena tanárnő büszke rád — " +
            "mostantól bátran használhatod a képernyőolvasót bármelyik alkalmazásban."
        4 -> "Jó. Csak apró bizonytalanság volt, a lényeget tudod. " +
            "Elena tanárnő szerint pár nap gyakorlás, és jeles leszel."
        3 -> "Közepes. Az alapokkal megvagy, de a több ujjas mozdulatok még nem ülnek. " +
            "Elena tanárnő azt üzeni: ne add fel, ez a nehezebbik része."
        2 -> "Elégséges. Épphogy átcsúsztál. " +
            "Elena tanárnő kedvesen, de határozottan javasolja az órák újrajárását."
        else -> "Elégtelen. Mész vissza a padba tanulni! " +
            "Elena tanárnő nem haragszik — mindenki így kezdte. Kezdd az órákkal, " +
            "és gyere vissza, ha begyakoroltad."
    }

    /** Ennyi hibánál a vizsga megszakad. */
    const val MAX_ERRORS = 14
}
