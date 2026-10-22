package com.superdl.launcher.sms

/** Az ellenőrző képernyőn kiválasztott sablont a már bediktált üzenet végére fűzi. */
object SmsTemplateComposer {
    fun append(prefix: String, template: String): String =
        listOf(prefix.trim(), template.trim()).filter { it.isNotEmpty() }.joinToString(" ")
}
