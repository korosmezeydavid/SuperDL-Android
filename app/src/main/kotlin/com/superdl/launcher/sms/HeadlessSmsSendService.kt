package com.superdl.launcher.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // MIÉRT: a rendszer (RESPOND_VIA_MESSAGE) a címzettet az intent adataként
        // (smsto:/sms:), a szöveget EXTRA_TEXT-ben adja — a régi extrák üresek voltak,
        // így a "válasz üzenettel" sosem ment el.
        val fromData = intent?.data
            ?.takeIf { it.scheme == "smsto" || it.scheme == "sms" }
            ?.schemeSpecificPart
            .orEmpty()
            .substringBefore('?')
            .split(',', ';')
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .trim()
        val phone = fromData.ifBlank {
            intent?.getStringExtra(Intent.EXTRA_PHONE_NUMBER).orEmpty().trim()
        }
        val message = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty().trim()
            .ifBlank { intent?.getStringExtra("android.intent.extra.MESSAGE").orEmpty().trim() }
        if (phone.isNotBlank() && message.isNotBlank()) {
            SmsHelper.send(this, phone, message)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}