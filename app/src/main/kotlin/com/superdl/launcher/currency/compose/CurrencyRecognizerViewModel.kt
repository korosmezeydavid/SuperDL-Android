package com.superdl.launcher.currency.compose

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.superdl.launcher.R
import com.superdl.launcher.currency.BanknoteClassificationResult
import com.superdl.launcher.currency.BanknoteClassifierEngine
import com.superdl.launcher.currency.BanknoteConsensusFilter
import com.superdl.launcher.currency.BanknoteFrameGate
import com.superdl.launcher.currency.BanknoteScanDebouncer
import com.superdl.launcher.currency.BanknoteTorchController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

class CurrencyRecognizerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CurrencyRecognizerUiState())
    val uiState: StateFlow<CurrencyRecognizerUiState> = _uiState.asStateFlow()

    @Volatile
    private var engine: BanknoteClassifierEngine? = null

    private val debouncer = BanknoteScanDebouncer()
    private val consensusFilter = BanknoteConsensusFilter()
    val torchController = BanknoteTorchController()

    private val scanning = AtomicBoolean(false)
    private val frameMutex = Mutex()
    private val engineMutex = Mutex()
    private var releaseJob: Job? = null
    private val pendingEvents = ArrayDeque<FrameEvent>()

    @Volatile
    private var lastTorchSpeechAt = 0L

    sealed interface FrameEvent {
        data class Announce(val speech: String, val playEntryBeep: Boolean) : FrameEvent
        data class SpeakAdd(val speech: String) : FrameEvent
        object PlayWorkingTick : FrameEvent
        object PlayEntryBeep : FrameEvent
        object PlayError : FrameEvent
    }

    private val _events = MutableStateFlow<FrameEvent?>(null)
    val events: StateFlow<FrameEvent?> = _events.asStateFlow()

    fun initialize(onReady: () -> Unit) {
        viewModelScope.launch {
            try {
                val created = withContext(Dispatchers.IO) {
                    BanknoteClassifierEngine.create(getApplication())
                }
                engine = created
                scanning.set(true)
                _uiState.update {
                    it.copy(
                        statusText = getString(R.string.currency_status_active),
                        hintText = getString(R.string.currency_hint),
                        isScanning = true,
                        isTwoStageEnabled = created.isTwoStageEnabled,
                        fatalError = null
                    )
                }
                onReady()
            } catch (_: Exception) {
                engine = null
                scanning.set(false)
                _uiState.update {
                    it.copy(
                        fatalError = getString(R.string.currency_model_error),
                        isScanning = false
                    )
                }
                emitEvent(FrameEvent.PlayError)
            }
        }
    }

    fun onFrame(bitmap: Bitmap) {
        if (!scanning.get() || engine == null) return

        viewModelScope.launch(Dispatchers.Default) {
            frameMutex.withLock {
                if (!scanning.get() || engine == null) return@withLock
                val frame = copyFrame(bitmap) ?: return@withLock
                try {
                    processFrame(frame)
                } finally {
                    if (!frame.isRecycled) frame.recycle()
                }
            }
        }
    }

    private suspend fun processFrame(bitmap: Bitmap) {
        val activeEngine = engine ?: return

        val frameDecision = BanknoteFrameGate.evaluate(bitmap)
        val torchJustOn = torchController.update(frameDecision.metrics)
        if (torchJustOn) {
            maybeEmitTorchOn()
        }

        if (frameDecision.isEmptySlot) {
            when (debouncer.onAbsentFrame()) {
                BanknoteScanDebouncer.BillPresenceEvent.REMOVED ->
                    updateStatus(getString(R.string.currency_status_scanning))
                else -> Unit
            }
            consensusFilter.reset()
            clearOverlay()
            return
        }

        emitEvent(FrameEvent.PlayWorkingTick)

        val rawResult = engineMutex.withLock { activeEngine.classify(bitmap) }
        val stableResult = consensusFilter.submit(frameDecision, rawResult)
        updateOverlay(rawResult)

        if (stableResult == null) {
            when (debouncer.onAbsentFrame()) {
                BanknoteScanDebouncer.BillPresenceEvent.REMOVED ->
                    updateStatus(getString(R.string.currency_status_scanning))
                else -> Unit
            }
            return
        }

        when (val decision = debouncer.onDetected(stableResult)) {
            is BanknoteScanDebouncer.ScanDecision.Announce -> {
                if (decision.playEntryBeep) {
                    emitEvent(FrameEvent.PlayEntryBeep)
                    updateStatus(getString(R.string.currency_status_detected))
                }
                updateStatus(decision.result.denomination.speechHu)
                emitEvent(
                    FrameEvent.Announce(
                        speech = decision.result.denomination.speechHu,
                        playEntryBeep = false
                    )
                )
                debouncer.markAnnounced(decision.result.denomination)
            }
            BanknoteScanDebouncer.ScanDecision.BillRemoved ->
                updateStatus(getString(R.string.currency_status_scanning))
            BanknoteScanDebouncer.ScanDecision.Ignored -> Unit
        }
    }

    fun manualVerify(bitmap: Bitmap?) {
        val activeEngine = engine ?: return
        if (bitmap == null) {
            emitEvent(FrameEvent.Announce(getString(R.string.currency_no_frame), playEntryBeep = false))
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            frameMutex.withLock {
                val frame = copyFrame(bitmap) ?: run {
                    emitEvent(FrameEvent.Announce(getString(R.string.currency_no_frame), playEntryBeep = false))
                    return@launch
                }
                try {
                    val frameDecision = BanknoteFrameGate.evaluate(frame)
                    torchController.update(frameDecision.metrics)

                    if (frameDecision.isEmptySlot) {
                        emitEvent(FrameEvent.PlayEntryBeep)
                        emitEvent(FrameEvent.Announce(getString(R.string.currency_no_banknote), playEntryBeep = false))
                        updateStatus(getString(R.string.currency_status_scanning))
                        clearOverlay()
                        return@withLock
                    }

                    if (frameDecision.needsMoreLight) {
                        torchController.forceOn()
                        maybeEmitTorchOn()
                    }

                    val result = engineMutex.withLock { activeEngine.classifyForManualCheck(frame) }
                    updateOverlay(result)

                    if (result == null || !result.isReliableForManualCheck()) {
                        emitEvent(FrameEvent.PlayEntryBeep)
                        emitEvent(FrameEvent.Announce(getString(R.string.currency_not_recognized), playEntryBeep = false))
                        updateStatus(getString(R.string.currency_status_scanning))
                        return@withLock
                    }

                    updateStatus(result.denomination.speechHu)
                    emitEvent(FrameEvent.Announce(result.denomination.speechHu, playEntryBeep = false))
                    debouncer.markAnnounced(result.denomination)
                } catch (_: Exception) {
                    emitEvent(FrameEvent.PlayError)
                    emitEvent(FrameEvent.Announce(getString(R.string.currency_verify_error), playEntryBeep = false))
                } finally {
                    if (!frame.isRecycled) frame.recycle()
                }
            }
        }
    }

    fun stopScanning() {
        scanning.set(false)
        debouncer.reset()
        consensusFilter.reset()
        torchController.release()
        _uiState.update {
            it.copy(isScanning = false, detectionBox = null, pipelineMode = null)
        }
    }

    fun release() {
        stopScanning()
        releaseJob?.cancel()
        releaseJob = viewModelScope.launch(Dispatchers.IO) {
            frameMutex.withLock {
                engineMutex.withLock {
                    engine?.close()
                    engine = null
                }
            }
        }
    }

    fun consumeEvent() {
        synchronized(pendingEvents) {
            _events.value = if (pendingEvents.isEmpty()) null else pendingEvents.removeFirst()
        }
    }

    private fun updateOverlay(result: BanknoteClassificationResult?) {
        _uiState.update {
            it.copy(
                pipelineMode = result?.pipelineMode,
                detectionBox = result?.detectionBox?.let { box -> RectF(box) },
                showDetectionOverlay = result?.detectionBox != null && isDebugBuild()
            )
        }
    }

    private fun clearOverlay() {
        _uiState.update { it.copy(detectionBox = null, pipelineMode = null) }
    }

    private fun updateStatus(text: String) {
        _uiState.update { it.copy(statusText = text) }
    }

    private fun maybeEmitTorchOn() {
        val now = System.currentTimeMillis()
        if (now - lastTorchSpeechAt < 8000L) return
        lastTorchSpeechAt = now
        emitEvent(FrameEvent.SpeakAdd(getString(R.string.currency_torch_on)))
    }

    private fun emitEvent(event: FrameEvent) {
        synchronized(pendingEvents) {
            if (_events.value == null) {
                _events.value = event
            } else {
                pendingEvents.addLast(event)
            }
        }
    }

    private fun copyFrame(bitmap: Bitmap): Bitmap? =
        try {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)

    private fun isDebugBuild(): Boolean {
        val flags = getApplication<Application>().applicationInfo.flags
        return flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}