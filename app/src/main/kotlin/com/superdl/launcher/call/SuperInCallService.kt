package com.superdl.launcher.call

import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import com.superdl.launcher.callfilter.CallFilterEngine
import com.superdl.launcher.contacts.ContactHelper
import com.superdl.launcher.system.QuietModeHelper

class SuperInCallService : InCallService() {

    private val callbacks = mutableMapOf<Call, Call.Callback>()

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
        ActiveCallRegistry.onCallRemoved(call)
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
                IncomingCallRinger.start(applicationContext, number, name)
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
                IncomingCallRinger.stop(applicationContext)
                IncomingCallState.dismissIfShowing(applicationContext)
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