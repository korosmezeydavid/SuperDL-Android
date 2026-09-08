package com.superdl.launcher.flow

import com.superdl.launcher.calendar.CalendarAction

/**
 * EGY VÁLASZTHATÓ TÉTEL A NAPTÁRI MŰVELET ÖSSZEÁLLÍTÁSÁHOZ.
 *
 * Miért egy közös típus négy különböző listához (menüpont, műveletsor,
 * alkalmazás, címzett): mert a felhasználó szempontjából mind a négy
 * ugyanaz — egy felolvasott lista, amiben fel-le lépked, és jobbra
 * választ. Ha mind a négyhez külön állapotot írnánk, ugyanaz a négy
 * gesztus-ág négyszer szerepelne a diszpécserekben, és a negyediket
 * felejtenénk el bekötni. Ez pontosan az a hiba, ami a podcast modult
 * használhatatlanná tette.
 *
 * Az `action` már KÉSZ művelet, egyetlen kivétellel: SMS-nél a szöveg még
 * üres, mert azt a következő lépésben diktálja be a felhasználó.
 */
data class CalendarActionOption(
    val label: String,
    val action: CalendarAction
)
