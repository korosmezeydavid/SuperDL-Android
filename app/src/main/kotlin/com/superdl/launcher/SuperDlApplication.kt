package com.superdl.launcher

import android.app.Application
import android.os.Build
import com.superdl.launcher.crash.CrashLogHandler
import com.superdl.launcher.system.QuietModeHelper
import org.lsposed.hiddenapibypass.HiddenApiBypass

class SuperDlApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLogHandler.install(this)
        ensureHiddenApiAccess()
        QuietModeHelper.reconcileOnStartup(this)
    }

    companion object {
        @Volatile
        private var hiddenApiReady = false

        fun ensureHiddenApiAccess() {
            if (hiddenApiReady) return
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                hiddenApiReady = true
                return
            }
            synchronized(this) {
                if (hiddenApiReady) return
                try {
                    HiddenApiBypass.addHiddenApiExemptions("")
                    hiddenApiReady = true
                } catch (_: Exception) {
                }
            }
        }
    }
}