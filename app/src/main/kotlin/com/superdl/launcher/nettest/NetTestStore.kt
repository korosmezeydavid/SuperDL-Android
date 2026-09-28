package com.superdl.launcher.nettest

import android.content.Context
import com.superdl.launcher.feedback.DeviceStateTonePlayer
import com.superdl.launcher.feedback.GestureSoundHelper
import com.superdl.launcher.storage.SafePrefs
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A KORÁBBI MÉRÉSEK TÁROLÁSA — saját prefs-fájlban („superdl_nettest").
 *
 * Saját fájl, hogy a mentés (BackupManager.INCLUDED_PREFS) név szerint át
 * tudja vinni új telefonra: a két hét mérése pont akkor kell, amikor a
 * szolgáltatóval vitatkozol — ne vesszen el egy telefoncserénél.
 */
object NetTestStore {

    const val PREFS = "superdl_nettest"
    private const val KEY_HISTORY = "history"

    fun load(context: Context): List<NetTestHistory.Entry> = try {
        NetTestHistory.fromJson(SafePrefs.get(context, PREFS).getString(KEY_HISTORY, null))
    } catch (_: Throwable) {
        emptyList()
    }

    /** Egy befejezett (NEM megszakított) mérés a napló végére. */
    @Synchronized
    fun add(context: Context, e: NetTestResult) {
        try {
            val list = NetTestHistory.append(load(context), NetTestHistory.entryOf(e))
            SafePrefs.get(context, PREFS).edit().putString(KEY_HISTORY, NetTestHistory.toJson(list)).apply()
        } catch (_: Throwable) {
        }
    }
}

/**
 * RÖVID SÍPOK a méréshez és a Wi-Fi bejáráshoz.
 *
 * Egyetlen háttérszálon szólnak, és ha az előző még szól, az új KIMARAD —
 * nem torlódnak fel: járkálás közben a MOSTANI jel hangja számít, nem egy
 * két másodperccel korábbié.
 */
object NetTestBeeper {

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "SDL-nettest-beep") }
    private val busy = AtomicBoolean(false)

    /** A hang a gesztushangok csatornáján szól; ha le van véve, felhozzuk hallhatóra. */
    fun prepare(context: Context) {
        try {
            GestureSoundHelper.ensureGestureStreamAudible(context)
        } catch (_: Throwable) {
        }
    }

    fun beep(frequencyHz: Double, durationMs: Int = 90) {
        if (!busy.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    DeviceStateTonePlayer.playBurstSync(frequencyHz.toInt().coerceIn(150, 4000), durationMs)
                } catch (_: Throwable) {
                } finally {
                    busy.set(false)
                }
            }
        } catch (_: Throwable) {
            busy.set(false)
        }
    }
}
