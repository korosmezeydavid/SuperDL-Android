package com.superdl.launcher.call

import android.telecom.Call
import android.telecom.InCallService
import com.superdl.launcher.callfilter.CallFilterEngine
import com.superdl.launcher.contacts.ContactHelper

class SuperInCallService : InCallService() {

    private val callbacks = mutableMapOf<Call, Call.Callback>()

    override fun onCallAdded(call: Call) {
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                ActiveCallRegistry.onStateChanged(call, state)
                handleCallState(call, state)
            }
        }
        call.registerCallback(callback)
        callbacks[call] = callback
        ActiveCallRegistry.onCallAdded(call)
        handleCallState(call, call.details.state)
    }

    override fun onCallRemoved(call: Call) {
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        ActiveCallRegistry.onCallRemoved(call)
        if (!ActiveCallRegistry.hasManagedCall) {
            IncomingCallState.dismissIfShowing(applicationContext)
        }
    }

    private fun handleCallState(call: Call, state: Int) {
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        val presentation = call.details.handlePresentation
        val name = resolveCallerName(number)

        when (state) {
            Call.STATE_RINGING -> {
                if (CallFilterEngine.shouldBlock(applicationContext, number, presentation)) {
                    call.reject(false, null)
                    return
                }
                if (!IncomingCallState.isShowing) {
                    IncomingCallState.show(applicationContext, number, name)
                }
            }
            Call.STATE_ACTIVE -> {
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
                IncomingCallState.dismissIfShowing(applicationContext)
            }
        }
    }

    private fun resolveCallerName(number: String): String {
        val fromContacts = ContactHelper.findNameByPhone(applicationContext, number).orEmpty()
        if (fromContacts.isNotBlank()) return fromContacts
        return if (number.isNotBlank()) "Ismeretlen szám" else "Ismeretlen"
    }
}