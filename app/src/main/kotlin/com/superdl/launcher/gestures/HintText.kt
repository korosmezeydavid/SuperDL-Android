package com.superdl.launcher.gestures

import android.widget.TextView

/**
 * A KIJELZŐ SÚGÓSORA — egyetlen pont, ahol az irányszavak elforgatódnak.
 *
 * MIÉRT NEM SZERKESZTETTÜK ÁT A 88 HELYET: a MainActivity nyolcvannyolc
 * helyen írja ki a súgósort. Ha mindegyiket kézzel kellene átfordítani, az
 * nemcsak most lenne fáradságos, hanem a JÖVŐBEN ÍRT képernyők is kimaradnának
 * belőle — és egy kimaradt képernyő vakon pontosan olyan rossz, mintha az
 * egész nem működne. Így viszont a `tvHint.text = ...` alakú sorok
 * változatlanul maradtak, a fordítás mégis mindre érvényes.
 *
 * A `text` tulajdonság neve szándékosan azonos a TextView-éval: a hívó kód
 * egyetlen karakterét sem kellett módosítani.
 */
class HintText(private val view: TextView) {

    var text: CharSequence
        get() = view.text ?: ""
        set(value) {
            view.text = GestureWords.translateHint(value.toString())
        }
}
