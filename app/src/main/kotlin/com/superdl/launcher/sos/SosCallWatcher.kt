package com.superdl.launcher.sos

import android.telecom.Call

/**
 * A KIMENŐ S.O.S. HÍVÁS SORSA.
 *
 * MIÉRT KELL EGYÁLTALÁN: a korábbi lánc vakon várt húsz másodpercet, aztán
 * tárcsázta a következő számot — AKKOR IS, HA AZ ELSŐ FELVETTE. Vagyis a
 * régi S.O.S. el tudta vágni pont azt a hívást, ami sikerült. Ez volt a
 * legsúlyosabb hiba az egész funkcióban.
 *
 * A megoldás: az alapértelmezett telefon alkalmazásként a SuperDL LÁTJA a
 * hívás állapotát (kicseng, felvették, bontva). A SuperInCallService ide
 * jelenti, a lánc innen kérdezi.
 *
 * MIÉRT LEKÉRDEZÉS, NEM VISSZAHÍVÁS: a lánc egy korutinban fut, és a
 * legfontosabb tulajdonsága, hogy MINDIG TOVÁBBLÉP. Egy elmaradt visszahívás
 * miatt beragadó lánc vészhelyzetben halálos csend lenne. Az egyszerű
 * állapot-lekérdezés nem tud beragadni.
 */
object SosCallWatcher {

    enum class Phase {
        /** Nincs S.O.S. hívás folyamatban. */
        IDLE,

        /** Tárcsázás vagy kicsengés — még nincs döntés. */
        RINGING,

        /** Létrejött a kapcsolat. (Lehet ember, lehet hangposta.) */
        ANSWERED,

        /** Vége: nem vették fel, elutasították, vagy befejeződött. */
        ENDED
    }

    @Volatile
    var phase: Phase = Phase.IDLE
        private set

    /** Igaz, ha a hívás valaha ANSWERED állapotba jutott. */
    @Volatile
    var wasAnswered: Boolean = false
        private set

    @Volatile
    private var armed = false

    /** Új S.O.S. hívás indul: minden nulláról. */
    fun arm() {
        armed = true
        wasAnswered = false
        phase = Phase.RINGING
    }

    fun disarm() {
        armed = false
        phase = Phase.IDLE
        wasAnswered = false
    }

    /**
     * A SuperInCallService jelentése. Csak akkor számít, ha épp S.O.S. lánc
     * fut — egy hétköznapi hívás nem zavarhatja meg a következő riasztást.
     */
    fun onCallState(state: Int) {
        if (!armed) return
        when (state) {
            Call.STATE_ACTIVE -> {
                wasAnswered = true
                phase = Phase.ANSWERED
            }
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                phase = Phase.ENDED
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_RINGING -> {
                if (phase == Phase.IDLE) phase = Phase.RINGING
            }
        }
    }
}
