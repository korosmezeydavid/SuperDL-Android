package com.superdl.launcher.calendar

enum class CalendarContextAction(val label: String) {
    READ("Program felolvasása"),
    EDIT("Program szerkesztése"),
    // A MŰVELET A TÖRLÉS ELÉ KERÜL, és nem véletlenül: a törlés
    // visszafordíthatatlan, ezért ne az legyen az, amibe véletlenül
    // belefut, aki csak lépked a listában.
    ACTION_ASSIGN("Művelet hozzárendelése"),
    ACTION_REMOVE("Hozzárendelt művelet törlése"),
    DELETE("Program törlése");

    companion object {
        val browseActions: List<CalendarContextAction> =
            listOf(READ, EDIT, ACTION_ASSIGN, ACTION_REMOVE, DELETE)
        val alarmActions: List<CalendarAlarmAction> = CalendarAlarmAction.entries.toList()
    }
}

enum class CalendarAlarmAction(val label: String) {
    REMIND_ONE_HOUR("Emlékeztetés 1 óra múlva"),
    MARK_COMPLETE("Megjelölés teljesítettként");
}