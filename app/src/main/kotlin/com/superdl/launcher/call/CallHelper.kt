package com.superdl.launcher.call

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.telephony.TelephonyManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat

object CallHelper {

    fun launchInCall(
        context: Context,
        phone: String,
        displayName: String,
        mode: String = InCallActivity.MODE_OUTGOING
    ) {
        CallSession.markInCallUiStarted(incomingHandoff = mode == InCallActivity.MODE_INCOMING)
        val intent = inCallIntent(context, phone, displayName, mode).apply {
            // Dedicated call task — isolated from the HOME/singleTask MainActivity stack.
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        context.startActivity(intent)
    }

    fun bringInCallToFront(context: Context) {
        if (!CallSession.isInCallUiActive) return
        val intent = Intent(context, InCallActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        context.startActivity(intent)
    }

    private fun inCallIntent(
        context: Context,
        phone: String,
        displayName: String,
        mode: String
    ): Intent {
        return Intent(context, InCallActivity::class.java).apply {
            putExtra(InCallActivity.EXTRA_PHONE, phone)
            putExtra(InCallActivity.EXTRA_NAME, displayName)
            putExtra(InCallActivity.EXTRA_MODE, mode)
        }
    }

    fun placeCall(context: Context, phone: String): Boolean {
        if (DialerRoleHelper.isDefaultDialer(context)) {
            return placeCallViaTelecom(context, phone)
        }
        return placeCallViaIntent(context, phone)
    }

    private fun placeCallViaIntent(context: Context, phone: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")).apply {
                if (context !is Activity) {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun placeCallViaTelecom(context: Context, phone: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return try {
            val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
            val uri = Uri.fromParts("tel", phone, null)
            telecom.placeCall(uri, Bundle())
            true
        } catch (_: Exception) {
            placeCallViaIntent(context, phone)
        }
    }

    fun acceptIncomingCall(context: Context): Boolean {
        ActiveCallRegistry.ringingCall?.let { call ->
            return try {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                true
            } catch (_: Exception) {
                false
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return try {
            val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
            telecom.acceptRingingCall()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun rejectIncomingCall(context: Context): Boolean = endCallAggressive(context)

    fun tryEndCall(context: Context): Boolean = endCallAggressive(context)

    fun endCallAggressive(context: Context): Boolean {
        if (endCallViaTelecom(context)) return true
        if (endCallViaReflection(context)) return true
        if (context is Activity) {
            dispatchEndCallKey(context)
            if (endCallViaTelecom(context)) return true
            if (endCallViaReflection(context)) return true
        }
        return false
    }

    private fun endCallViaTelecom(context: Context): Boolean {
        val managed = ActiveCallRegistry.activeCall ?: ActiveCallRegistry.ringingCall
        managed?.let { call ->
            return try {
                when (call.state) {
                    Call.STATE_RINGING -> call.reject(false, null)
                    else -> call.disconnect()
                }
                true
            } catch (_: Exception) {
                false
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return try {
            val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
            @Suppress("DEPRECATION")
            telecom.endCall()
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun endCallViaReflection(context: Context): Boolean {
        return try {
            val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val telephonyClass = Class.forName(telephony.javaClass.name)
            val getITelephony = telephonyClass.getDeclaredMethod("getITelephony")
            getITelephony.isAccessible = true
            val iTelephony = getITelephony.invoke(telephony) ?: return false
            val endCall = iTelephony.javaClass.getDeclaredMethod("endCall")
            endCall.isAccessible = true
            endCall.invoke(iTelephony) as Boolean
        } catch (_: Exception) {
            try {
                val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val endCall = telephony.javaClass.getDeclaredMethod("endCall")
                endCall.isAccessible = true
                endCall.invoke(telephony) as Boolean
            } catch (_: Exception) {
                false
            }
        }
    }

    fun dispatchEndCallKey(activity: Activity): Boolean {
        return try {
            val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL)
            val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENDCALL)
            activity.dispatchKeyEvent(down)
            activity.dispatchKeyEvent(up)
        } catch (_: Exception) {
            false
        }
    }

    fun sendDtmfTone(digit: Char): Boolean {
        val tone = dtmfToneFor(digit) ?: return false
        return try {
            val generator = ToneGenerator(AudioManager.STREAM_DTMF, ToneGenerator.MAX_VOLUME)
            val ok = generator.startTone(tone, 200)
            handlerPostRelease(generator)
            ok
        } catch (_: Exception) {
            false
        }
    }

    fun setSpeakerphone(context: Context, enabled: Boolean): Boolean {
        return try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = enabled
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isSpeakerphoneOn(context: Context): Boolean {
        return try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn
        } catch (_: Exception) {
            false
        }
    }

    fun setMicrophoneMute(context: Context, muted: Boolean): Boolean {
        return try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.isMicrophoneMute = muted
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isMicrophoneMuted(context: Context): Boolean {
        return try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.isMicrophoneMute
        } catch (_: Exception) {
            false
        }
    }

    fun speakPhoneNumber(phone: String): String {
        val words = mapOf(
            '0' to "nulla", '1' to "egy", '2' to "kettő", '3' to "három", '4' to "négy",
            '5' to "öt", '6' to "hat", '7' to "hét", '8' to "nyolc", '9' to "kilenc"
        )
        return phone.map { char ->
            when {
                char == '+' -> "plusz"
                words.containsKey(char) -> words.getValue(char)
                else -> ""
            }
        }.filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun dtmfToneFor(digit: Char): Int? = when (digit) {
        '0' -> ToneGenerator.TONE_DTMF_0
        '1' -> ToneGenerator.TONE_DTMF_1
        '2' -> ToneGenerator.TONE_DTMF_2
        '3' -> ToneGenerator.TONE_DTMF_3
        '4' -> ToneGenerator.TONE_DTMF_4
        '5' -> ToneGenerator.TONE_DTMF_5
        '6' -> ToneGenerator.TONE_DTMF_6
        '7' -> ToneGenerator.TONE_DTMF_7
        '8' -> ToneGenerator.TONE_DTMF_8
        '9' -> ToneGenerator.TONE_DTMF_9
        '*' -> ToneGenerator.TONE_DTMF_S
        '#' -> ToneGenerator.TONE_DTMF_P
        else -> null
    }

    private fun handlerPostRelease(generator: ToneGenerator) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                generator.release()
            } catch (_: Exception) {
            }
        }, 250L)
    }
}