package com.superdl.launcher.tts

/**
 * HANGSZEREPEK — hogy hallani lehessen, KI beszél hozzád.
 *
 * A PROBLÉMA:
 * Ma minden ugyanazon a hangon szól: a képernyő tartalma, a program saját
 * üzenete, a hibajelzés, a mozdulat-útmutató. A felhasználónak a SZÖVEGBŐL
 * kell kitalálnia, hogy amit hall, az az alkalmazásból jött-e, vagy a
 * SuperDL mondja neki.
 *
 * Ez fárasztó, és félreértésekhez vezet: "a bank azt mondta, hogy nincs
 * hálózat" — nem, a program mondta.
 *
 * A MEGOLDÁS: szerepenként más hangszín. A felhasználó egy pillanat alatt
 * tudja, ki beszél — anélkül, hogy végighallgatná a mondatot.
 *
 * MIÉRT NEM KÜLÖN BESZÉDMOTOR (egyelőre):
 * A legerősebb megkülönböztetés az lenne, ha az egyik szerepet az eSpeak,
 * a másikat a Google mondaná. DE: nem mindenkinél van két motor telepítve,
 * és két motor párhuzamos futtatása több memóriát eszik — olcsóbb
 * készülékeken ez érezhető.
 * Ezért UGYANAZ a motor szól, más hangmagassággal és tempóval. Ez MINDEN
 * készüléken működik, és ingyen van. A külön motor később hozzáadható.
 */
enum class SpeechRole(
    /** Hangmagasság-szorzó az alaphangoz képest. */
    val pitchFactor: Float,
    /** Tempó-szorzó az alap sebességhez képest. */
    val rateFactor: Float,
    val label: String
) {
    /**
     * A KÉPERNYŐ TARTALMA — amit az alkalmazás mutat.
     * Ez az alap: a felhasználó megszokott hangja, változtatás nélkül.
     */
    CONTENT(1.0f, 1.0f, "tartalom"),

    /**
     * A PROGRAM SAJÁT SZAVA — útmutatók, megerősítések, tájékoztatás.
     * Kicsit MAGASABB és GYORSABB: ez jelzi, hogy nem a képernyőről van szó,
     * és mivel ezeket a felhasználó már ismeri, gyorsabban is hallgathatja.
     */
    SYSTEM(1.15f, 1.08f, "a program üzenete"),

    /**
     * FIGYELMEZTETÉS — hiba, veszély, "nem sikerült".
     * MÉLYEBB és LASSABB. A mélyebb hang ösztönösen komolyabbnak hat, a
     * lassúbb tempó pedig időt ad felfogni.
     */
    WARNING(0.85f, 0.94f, "figyelmeztetés"),

    /**
     * IDÉZET — más ember szava: csevegőben a másik fél üzenete,
     * levélben az idézett rész.
     * Kicsit mélyebb, de nem figyelmeztető: érezhetően "valaki más".
     */
    QUOTE(0.92f, 1.0f, "idézet");
}
