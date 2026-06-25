package com.superdl.launcher.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceInput(context: Context) {

    private val recognizerContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: (() -> Unit)? = null
    private var pendingIntent: Intent? = null
    private var retryCount = 0
    private var toneGenerator: ToneGenerator? = null
    private var listenGeneration = 0

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(recognizerContext)

    fun listen(
        prompt: String,
        speakFirst: (String, () -> Unit) -> Unit,
        onResult: (String) -> Unit,
        onError: () -> Unit
    ) {
        speakFirst(prompt) {
            listenPrompt(prompt, onResult, onError)
        }
    }

    fun listenPrompt(
        prompt: String,
        onResult: (String) -> Unit,
        onError: () -> Unit
    ) {
        if (!isAvailable()) {
            onError()
            return
        }

        val generation = ++listenGeneration
        onResultCallback = onResult
        onErrorCallback = onError
        retryCount = 0
        prepareRecognizer(generation)
        pendingIntent = buildIntent(prompt)
        handler.postDelayed({ startListeningInternal(generation) }, 400L)
    }

    private fun buildIntent(prompt: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hu-HU")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, recognizerContext.packageName)
            }
        }

    private fun startListeningInternal(generation: Int) {
        if (generation != listenGeneration) return
        val intent = pendingIntent ?: return
        playListeningEarcon()
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            if (generation == listenGeneration) {
                handler.post { onErrorCallback?.invoke() }
            }
        }
    }

    private fun playListeningEarcon() {
        try {
            toneGenerator?.release()
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 130)
            handler.postDelayed({
                toneGenerator?.release()
                toneGenerator = null
            }, 180L)
        } catch (_: Exception) {
        }
    }

    private fun prepareRecognizer(generation: Int) {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(recognizerContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onError(error: Int) {
                    if (generation != listenGeneration) return
                    if (shouldRetry(error) && retryCount < 1) {
                        retryCount++
                        handler.postDelayed({ startListeningInternal(generation) }, 500L)
                        return
                    }
                    val errorCb = onErrorCallback
                    handler.post {
                        if (generation == listenGeneration) errorCb?.invoke()
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (generation != listenGeneration) return
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                        ?.trim()
                        .orEmpty()
                    val resultCb = onResultCallback
                    val errorCb = onErrorCallback
                    handler.post {
                        if (generation != listenGeneration) return@post
                        if (spoken.isBlank()) errorCb?.invoke()
                        else resultCb?.invoke(spoken)
                    }
                }
            })
        }
    }

    private fun shouldRetry(error: Int): Boolean =
        error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

    fun cancel() {
        listenGeneration++
        handler.removeCallbacksAndMessages(null)
        onResultCallback = null
        onErrorCallback = null
        pendingIntent = null
        retryCount = 0
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
        } catch (_: Exception) {}
    }

    fun destroy() {
        cancel()
        toneGenerator?.release()
        toneGenerator = null
        recognizer?.destroy()
        recognizer = null
    }
}