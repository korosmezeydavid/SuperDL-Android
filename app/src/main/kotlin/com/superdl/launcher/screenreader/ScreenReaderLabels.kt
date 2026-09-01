package com.superdl.launcher.screenreader

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo

/**
 * SAJÁT ELNEVEZÉSEK — a rosszul címkézett alkalmazások megszelídítése.
 *
 * A PROBLÉMA: rengeteg alkalmazásban vannak felirat nélküli gombok. A
 * képernyőolvasó ilyenkor legfeljebb annyit tud mondani, hogy "gomb" — a
 * felhasználó pedig találgathat, melyik mit csinál. Ezen semmilyen okos
 * felismerés nem segít, mert az információ EGYSZERŰEN NINCS OTT.
 *
 * A MEGOLDÁS: ha egyszer kiderítetted, mit csinál az a gomb, ELNEVEZHETED.
 * A név megjegyződik, és onnantól a képernyőolvasó azt mondja.
 *
 * AZONOSÍTÁS: elsősorban az elem belső azonosítója alapján (ez frissítés után
 * is ugyanaz marad), ha az nincs, akkor a képernyőn elfoglalt helye alapján.
 * A nevek alkalmazásonként külön tárolódnak.
 */
object ScreenReaderLabels {

    private const val PREFS = "superdl_screenreader_labels"

    /**
     * Az UJJLENYOMATOK külön fájlban.
     *
     * MIÉRT KÜLÖN: a nevek fájlja "kulcs → név" párokból áll, és ezt járja be a
     * címkekezelő meg a mentés. Ha az ujjlenyomatokat is ide tennénk, minden
     * bejáráskor szét kellene válogatni őket, és egy elrontott szűrő rögtön
     * szemetet mutatna a listában. Két fájl, két tiszta jelentés.
     */
    private const val PREFS_FINGERPRINTS = "superdl_screenreader_fingerprints"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun fingerprints(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_FINGERPRINTS, Context.MODE_PRIVATE)

    /**
     * Az elem azonosító kulcsa.
     * @return null, ha az elem nem azonosítható megbízhatóan
     */
    fun keyOf(node: AccessibilityNodeInfo, packageName: String): String? {
        // 1. A legjobb: a fejlesztő által adott belső azonosító — ez az
        //    alkalmazás frissítése után is jellemzően ugyanaz marad.
        val viewId = try {
            node.viewIdResourceName
        } catch (_: Exception) {
            null
        }
        if (!viewId.isNullOrBlank()) return "$packageName|id|$viewId"

        // 2. Tartalék: a képernyőn elfoglalt hely. Ez törékenyebb (elmozdulhat),
        //    de jobb a semminél. Rácsra kerekítünk, hogy pár képpontnyi
        //    eltolódás ne rontsa el.
        return try {
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            if (r.width() <= 0 || r.height() <= 0) return null
            val gx = r.centerX() / 24
            val gy = r.centerY() / 24
            "$packageName|pos|$gx:$gy"
        } catch (_: Exception) {
            null
        }
    }

    /** A mentett név, vagy null. */
    fun labelFor(context: Context, node: AccessibilityNodeInfo, packageName: String): String? {
        val key = keyOf(node, packageName) ?: return null
        return prefs(context).getString(key, null)?.takeIf { it.isNotBlank() }
    }

    fun setLabel(context: Context, node: AccessibilityNodeInfo, packageName: String, label: String): Boolean {
        val key = keyOf(node, packageName) ?: return false
        prefs(context).edit().putString(key, label.trim()).apply()
        return true
    }

    fun removeLabel(context: Context, node: AccessibilityNodeInfo, packageName: String): Boolean {
        val key = keyOf(node, packageName) ?: return false
        prefs(context).edit().remove(key).apply()
        return true
    }

    // ── KULCS SZERINTI ELÉRÉS ───────────────────────────────────────────────
    //
    // MIÉRT KELL: az elnevezés BILLENTYŰZETTEL egy külön ablakban történik, és
    // addigra a képernyő-elem már nincs meg — másik alkalmazás van elöl. A
    // kulcsot ezért ELŐRE kiszámoljuk, átadjuk az ablaknak, és az ablak már
    // csak a kulccsal dolgozik. Így az elnevezés akkor is elmenthető, ha az
    // eredeti elem közben eltűnt.

    /** A mentett név egy KÉSZ kulcshoz, vagy null. */
    fun labelForKey(context: Context, key: String): String? =
        prefs(context).getString(key, null)?.takeIf { it.isNotBlank() }

