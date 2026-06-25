package com.superdl.launcher

import android.app.Application
import com.superdl.launcher.crash.CrashLogHandler

class SuperDlApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLogHandler.install(this)
    }
}