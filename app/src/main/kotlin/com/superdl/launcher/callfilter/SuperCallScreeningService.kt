package com.superdl.launcher.callfilter

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import com.superdl.launcher.call.IncomingCallCache
import com.superdl.launcher.contacts.ContactHelper
import com.superdl.launcher.patrol.PatrolAnnouncer

class SuperCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // MIÉRT: Android 10-től a kimenő hívások is ide futnak be. Egy tiltólistás
        // szám felhívását így a szűrő "kiszűrt hívásként" naplózta és bemondta,
        // és a bejövő-hívás tárba is a saját tárcsázott számunk került.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection == Call.Details.DIRECTION_OUTGOING
        ) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }
        val number = callDetails.handle?.schemeSpecificPart
        val presentation = callDetails.handlePresentation
        if (!number.isNullOrBlank()) {
            IncomingCallCache.store(number)
        }
        val block = CallFilterEngine.shouldBlock(applicationContext, number, presentation)

        // ELŐBB A VÁLASZ A RENDSZERNEK, AZTÁN A FELJEGYZÉS.
        //
        // A rendszer ezt a pillanatot rövidre szabja: ha itt elidőzünk, a
        // hívás kezelése csúszik. A naplózás fontos, de nem annyira, mint
        // maga a szűrés — ezért megy utána, és a saját védőhálójában.
        val response = if (block) {
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()
        } else {
            CallResponse.Builder().build()
        }
        respondToCall(callDetails, response)

        if (block) {
            val blockedAt = System.currentTimeMillis()
            noteBlockedCall(number, presentation)
            // FEKETELISTÁS HÍVÁS: a sora a rendszer hívásnaplójából is
            // eltűnik (ha a beállítás be van kapcsolva). A takarító maga nézi
            // meg a háttérben, hogy tényleg feketelistás-e — itt, a hívás
            // kezelésének pillanatában, nem töltünk vele időt.
            BlacklistCallLogPurger.scheduleAfterBlock(applicationContext, number, blockedAt)
        }
    }

    /**
     * A KISZŰRT HÍVÁS FELJEGYZÉSE, ÉS — HA KÉRTED — A BEMONDÁSA.
     *
     * Minden külön védőhálóban: egy elszállt feljegyzés ne vigye magával a
     * szolgáltatást, ami a hívásokat kezeli.
     */
    private fun noteBlockedCall(number: String?, presentation: Int) {
        val app = applicationContext
        val szam = number.orEmpty()
        val nev = try {
            if (szam.isBlank()) "" else ContactHelper.findNameByPhone(app, szam).orEmpty()
        } catch (_: Throwable) {
            ""
        }
        val ok = try {
            CallFilterEngine.blockReasonId(app, number, presentation)
        } catch (_: Throwable) {
            "egyeb"
        }

        FilteredCallStore.add(app, szam, nev, ok)

        // FEKETELISTÁS SZÁMRÓL SOHA NEM SZÓLUNK — Alph döntése (2026-09-30):
        // „akit odaraktunk, azt pontosan azért raktuk oda, hogy ne is tudjunk
        // róla." A feljegyzés (fent) megmarad, de a Szűrt hívások listája sem
        // olvassa fel.
        if (ok == CallLogVisibility.REASON_BLACKLIST) return

        // A BEMONDÁS CSAK AKKOR, HA KÉRTED. Alapból néma marad, és a
        // helyzetjelentés mondja meg, hány hívást szűrt a program aznap —
        // éjjel senkit nem ébresztünk fel egy kiszűrt reklámhívással.
        try {
            if (CallFilterStore.announceMode(app) == CallFilterStore.AnnounceMode.ALWAYS) {
                val kit = when {
                    nev.isNotBlank() -> nev
                    szam.isNotBlank() -> szam
                    else -> "rejtett szám"
                }
                PatrolAnnouncer.announce(app, "Kiszűrt hívás: $kit.", critical = false)
            }
        } catch (_: Throwable) {
        }
    }
}
