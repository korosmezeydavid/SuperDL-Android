package com.superdl.launcher.screenreader

/**
 * KÉPERNYŐ-VÁLTOZÁS FIGYELŐ.
 *
 * MIÉRT KELL: vannak felületek, amik MÁSODPERCENKÉNT TÖBBSZÖR frissülnek —
 * élő adás, lejátszó időzítője, töltésjelző, csevegő, mérőóra. Ilyenkor a
 * képernyőolvasó folyamatosan újraolvasná a teljes elemfát, és ha az
 * automatikus felolvasás be van kapcsolva, újra és újra megszólalna.
 *
 * A felhasználó ezt úgy éli meg, hogy a program "megkergült" vagy lelassult —
 * pedig csak becsületesen követi a képernyőt.
 *
 * Ez az osztály MÉRI a változások ütemét, és megmondja, mikor "nyugtalan" a
 * képernyő. Nem dönt semmiről — csak tényt szolgáltat, a döntés a hívóé.
 */
class ScreenChangeMonitor {

    /** Az utolsó változások időpontjai (csak a friss ablak). */
    private val recentChanges = ArrayDeque<Long>()

    /** Ekkora időablakot vizsgálunk. */
    private val windowMs = 2_000L

    /** Ennyi változás fölött tekintjük nyugtalannak a képernyőt. */
    private val restlessThreshold = 12

    /** Mikor jeleztük utoljára a felhasználónak, hogy nyugtalan a képernyő. */
    private var lastAnnouncedAt = 0L

    /** Mikor olvastuk újra utoljára a teljes elemfát. */
    private var lastRebuildAt = 0L

    /** Egy változás feljegyzése. */
    @Synchronized
    fun onContentChanged() {
        val now = System.currentTimeMillis()
        recentChanges.addLast(now)
        prune(now)
    }

    /** Nyugtalan-e ÉPPEN a képernyő? */
    @Synchronized
    fun isRestless(): Boolean {
        prune(System.currentTimeMillis())
        return recentChanges.size >= restlessThreshold
    }

    /**
     * Szabad-e MOST újraolvasni a teljes elemfát?
     *
     * Nyugodt képernyőn mindig igen. Nyugtalan képernyőn viszont felesleges
     * másodpercenként tízszer újraépíteni: a felhasználó úgysem tud olyan
     * gyorsan olvasni, viszont a processzor és az akkumulátor issza meg.
     *
     * @param minIntervalMs nyugtalan képernyőn ennyi idő teljen el két
     *        újraolvasás között
     */
    @Synchronized
    fun mayRebuild(minIntervalMs: Long = 400L): Boolean {
        if (!isRestless()) return true
        val now = System.currentTimeMillis()
        return now - lastRebuildAt >= minIntervalMs
    }

    @Synchronized
    fun onRebuilt() {
        lastRebuildAt = System.currentTimeMillis()
    }

    /**
     * Szóljunk-e a felhasználónak, hogy nyugtalan a képernyő?
     * Csak EGYSZER, és utána sokáig nem — ez tájékoztatás, nem nyaggatás.
     */
    @Synchronized
    fun shouldAnnounceRestless(): Boolean {
        if (!isRestless()) return false
        val now = System.currentTimeMillis()
        if (now - lastAnnouncedAt < 60_000L) return false
        lastAnnouncedAt = now
        return true
    }

    /** Új képernyőre érkeztünk: tiszta lappal indulunk. */
    @Synchronized
    fun reset() {
        recentChanges.clear()
        lastAnnouncedAt = 0L
        lastRebuildAt = 0L
    }

    private fun prune(now: Long) {
        while (recentChanges.isNotEmpty() && now - recentChanges.first() > windowMs) {
            recentChanges.removeFirst()
        }
    }
}
