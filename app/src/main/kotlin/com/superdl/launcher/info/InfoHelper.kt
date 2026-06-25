package com.superdl.launcher.info

import android.content.Context
import android.content.Intent
import com.superdl.launcher.qr.QrScanActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InfoHelper {

    fun speakDateTime(): String {
        val now = Date()
        val dateFmt = SimpleDateFormat("yyyy. MMMM d., EEEE", Locale("hu", "HU"))
        val timeFmt = SimpleDateFormat("H:mm", Locale("hu", "HU"))
        return "Ma ${dateFmt.format(now)}. Az idő ${timeFmt.format(now)}."
    }

    fun openQrScanner(context: Context): Boolean {
        val intent = Intent(context, QrScanActivity::class.java)
        if (context !is android.app.Activity) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return true
    }
}