package com.superdl.launcher.sms

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.superdl.launcher.MainActivity

class SmsComposeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val address = extractAddress(intent)
        val body = intent.getStringExtra("sms_body").orEmpty()
        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (address.isNotBlank()) putExtra(EXTRA_SMS_COMPOSE_ADDRESS, address)
            if (body.isNotBlank()) putExtra(EXTRA_SMS_COMPOSE_BODY, body)
            action = ACTION_SMS_COMPOSE
        }
        startActivity(launch)
        finish()
    }

    /**
     * A CÍMZETT KIOLVASÁSA EGY `smsto:` HIVATKOZÁSBÓL.
     *
     * A HIBA, AMIT EZ JAVÍT: a régi változat a teljes `schemeSpecificPart`-ot
     * adta vissza. Egy `smsto:+3630111,+3630222` alakú hivatkozásnál — és az
     * szabályos, a szabvány engedi a több címzettet — ez egyetlen sztringként
     * jött át, a `SmsHelper.normalizePhoneForSms` pedig a vesszőt kidobja.
     * A két számból így EGY, nem létező szám lett, és az üzenet CSENDBEN
     * rossz helyre ment.
     *
     * Amíg a többcímzettes küldés nincs kész (MK-V, M2), az ELSŐ címzettet
     * vesszük, és a többit inkább elhagyjuk. Egy embernek elküldeni jobb,
     * mint senkinek — de egy kitalált számra elküldeni a legrosszabb.
     */
    private fun extractAddress(intent: Intent): String {
        intent.data?.let { uri ->
            if (uri.scheme == "smsto" || uri.scheme == "sms") {
                val raw = uri.schemeSpecificPart?.substringBefore('?').orEmpty()
                return raw.split(',', ';')
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                    .trim()
            }
        }
        return intent.getStringExtra("address").orEmpty().trim()
    }

    companion object {
        const val ACTION_SMS_COMPOSE = "com.superdl.launcher.action.SMS_COMPOSE"
        const val EXTRA_SMS_COMPOSE_ADDRESS = "sms_compose_address"
        const val EXTRA_SMS_COMPOSE_BODY = "sms_compose_body"
    }
}