    /** Név mentése KÉSZ kulcshoz. Üres név = a címke törlése. */
    fun setLabelByKey(context: Context, key: String, label: String) {
        val trimmed = label.trim()
        val editor = prefs(context).edit()
        if (trimmed.isBlank()) editor.remove(key) else editor.putString(key, trimmed)
        editor.apply()
        // A név törlésével az ujjlenyomat is elmegy — különben egy gazdátlan
        // ujjlenyomat maradna, ami semmire nem mutat.
        if (trimmed.isBlank()) fingerprints(context).edit().remove(key).apply()
    }

    /** Törlés KÉSZ kulcs alapján. Az ujjlenyomat is vele megy. */
    fun removeByKey(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
        fingerprints(context).edit().remove(key).apply()
    }

    // ── UJJLENYOMAT: a MÁSODIK út, ha a pontos kulcs nem talált ─────────────

    /** Ujjlenyomat mentése az elnevezéssel együtt. */
    fun saveFingerprint(context: Context, key: String, print: ElementFingerprint.Print) {
        fingerprints(context).edit().putString(key, print.serialize()).apply()
    }

    /**
     * Egy ujjlenyomatos találat.
     * @param sure ha hamis, a nevet BIZONYTALANKÉNT kell kimondani
     */
    data class Match(val label: String, val sure: Boolean, val score: Float)

    /**
     * Név keresése UJJLENYOMAT alapján, ha a pontos kulcs nem talált.
     *
     * Végigméri az adott alkalmazáshoz mentett ujjlenyomatokat, és a legjobb
     * pontszámút veszi. A küszöb alatt NEM ad vissza semmit — inkább hallgat.
     */
    fun matchByFingerprint(
        context: Context,
        node: AccessibilityNodeInfo,
        packageName: String,
        screenWidth: Int,
        screenHeight: Int
    ): Match? {
        val current = ElementFingerprint.of(node, screenWidth, screenHeight) ?: return null
        val prefix = "$packageName|"

        var bestKey: String? = null
        var bestScore = 0f
        for ((key, value) in fingerprints(context).all) {
            if (!key.startsWith(prefix)) continue
            val stored = ElementFingerprint.parse(value as? String) ?: continue
            val s = ElementFingerprint.score(current, stored)
            if (s > bestScore) {
                bestScore = s
                bestKey = key
            }
        }

        if (bestKey == null || bestScore < ElementFingerprint.THRESHOLD_MAYBE) return null
        val label = labelForKey(context, bestKey) ?: return null
        return Match(label, bestScore >= ElementFingerprint.THRESHOLD_SURE, bestScore)
    }

    // ── A TÁR BEJÁRÁSA ──────────────────────────────────────────────────────

    /**
     * Egy mentett elnevezés, kibontva.
     *
     * A `stable` mező azt mondja meg, MENNYIRE megbízható az azonosítás:
     * az `id`-alapú kulcs az alkalmazás frissítése után is jellemzően talál,
     * a `pos`-alapú (képernyő-helyzet) viszont elmozdulhat. A felhasználónak
     * joga van tudni, melyik névben bízhat — ezért a kezelő ki is mondja.
     */
    data class Entry(
        val key: String,
        val label: String,
        val packageName: String,
        val stable: Boolean
    )

    /** Minden mentett elnevezés, alkalmazás szerint, azon belül név szerint rendezve. */
    fun allLabels(context: Context): List<Entry> {
        val out = mutableListOf<Entry>()
        for ((key, value) in prefs(context).all) {
            val label = (value as? String)?.takeIf { it.isNotBlank() } ?: continue
            // A kulcs alakja: "csomagnév|id|..." vagy "csomagnév|pos|..."
            val parts = key.split("|")
            if (parts.size < 3) continue
            out.add(
                Entry(
                    key = key,
                    label = label,
                    packageName = parts[0],
                    stable = parts[1] == "id"
                )
            )
        }
        return out.sortedWith(compareBy({ it.packageName }, { it.label.lowercase() }))
    }

    /** Hány saját elnevezés van mentve. */
    fun count(context: Context): Int = prefs(context).all.size

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
        fingerprints(context).edit().clear().apply()
    }

    /** Van-e ujjlenyomat ehhez a névhez — a címkekezelő ezt mondja ki. */
    fun hasFingerprint(context: Context, key: String): Boolean =
        !fingerprints(context).getString(key, null).isNullOrBlank()

    /** Az ujjlenyomat NYERS alakja — a beküldés ezt viszi magával. */
    fun fingerprintRaw(context: Context, key: String): String? =
        fingerprints(context).getString(key, null)?.takeIf { it.isNotBlank() }
}
