package com.superdl.launcher.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MmsWapPushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // MMS fogadás – alap implementáció a default SMS app szerephez.
        // A Super DL fő funkciója az SMS; az MMS csak a rendszerkövetelmény miatt van jelen.
        // MIÉRT: alapértelmezett SMS-appként a bejövő MMS eddig nyomtalanul
        // elveszett. Amíg nincs MMS-letöltés, legalább szólunk róla.
        if (intent.action != android.provider.Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        com.superdl.launcher.patrol.PatrolAnnouncer.announce(
            context.applicationContext,
            "Multimédiás üzenet érkezett. A Super DL még nem tudja megnyitni, " +
                "nyisd meg másik üzenetkezelővel.",
            critical = true
        )
    }
}