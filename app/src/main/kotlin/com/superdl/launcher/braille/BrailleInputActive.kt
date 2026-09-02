package com.superdl.launcher.braille

/**
 * „ÉPP BRAILLE-T ÍROK — NE VEDD EL AZ ÉRINTÉSEIMET."
 *
 * MIÉRT KELL EZ (2026-09-02, tesztelői visszajelzés):
 *
 * A képernyőolvasó a saját felületünkön is elkapja a mozdulatokat. A mátrix
 * billentyűzetnél ezt már megoldottuk: az olvasó észreveszi, hogy beviteli
 * ablak (`TYPE_INPUT_METHOD`) van a képernyőn, és félreáll. A Braille
 * próbapad viszont NEM beviteli ablak, hanem sima képernyő — vagyis arra a
 * felismerésre vak. Az eredmény: a próbapad minden érintést magának kért, az
 * olvasó söprései pedig nem jutottak el sehova, és a visszalépés se működött.
 *
 * Ez a kapcsoló mondja meg az olvasónak, hogy most Braille-bevitel folyik.
 * Egyetlen `@Volatile` logikai érték, mert két különböző szál olvassa és
 * írja: a próbapad felülete és a szolgáltatás.
 *
 * FONTOS: aki bekapcsolja, annak KÖTELESSÉGE kikapcsolni is — az `onPause`-ban,
 * nem az `onDestroy`-ban. Ha a felület valamiért nem semmisül meg rendesen, a
 * ragadt kapcsoló némává tenné az egész telefont. Ezért van a mindent
 * helyreállító `release()` is.
 */
object BrailleInputActive {

    @Volatile
    private var active = false

    val isActive: Boolean get() = active

    fun hold() {
        active = true
    }

    fun release() {
        active = false
    }
}
