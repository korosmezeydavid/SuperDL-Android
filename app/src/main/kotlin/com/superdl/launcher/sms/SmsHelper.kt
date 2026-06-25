package com.superdl.launcher.sms

import android.content.ContentUris
import android.content.Context
import android.provider.Telephony
import android.telephony.SmsManager
import com.superdl.launcher.contacts.ContactHelper
import com.superdl.launcher.sos.SosPreferences

data class SmsMessage(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long
)

object SmsHelper {

    fun send(context: Context, phone: String, message: String): Boolean {
        return try {
            val manager = context.getSystemService(SmsManager::class.java)
                ?: @Suppress("DEPRECATION") SmsManager.getDefault()
            manager.sendTextMessage(phone, null, message, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getRecentMessages(context: Context, limit: Int = 20): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
            val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
            while (cursor.moveToNext() && messages.size < limit) {
                val id = cursor.getLong(idIdx)
                val address = cursor.getString(addressIdx)?.trim().orEmpty()
                val body = cursor.getString(bodyIdx)?.trim().orEmpty()
                val date = cursor.getLong(dateIdx)
                if (address.isNotBlank() && body.isNotBlank()) {
                    messages.add(SmsMessage(id, address, body, date))
                }
            }
        }
        return messages
    }

    fun deleteMessage(context: Context, id: Long): Boolean {
        return try {
            val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun resolveSenderLabel(context: Context, address: String): String =
        ContactHelper.findNameByPhone(context, address) ?: address

    fun resolveRecipient(spoken: String, contacts: List<com.superdl.launcher.contacts.ContactMatch>): Recipient? {
        val normalizedNumber = SosPreferences.normalizeSpokenNumber(spoken)
        if (normalizedNumber != null && normalizedNumber.isNotBlank()) {
            return Recipient(normalizedNumber, normalizedNumber)
        }
        if (contacts.size == 1) {
            val contact = contacts.first()
            return Recipient(contact.phone, contact.name)
        }
        return null
    }
}

data class Recipient(
    val phone: String,
    val label: String
)