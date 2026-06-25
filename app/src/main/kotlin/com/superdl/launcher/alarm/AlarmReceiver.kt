package com.superdl.launcher.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.superdl.launcher.feedback.AlertSoundCategory
import com.superdl.launcher.feedback.AlertSoundPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.superdl.launcher.tts.TtsEngineStore
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        private const val TTS_UTTERANCE_ID = "alarm_tts"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val label = intent.getStringExtra(EXTRA_LABEL)?.takeIf { it.isNotBlank() } ?: "Ébresztő"
        val hour = intent.getIntExtra(EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(EXTRA_MINUTE, -1)
        val spokenLabel = if (hour >= 0 && minute >= 0) {
            "$label. ${hour.toString().padStart(2, '0')} óra ${minute.toString().padStart(2, '0')} perc."
        } else {
            "$label."
        }

        vibrateAlarm(appContext)

        AlertSoundPlayer.playOnce(appContext, AlertSoundCategory.ALARM_CLOCK)

        val finish: () -> Unit = {
            rescheduleAlarm(intent, appContext)
            pendingResult.finish()
        }

        val enginePackage = TtsEngineStore.getSelectedPackage(appContext)
        var tts: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            if (status != TextToSpeech.SUCCESS) {
                tts?.shutdown()
                finish()
                return@OnInitListener
            }
            val engine = tts ?: run {
                finish()
                return@OnInitListener
            }
            engine.setLanguage(Locale("hu", "HU"))
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == TTS_UTTERANCE_ID) {
                        engine.shutdown()
                        finish()
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == TTS_UTTERANCE_ID) {
                        engine.shutdown()
                        finish()
                    }
                }
            })
            engine.speak("$spokenLabel Ébresztő!", TextToSpeech.QUEUE_FLUSH, null, TTS_UTTERANCE_ID)
        }
        tts = if (enginePackage.isNullOrBlank()) {
            TextToSpeech(appContext, listener)
        } else {
            TextToSpeech(appContext, listener, enginePackage)
        }
    }

    private fun rescheduleAlarm(intent: Intent, context: Context) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (alarmId >= 0) {
            AlarmStore.getAll(context).firstOrNull { it.id == alarmId }?.let { entry ->
                AlarmScheduler.schedule(context, entry)
            }
        }
    }

    private fun vibrateAlarm(context: Context) {
        val pattern = longArrayOf(0, 500, 300, 500, 300)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (_: Exception) {
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                AlarmScheduler.rescheduleAll(context)
                com.superdl.launcher.medication.MedicationScheduler.rescheduleAll(context)
                com.superdl.launcher.calendar.CalendarReminderScheduler.rescheduleUpcoming(context)
                com.superdl.launcher.timer.TimerManager.resumeIfNeeded(context)
                com.superdl.launcher.battery.BatteryPatrolManager.start(context)
                com.superdl.launcher.feedback.DeviceStateSoundManager.start(context)
            }
        }
    }
}