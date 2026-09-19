package com.superdl.launcher.setup

import com.superdl.launcher.menu.MenuAction

/**
 * „SZERETNÉD KIPRÓBÁLNI, VAGY TOVÁBBLÉPÜNK?"
 *
 * MIÉRT VAN (Alph és Tamás Bálint, 2026-09-19):
 *
 * A tanuló módok eddig is megvoltak — csak épp a menü mélyén. Aki most
 * kapta életében az első androidos telefont, az nem fogja átkutatni értük
 * a készüléket; nem is tudja, hogy léteznek. Aki viszont már belejött,
 * annak egy kötelező bemutató csak nyűg.
 *
 * Ezért a varázsló VÉGÉN, EGYSZER felajánljuk — és nem kötelezzük rá
 * senkit. Ez az általános eljárás a játékoknál is: az elején kérdez, utána
 * nem erőlteti. Aki most nemet mond, később a menüből bármikor előveheti;
 * ezt ki is mondjuk neki, hogy ne érezze úgy, elszalasztott valamit.
 *
 * MIÉRT A VARÁZSLÓ UTÁN, ÉS NEM ELŐTTE: a tanuló módhoz már működő
 * programra van szükség — beszédre, gesztusokra, néhány engedélyre. Előbb
 * legyen mivel gyakorolni.
 */
enum class FirstLesson(
    val label: String,
    /** Mit kap, ha ezt választja — ezt mondjuk ki a soron. */
    val detail: String,
    /** Melyik menüpont indul el. Null: nem indul semmi, kezdjük a használatot. */
    val action: MenuAction?
) {

    PLAYGROUND(
        "Tanuló mód: a funkciók kipróbálása",
        "Végigvesszük, mit tud a program. Itt semmi nem él éles, " +
            "nem indul hívás és nem megy el üzenet — nyugodtan nyomkodhatod.",
        MenuAction.TRAINING_PLAYGROUND
    ),

    GESTURES(
        "Gesztus-órák Elena tanárnővel",
        "Megtanulod a söpréseket és a koppintásokat, lépésről lépésre. " +
            "A végén vizsgázhatsz is, ha akarsz.",
        MenuAction.SR_TRAIN_LESSONS
    ),

    SOUNDS(
        "A program hangjainak megismerése",
        "Megmutatom, melyik hang mit jelent: mi a siker, mi a hiba, " +
            "mi az, hogy a lista végére értél.",
        MenuAction.SOUND_TRAINING
    ),

    LATER(
        "Most nem, kezdjük el a használatot",
        "Rendben. Bármelyiket bármikor előveheted később is.",
        null
    );

    companion object {
        val ALL: List<FirstLesson> = entries.toList()

        /** A nyitó kérdés. Rövid: aki most végzett a varázslóval, már sokat hallott. */
        const val QUESTION: String =
            "Kész a beállítás. Szeretnéd előbb kipróbálni a programot tanuló módban, " +
                "vagy vágjunk bele? Fel-le söpréssel válogatsz, jobbra választasz."
    }
}
