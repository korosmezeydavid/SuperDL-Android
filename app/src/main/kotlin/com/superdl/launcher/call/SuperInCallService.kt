package com.superdl.launcher.call

import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.superdl.launcher.callfilter.CallFilterEngine
import com.superdl.launcher.contacts.ContactHelper
import com.superdl.launcher.system.QuietModeHelper

class SuperInCallService : InCallService() {

    private val callbacks = mutableMapOf<Call, Call.Callback>()

    // A DISCONNECTING és a DISCONNECTED is ide fut: ne indítsuk kétszer újra a csengést.
    private var promotedWaitingCall: Call? = null

    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        // Megjegyezzük az AKTUÁLIS állapotot, hogy a hívás-képernyő pontosan
        // tudja, mi van bekapcsolva — ne csak feltételezze.
        audioState?.let {
            CallAudioController.onAudioStateChanged(
                speakerOn = it.route == android.telecom.CallAudioState.ROUTE_SPEAKER,
                muted = it.isMuted
            )
        }
    }

    override fun onCallAdded(call: Call) {
        // A hangvezérlés innen tud igazán dolgozni: alapértelmezett telefon
        // alkalmazásként a rendszer ENGEDI a hangút és a némítás állítását.
        CallAudioController.attach(this)
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                ActiveCallRegistry.onStateChanged(call, state)
                handleCallState(call, state)
            }
        }
        call.registerCallback(callback)
        callbacks[call] = callback
        ActiveCallRegistry.onCallAdded(call)
        handleCallState(call, callState(call))
    }

    override fun onCallRemoved(call: Call) {
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        if (promotedWaitingCall == call) promotedWaitingCall = null
        ActiveCallRegistry.onCallRemoved(call)
        // MIÉRT: ha a letett hívás mellett egy TARTOTT hívás maradt, eddig
        // semmi nem vette vissza — a hang és a képernyő leállt, a másik fél
        // pedig örökre várakozott. Visszavesszük, és aktívként nyilvántartjuk.
        if (!ActiveCallRegistry.hasManagedCall) {
            val held = calls.firstOrNull { it != call && callState(it) == Call.STATE_HOLDING }
            if (held != null) {
                try {
                    held.unhold()
                } catch (_: Exception) {
                }
                ActiveCallRegistry.onStateChanged(held, Call.STATE_ACTIVE)
            }
        }
        if (!ActiveCallRegistry.hasManagedCall) {
            IncomingCallRinger.stop(applicationContext)
            IncomingCallState.dismissIfShowing(applicationContext)
            // Nincs több hívás: a hangvezérlés leáll, és minden visszaáll.
            CallAudioController.detach()
        }
    }

    private fun handleCallState(call: Call, state: Int) {
        // AZ S.O.S. LÁNCNAK TUDNIA KELL, FELVETTÉK-E. Enélkül a lánc vakon
        // várt húsz másodpercet, és rátárcsázott arra a hívásra is, ami
        // sikerült. A jelentés akkor is elmegy, ha nincs S.O.S. — a
        // SosCallWatcher maga dobja el, ha nem tartozik rá.
        com.superdl.launcher.sos.SosCallWatcher.onCallState(state)

        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        val presentation = call.details.handlePresentation
        val name = resolveCallerName(number)

        when (state) {
            Call.STATE_RINGING -> {
                if (QuietModeHelper.shouldSuppressIncomingCalls(applicationContext)) {
                    IncomingCallRinger.stop(applicationContext)
                    return
                }
                if (CallFilterEngine.shouldBlock(applicationContext, number, presentation)) {
                    IncomingCallRinger.stop(applicationContext)
                    call.reject(false, null)
                    return
                }
                // MIÉRT: beszélgetés közben érkező (várakoztatott) hívásra a teljes
                // csengőhang a fülbe szólt és átállította a hangmódot — ilyenkor
                // csak rövid kopogó hang jelez.
                val other = ActiveCallRegistry.activeCall
                if (other != null && other != call) {
                    IncomingCallRinger.startCallWaiting(applicationContext, number, name)
                } else {
                    IncomingCallRinger.start(applicationContext, number, name)
                }
                if (!IncomingCallState.isShowing) {
                    IncomingCallState.show(applicationContext, number, name)
                }
            }
            Call.STATE_ACTIVE -> {
                IncomingCallRinger.stop(applicationContext)
                IncomingCallState.isShowing = false
                if (!CallSession.isInCallUiActive) {
                    CallHelper.launchInCall(
                        applicationContext,
                        number,
                        name,
                        InCallActivity.MODE_INCOMING
                    )
                }
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                IncomingCallRinger.stop(applicationContext)
                if (!CallSession.isInCallUiActive) {
                    CallHelper.launchInCall(
                        applicationContext,
                        number,
                        name,
                        InCallActivity.MODE_OUTGOING
                    )
                }
            }
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                // MIÉRT: ha a letett hívás mellett egy MÁSIK hívás még csörög (várakoztatott
                // hívás), eddig azt is elnémítottuk és a képernyőjét is bezártuk — némán
                // csörgött tovább. Csak akkor állítunk le, ha nincs másik csörgő hívás.
                val waiting = ActiveCallRegistry.ringingCall
                if (waiting == null || waiting == call) {
                    IncomingCallRinger.stop(applicationContext)
                    IncomingCallState.dismissIfShowing(applicationContext)
                } else if (state == Call.STATE_DISCONNECTED && promotedWaitingCall != waiting) {
                    // Már nincs élő beszélgetés: a kopogó hang helyett rendes csengés és
                    // bejövőhívás-képernyő, mint egy friss hívásnál.
                    // MIÉRT KÉSLELTETVE ÉS ÚJRA ELLENŐRIZVE: a letevés gyakran a várakozó
                    // hívást is elutasítja, de az csak kicsivel később lesz DISCONNECTED —
                    // azonnal egy fölösleges csengés és egy szellem-képernyő jönne.
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (ActiveCallRegistry.ringingCall === waiting &&
                            ActiveCallRegistry.activeCall == null &&
                            callState(waiting) == Call.STATE_RINGING &&
                            promotedWaitingCall != waiting
                        ) {
                            promotedWaitingCall = waiting
                            val waitingNumber = waiting.details.handle?.schemeSpecificPart.orEmpty()
                            val waitingName = resolveCallerName(waitingNumber)
                            IncomingCallRinger.stop(applicationContext)
                            IncomingCallRinger.start(applicationContext, waitingNumber, waitingName)
                            if (!IncomingCallState.isShowing) {
                                IncomingCallState.show(applicationContext, waitingNumber, waitingName)
                            }
                        }
                    }, 600L)
                }
            }
            Call.STATE_SELECT_PHONE_ACCOUNT -> selectPhoneAccount(call)
        }
    }

    // MIÉRT: "mindig kérdezzen" SIM-beállításnál a kimenő hívás ebben az
    // állapotban várt a választásra, amit senki nem adott meg — elakadt.
    // Az alapértelmezett (vagy az első elérhető) SIM-mel indítjuk.
    private fun selectPhoneAccount(call: Call) {
        val telecom = getSystemService(TelecomManager::class.java)
        @Suppress("DEPRECATION")
        val available: List<PhoneAccountHandle> = (
            call.details.intentExtras
                ?.getParcelableArrayList<PhoneAccountHandle>(Call.AVAILABLE_PHONE_ACCOUNTS)
                ?: call.details.extras
                    ?.getParcelableArrayList<PhoneAccountHandle>(Call.AVAILABLE_PHONE_ACCOUNTS)
            ).orEmpty().ifEmpty {
                try {
                    telecom?.callCapablePhoneAccounts.orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
            }
        val preferred = try {
            telecom?.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
        } catch (_: Exception) {
            null
        }
        val chosen = preferred?.takeIf { available.isEmpty() || it in available }
            ?: available.firstOrNull()
        if (chosen == null) {
            try {
                call.disconnect()
            } catch (_: Exception) {
            }
            com.superdl.launcher.patrol.PatrolAnnouncer.announce(
                applicationContext,
                "A hívás nem indítható: nincs használható SIM-kártya.",
                withBeep = false
            )
            return
        }
        try {
            call.phoneAccountSelected(chosen, false)
        } catch (_: Exception) {
            try {
                call.disconnect()
            } catch (_: Exception) {
            }
            return
        }
        if (available.size > 1) {
            val label = try {
                telecom?.getPhoneAccount(chosen)?.label?.toString()
            } catch (_: Exception) {
                null
            }
            if (!label.isNullOrBlank()) {
                com.superdl.launcher.patrol.PatrolAnnouncer.announce(
                    applicationContext,
                    "Hívás ezzel: $label.",
                    withBeep = false
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun callState(call: Call): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            call.state
        }

    private fun resolveCallerName(number: String): String {
        val fromContacts = ContactHelper.findNameByPhone(applicationContext, number).orEmpty()
        if (fromContacts.isNotBlank()) return fromContacts
        return if (number.isNotBlank()) "Ismeretlen szám" else "Ismeretlen"
    }
}