package com.superdl.launcher.telegram

import org.drinkless.tdlib.JsonClient
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns TDLib's process-wide JSON receive loop. Callbacks run on the receiver thread; UI callers
 * must post them to the main thread. Never log callback contents (messages and login codes).
 */
class TdlibNativeTransport {
    enum class StartResult { Started, AlreadyRunning, NativeLibraryMissing, ClientUnavailable }

    private val running = AtomicBoolean(false)
    private var clientId = 0
    private var receiver: Thread? = null

    fun start(onJson: (String) -> Unit, onStopped: () -> Unit): StartResult {
        if (!claimed.compareAndSet(false, true)) return StartResult.AlreadyRunning
        try {
            System.loadLibrary("tdjsonjava")
            clientId = JsonClient.createClientId()
            if (clientId == 0) {
                claimed.set(false)
                return StartResult.ClientUnavailable
            }
            running.set(true)
            receiver = Thread({
                try {
                    while (running.get()) {
                        val response = JsonClient.receive(1.0) ?: continue
                        onJson(response)
                    }
                } catch (_: Exception) {
                    // TDLib may contain account data in exception text. Never log it.
                } finally {
                    running.set(false)
                    claimed.set(false)
                    onStopped()
                }
            }, "SuperDL-TDLib-receiver").apply { isDaemon = true; start() }
            JsonClient.send(clientId, JSONObject().put("@type", "getAuthorizationState").toString())
            return StartResult.Started
        } catch (_: UnsatisfiedLinkError) {
            if (running.get()) close() else claimed.set(false)
            return StartResult.NativeLibraryMissing
        } catch (_: Exception) {
            if (running.get()) close() else claimed.set(false)
            return StartResult.ClientUnavailable
        }
    }

    fun send(request: String): Boolean {
        if (!running.get()) return false
        return try {
            JsonClient.send(clientId, request)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Closes the local TDLib client without logging out or erasing its on-device session. */
    fun close() {
        if (!running.getAndSet(false)) return
        try {
            JsonClient.send(clientId, JSONObject().put("@type", "close").toString())
        } catch (_: Exception) { }
    }

    companion object {
        private val claimed = AtomicBoolean(false)
    }
}
