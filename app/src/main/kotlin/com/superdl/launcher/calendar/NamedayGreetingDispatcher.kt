package com.superdl.launcher.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.superdl.launcher.sms.SmsHelper

/** Csak a külön engedélyezett éves névnap pontos, nem halasztott riasztásához. */
object NamedayGreetingDispatcher {
    enum class Result { NOT_ELIGIBLE, ACCEPTED_BY_PHONE, SEND_FAILED }

    internal fun dispatchVerified(
        event: CalendarEvent?, nowMs: Long,
        reserve: (CalendarEvent, Long) -> NamedayGreetingPolicy.Greeting?,
        send: (String, String) -> Boolean
    ): Result {
        if (event == null) return Result.NOT_ELIGIBLE
        val greeting = try { reserve(event, nowMs) } catch (_: Exception) { null }
            ?: return Result.NOT_ELIGIBLE
        // A foglalás már tartós: hamis vagy kétes eredménynél nincs automatikus ismétlés.
        return try {
            if (send(greeting.phone, greeting.text)) Result.ACCEPTED_BY_PHONE
            else Result.SEND_FAILED
        } catch (_: Exception) {
            Result.SEND_FAILED
        }
    }

    internal fun originalAlarm(triggerMs: Long, beginMs: Long, nowMs: Long): Boolean =
        beginMs > 0L && triggerMs == beginMs && nowMs >= beginMs &&
            NamedayGreetingPolicy.dayKey(beginMs) == NamedayGreetingPolicy.dayKey(nowMs)
    fun dispatchForAlarm(
        context: Context, eventId: Long, beginMs: Long, title: String,
        triggerMs: Long, nowMs: Long = System.currentTimeMillis()
    ): Result {
        // A halasztott emlékeztető nem indíthat második SMS-t.
        if (!originalAlarm(triggerMs, beginMs, nowMs)) return Result.NOT_ELIGIBLE
        if (!CalendarHelper.hasReadPermission(context) ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED) return Result.NOT_ELIGIBLE
        val verified = try {
            val definition = CalendarHelper.getEventById(context, eventId)
            val instance = CalendarHelper.getInstancesBetween(context, beginMs, beginMs + 1L)
                .singleOrNull { it.eventId == eventId && it.begin == beginMs }
            NamedayGreetingOccurrence.verified(definition, instance, eventId, beginMs, title)
        } catch (_: Exception) {
            null
        }
        val ownNameday = try { NamedayStore.get(context, eventId) } catch (_: Exception) { null }
        if (!NamedayOwnership.matches(ownNameday, verified)) return Result.NOT_ELIGIBLE
        return dispatchVerified(verified, nowMs,
            reserve = { event, day -> NamedayGreetingStore.reserve(context, event, day) },
            send = { phone, text -> SmsHelper.send(context, phone, text) })
    }
}