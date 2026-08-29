package com.superdl.launcher.info

import com.superdl.launcher.weather.WeatherHelper

object DayInfoHelper {

    fun speakLocal(): String {
        val datePart = InfoHelper.speakDateTime()
        val namedayPart = NamedayHelper.speakToday()
        return if (namedayPart.isBlank()) datePart else "$datePart $namedayPart"
    }

    /**
     * @param context a telefon SAJÁT HELYZETÉHEZ kell — enélkül az időjárás
     *        az internetkapcsolatból találgatna, és rossz várost mondana.
     */
    fun fetchGreeting(
        context: android.content.Context? = null,
        includeWeather: Boolean = true,
        onResult: (String) -> Unit
    ) {
        val local = speakLocal()
        if (!includeWeather) {
            onResult(local)
            return
        }
        WeatherHelper.fetch(
            context = context,
            city = null,
            onResult = { weather -> onResult("$local ${weather.speakSummary()}") },
            onError = { onResult(local) }
        )
    }
}