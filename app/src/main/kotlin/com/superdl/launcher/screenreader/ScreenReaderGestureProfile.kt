package com.superdl.launcher.screenreader

internal enum class ScreenReaderGestureProfile(val label: String, val help: String?) {
    SUPER_DL("Super DL", null),
    TALKBACK(
        "TalkBack-stílusú gesztusok",
        "Jobbra a következő, balra az előző elem. Dupla koppintás a megnyitás. " +
            "Fel vagy le az aktív olvasási beállítás szerint. Háromujjas fel vagy le az olvasási beállítás váltása. " +
            "Le, majd balra: vissza. Fel, majd balra: kezdőképernyő. Balra, majd fel: alkalmazásváltó."
    ),
    VOICE_OVER(
        "iPhone-stílusú gesztusok",
        "Jobbra a következő, balra az előző elem. Dupla koppintás a megnyitás. " +
            "Két ujj forgatása: rotor. Egy ujjal fel vagy le a rotor szerint lép. " +
            "Két ujjal fel: felolvasás az elejétől. Két ujjal le: felolvasás innen. " +
            "Három ujjal fel vagy le: lapozás. Két ujjal Z mozdulat: vissza. " +
            "Az alsó szél közeli sávból húzz felfelé. A második hangnál engedd el a kezdőképernyőhöz, " +
            "a harmadiknál az alkalmazásváltóhoz. Felülről lefelé a második hang a gyorsbeállítások, " +
            "a harmadik az értesítések."
    )
}
