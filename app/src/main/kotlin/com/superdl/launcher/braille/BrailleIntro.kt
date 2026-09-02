package com.superdl.launcher.braille

import android.content.Context

/**
 * A BEMUTATÓ MONDAT — a próbapad és az éles billentyűzet ugyanazt mondja.
 *
 * Ha kétszer lenne megírva, az egyik elavulna, és a felhasználó két
 * különböző leírást hallana ugyanarról a mozdulatrendszerről.
 */
object BrailleIntro {

    fun text(context: Context, recognizer: BrailleTouchRecognizer, title: String): String {
        if (recognizer.isRail) {
            return "$title Sín mód. Egyetlen ujjal írsz. Tedd le az ujjad bárhova: " +
                "az lesz a sín közepe, a második sor. Fel-le mozgatva választasz " +
                "sort, balra lépve a sor bal pontját ütöd le, jobbra lépve a jobbat. " +
                "Visszatérsz a sínre, és jöhet a következő pont. Ugyanoda visszalépve " +
                "visszavonod. Ha megállsz, bemondom, hol vagy. Felengeded: kész a betű. " +
                "Pont nélkül felengedve szóköz. Két ujjal koppintva törlés, három " +
                "ujjal felolvasás. Az ujjad messze lehúzva az egész cellát törlöd. " +
                "Összecsippentés két ujjal: kilépés."
        }
        val hold = BrailleLayoutPrefs.orientation(context).speakHold()
        val where = BrailleLayoutPrefs.layout(context).speakDescription()
        val how = if (recognizer.isTwoPass) {
            "Két menetben írsz: előbb a bal oszlop pontjait teszed le, felengeded, " +
                "aztán a jobb oszlopét. A kettő együtt ad egy betűt."
        } else {
            "Egyszerre teheted le a betű összes pontját. Leteszed, felengeded, kész."
        }
        return "$title $hold $where $how " +
            "Söprés jobbra: szóköz. Söprés balra: törlés. Három ujjal balra: szó törlése. " +
            "Söprés lefelé: felolvasás. Összecsippentés két ujjal: kilépés."
    }
}
