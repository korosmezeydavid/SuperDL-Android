package com.superdl.launcher.callfilter

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * MELYIK ALKALMAZÁS VAN ELŐTÉRBEN — az alkalmazás szerinti fókuszhoz.
 *
 * MIÉRT A KISEGÍTŐ SZOLGÁLTATÁSOKBÓL: az Android a „melyik app van elöl"
 * kérdésre csak külön, ijesztő nevű engedéllyel (használati adatok) felel.
 * A mi két kisegítő szolgáltatásunk (a képernyőolvasó és a PIN segéd)
 * viszont amúgy is megkapja az ablakváltás-eseményeket — akkor is, ha a
 * képernyőolvasó beszéde ki van kapcsolva. Ezért innen tudjuk, ingyen.
 *
 * MIÉRT CSAK MEMÓRIÁBAN: ez a szám percenként változhat, lemezre írni
 * felesleges kopás. A kisegítő szolgáltatás amúgy is életben tartja a
 * folyamatot; ha mégis újraindul, a következő ablakváltás újra kitölti.
 * Amíg nincs adat, az alkalmazás szerinti fókusz egyszerűen nem hat —
 * vagyis a hiba iránya mindig a „több hívás jön át", sosem a „lemarad".
 */
object ForegroundAppTracker {

    /**
     * KIKAPCSOLT VAGY ZÁROLT KÉPERNYŐNÉL ennyi ideig tartjuk még érvényben.
     *
     * MIÉRT 10 PERC, ÉS MIÉRT NEM RÖVIDEBB ÁLTALÁBAN: egy előtérben lévő
     * alkalmazás tétlenül NEM küld eseményt — aki egy órája nézi a TikTok
     * élő adást, attól sem jön ablakváltás. Ezért bekapcsolt, feloldott
     * képernyőnél NINCS lejárat: ami elöl van, az elöl van. Ha viszont a
     * képernyő sötét vagy zárolt, a felhasználó már nem „használja" az appot;
     * a tíz perc csak arra a helyzetre türelmi idő, amikor épp letette a
     * telefont (pl. egy élő adás szünetében), és ne csörögjön rá azonnal
     * bárki. Utána visszaáll a rendes beállítás.
     */
    private const val SCREEN_OFF_GRACE_MS = 10L * 60L * 1000L

    @Volatile
    private var currentPackage: String? = null

    /** Mikor láttuk utoljára életjelét (SystemClock.elapsedRealtime). */
    @Volatile
    private var lastSeenAt = 0L

    /** Az utoljára lekérdezett alapértelmezett billentyűzet csomagja. */
    @Volatile
    private var cachedImePackage: String? = null

    @Volatile
    private var cachedImeAt = 0L

    /**
     * Hívd a kisegítő szolgáltatás onAccessibilityEvent-jéből.
     *
     * OLCSÓ: tartalomváltozásnál csak egy szöveg-összehasonlítás; a tényleges
     * munka (szűrés, billentyűzet-lekérdezés) csak valódi ablakváltáskor fut.
     */
    fun onAccessibilityEvent(context: Context, event: AccessibilityEvent) {
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return
        val pkg = event.packageName?.toString()
        if (pkg.isNullOrBlank()) return
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // MIÉRT: a tartalomváltozás NEM jelent appváltást (az értesítési
            // sáv órája is küld ilyet) — csak az életjelet frissítjük, ha az
            // előtérben lévő app mozdult.
            if (pkg == currentPackage) lastSeenAt = SystemClock.elapsedRealtime()
            return
        }
        noteForeground(context, pkg)
    }

    /** Egy ablakváltás csomagneve. A rendszer-rétegeket figyelmen kívül hagyjuk. */
    fun noteForeground(context: Context, pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (pkg == currentPackage) {
            lastSeenAt = now
            return
        }
        if (isIgnored(context, pkg)) return
        currentPackage = pkg
        lastSeenAt = now
    }

    /**
     * A SuperDL saját kezdőképernyője került elöl (MainActivity.onResume).
     *
     * MIÉRT KÜLÖN, ÉS MIÉRT NEM A KISEGÍTŐ ESEMÉNYBŐL: a saját csomagunk
     * eseményeit szándékosan eldobjuk, mert a saját billentyűzeteink (mátrix,
     * Braille, diktálás) és lebegő ablakaink is a mi csomagnevünkkel
     * jelentkeznek — egy TikTok fölött felugró billentyűzet ne kapcsolja ki
     * a fókuszt. Hogy tényleg a kezdőképernyőre tértél vissza, azt a
     * tevékenység maga tudja biztosan.
     */
    fun noteOwnAppInFront(context: Context) {
        currentPackage = context.packageName
        lastSeenAt = SystemClock.elapsedRealtime()
    }

    /** A legutóbb elöl látott csomag, feltételek nélkül (állapot-bemondáshoz). */
    fun lastKnownPackage(): String? = currentPackage

    /**
     * Az alkalmazás, amire most az alkalmazás szerinti fókusz vonatkozhat,
     * vagy null. Lásd a SCREEN_OFF_GRACE_MS magyarázatát.
     */
    fun activePackage(context: Context): String? {
        val pkg = currentPackage ?: return null
        if (isScreenOnAndUnlocked(context)) return pkg
        val age = SystemClock.elapsedRealtime() - lastSeenAt
        return if (age in 0L..SCREEN_OFF_GRACE_MS) pkg else null
    }

    /**
     * Fut-e legalább az egyik kisegítő szolgáltatásunk? Enélkül nem tudjuk,
     * mi van elöl, és az alkalmazás szerinti fókusz nem működhet.
     */
    fun isTrackingPossible(context: Context): Boolean = try {
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val prefix = context.packageName + "/"
        enabled.split(':').any { it.trim().startsWith(prefix) }
    } catch (_: Exception) {
        false
    }

    private fun isScreenOnAndUnlocked(context: Context): Boolean = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        pm.isInteractive && !km.isKeyguardLocked
    } catch (_: Exception) {
        false
    }

    /**
     * AMIT NEM TEKINTÜNK APPVÁLTÁSNAK: az értesítési sáv és a hangerő-panel
     * (SystemUI), a rendszer párbeszédablakai („android"), a billentyűzetek,
     * és a saját csomagunk (lásd noteOwnAppInFront). Ezek egy app FÖLÉ
     * nyílnak, az app közben elöl marad.
     */
    private fun isIgnored(context: Context, pkg: String): Boolean {
        if (pkg == "android") return true
        if (pkg.contains("systemui")) return true
        if (pkg.startsWith(context.packageName.removeSuffix(".debug"))) return true
        val lower = pkg.lowercase()
        if (lower.contains("inputmethod") || lower.contains("keyboard") ||
            lower.contains("honeyboard") || lower.contains("swiftkey")
        ) return true
        return pkg == defaultImePackage(context)
    }

    /** Az alapértelmezett billentyűzet csomagja, percenként legfeljebb egyszer lekérdezve. */
    private fun defaultImePackage(context: Context): String? {
        val now = SystemClock.elapsedRealtime()
        if (cachedImeAt != 0L && now - cachedImeAt in 0L..60_000L) return cachedImePackage
        val value = try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
            )?.substringBefore('/')?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
        cachedImePackage = value
        cachedImeAt = now
        return value
    }
}
