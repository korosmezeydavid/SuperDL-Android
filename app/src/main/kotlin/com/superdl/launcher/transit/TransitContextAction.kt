package com.superdl.launcher.transit

enum class TransitContextAction(val label: String) {
    SPEAK_FULL("Részletes felolvasás"),
    WATCH_RIDE("Célmegálló figyelésének beállítása"),
    SAVE_FAVORITE("Megálló mentése kedvencekbe"),
    REMOVE_FAVORITE("Kedvenc törlése"),
    TOGGLE_RADIUS("Keresési kör váltása"),
    REFRESH("Frissítés")
}
