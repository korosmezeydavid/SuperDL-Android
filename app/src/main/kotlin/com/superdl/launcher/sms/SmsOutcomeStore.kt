package com.superdl.launcher.sms

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AZ UTOLSÓ ELKÜLDÖTT ÜZENET SORSA.
 *
 * MIÉRT KELL, ÉS MIÉRT NEM ELÉG A KÜLDÉS PILLANATA:
 *
 * Az SMS-nek három állapota van, és ezek IDŐBEN szétesnek:
 *
 *   1. a program átadta a rendszernek        — azonnal
 *   2. a hálózat átvette                     — pár másodperc
 *   3. a címzett készüléke megkapta          — másodpercek vagy PERCEK
 *
 * A program eddig csak az elsőt nézte, és arra mondta, hogy „Üzenet
 * elküldve." A `SmsSendReceiver` a másodikat és a harmadikat is megkapta,
 * emberi nyelvre is fordította — de SENKI NEM OLVASTA. Ha a hálózat
 * elutasította a küldést, a felhasználó akkor is azt hallotta, hogy elment.
 *
 * A kézbesítést a küldés pillanatában nem lehet bemondani, mert még nem
 * tudjuk. Ezért kell ez a tároló: a választ akkor kérdezheti meg a
 * felhasználó, amikor már megérkezett.
 *
 * ADATVÉDELEM: az ÜZENET SZÖVEGE NEM kerül ide, csak a címzett neve, az idő
 * és a kimenetel. Egy „mi lett az utolsó üzenettel" kérdéshez a tartalom nem
 * kell, tárolni viszont kockázat.
 */
object SmsOutcomeStore {

    private const val PREFS = "superdl"
    private const val KEY_LABEL = "sms_last_label"
    private const val KEY_TIME = "sms_last_time"
    private const val KEY_STATE = "sms_last_state"
    private const val KEY_REASON = "sms_last_reason"

    /** Amit a program tud az utolsó üzenetről. */
    enum class State {
        /** Átadtuk a rendszernek, de a hálózat válasza még nem jött meg. */
        SENDING,

        /** A hálózat átvette. Ez még NEM kézbesítés. */
        HANDED_OVER,

        /** A címzett készüléke megkapta. */
        DELIVERED,

        /** Nem sikerült. A `reason` megmondja, miért. */
        FAILED
    }

    fun note(context: Context, label: String, state: State, reason: String? = null) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LABEL, label)
                .putLong(KEY_TIME, System.currentTimeMillis())
                .putString(KEY_STATE, state.name)
                .putString(KEY_REASON, reason)
                .apply()
        } catch (_: Exception) {
        }
    }

    /**
     * Csak az állapot frissítése, a címzett és az idő megtartásával.
     * A kézbesítési visszajelzés percekkel később érkezhet.
     */
    fun updateState(context: Context, state: State, reason: String? = null) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LABEL, null) == null) return
            prefs.edit()
                .putString(KEY_STATE, state.name)
                .putString(KEY_REASON, reason)
                .apply()
        } catch (_: Exception) {
        }
    }

    /** Felolvasható válasz arra, hogy „mi lett az utolsó üzenettel". */
    fun speakLast(context: Context): String = try {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val label = prefs.getString(KEY_LABEL, null)
        if (label == null) {
            "Még nem küldtél üzenetet ebből a programból."
        } else {
            val time = prefs.getLong(KEY_TIME, 0L)
            val state = try {
                State.valueOf(prefs.getString(KEY_STATE, State.SENDING.name).orEmpty())
            } catch (_: Exception) {
                State.SENDING
            }
            val reason = prefs.getString(KEY_REASON, null)
            val whenText = if (time > 0L) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time)) + "-kor"
            } else {
                ""
            }
            when (state) {
                State.DELIVERED ->
                    "Az utolsó üzenet $label részére $whenText megérkezett a címzetthez."
                State.HANDED_OVER ->
                    "Az utolsó üzenet $label részére $whenText elment. A címzett készüléke " +
                        "még nem jelezte vissza, hogy megkapta — ez nem baj, néha percekig tart, " +
                        "és nem minden szolgáltató küld ilyen visszajelzést."
                State.SENDING ->
                    "Az utolsó üzenet $label részére $whenText elindult, de a hálózat válasza " +
                        "nem érkezett meg. Ha fontos, érdemes megkérdezni a címzettet."
                State.FAILED ->
                    "Az utolsó üzenet $label részére $whenText NEM ment el" +
                        (if (!reason.isNullOrBlank()) ": $reason." else ".") +
                        " Próbáld újra."
            }
        }
    } catch (_: Exception) {
        "Az utolsó üzenet sorsát most nem tudom megmondani."
    }
}
