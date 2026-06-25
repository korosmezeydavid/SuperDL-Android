package com.superdl.launcher.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        if (!SmsRoleHelper.isDefaultSmsApp(context)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.first().displayOriginatingAddress.orEmpty().trim()
        val body = messages.joinToString("") { it.displayMessageBody.orEmpty() }.trim()
        val timestamp = messages.first().timestampMillis
        if (sender.isBlank() || body.isBlank()) return

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        try {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        } catch (_: Exception) {
        }
    }
}