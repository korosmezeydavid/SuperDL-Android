package com.superdl.launcher.telegram

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.superdl.launcher.BuildConfig
import java.util.concurrent.CopyOnWriteArraySet

/** Egyetlen TDLib-kapcsolat az Activity és a háttérszolgáltatás számára. */
internal object TelegramSession {
    private val main = Handler(Looper.getMainLooper())
    private val observers = CopyOnWriteArraySet<(String) -> Unit>()
    private var transport: TdlibNativeTransport? = null

    @Synchronized fun start(context: Context): TdlibNativeTransport.StartResult {
        if (transport != null) return TdlibNativeTransport.StartResult.AlreadyRunning
        if (!BuildConfig.FEATURE_TELEGRAM) return TdlibNativeTransport.StartResult.ClientUnavailable
        val client = TdlibNativeTransport()
        val result = client.start(
            onJson = { json ->
                try {
                    if (TdlibJsonProtocol.loginStep(json) == TelegramLoginStep.Parameters) {
                        client.send(TdlibJsonProtocol.parameters(
                            BuildConfig.TELEGRAM_API_ID, BuildConfig.TELEGRAM_API_HASH,
                            context.getDir("telegram_db", Context.MODE_PRIVATE).absolutePath,
                            context.getDir("telegram_files", Context.MODE_PRIVATE).absolutePath,
                            "hu", Build.VERSION.RELEASE, BuildConfig.VERSION_NAME))
                    }
                } catch (_: Exception) { }
                main.post { observers.forEach { observer -> observer(json) } }
            },
            onStopped = {
                synchronized(this) { if (transport === client) transport = null }
                main.post { observers.forEach { observer -> observer(STOPPED_EVENT) } }
            }
        )
        if (result == TdlibNativeTransport.StartResult.Started) transport = client
        return result
    }

    fun observe(observer: (String) -> Unit) { observers.add(observer) }
    fun remove(observer: (String) -> Unit) { observers.remove(observer) }
    fun send(json: String): Boolean = transport?.send(json) ?: false
    fun requestAuthState(): Boolean = send("{\"@type\":\"getAuthorizationState\"}")

    @Synchronized fun stop() {
        transport?.close()
        transport = null
    }

    const val STOPPED_EVENT = "{\"@type\":\"superdlSessionStopped\"}"
}
