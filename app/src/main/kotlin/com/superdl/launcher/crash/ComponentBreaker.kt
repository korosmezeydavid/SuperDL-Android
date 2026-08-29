package com.superdl.launcher.crash

import android.util.Log

/**
 * KOMPONENS-MEGSZAKÍTÓ — ismétlődő hiba esetén a funkció ideiglenesen leáll.
 *
 * MIÉRT KELL, HA MÁR VAN GESZTUS-PAJZS:
 * A pajzs minden alkalommal elkapja a hibát — de a felhasználó közben újra és
 * újra nekifut ugyanannak a funkciónak, és mindig ugyanazt a hibaüzenetet
 * hallja. Ez fárasztó, és el is fedi, hogy a probléma tartós.
 *
 * A megszakító ilyenkor közbelép: HÁROM hiba után a funkció ideiglenesen
 * kikapcsol, és a felhasználó ezt HALLJA is. A SuperDL többi része érintetlen.
 *
 * Ez a képernyőolvasó már bevált hibaszámlálójának elve, kiterjesztve.
 *
 * FONTOS: a letiltás CSAK a program futása idejére szól. Újraindítás után
 * minden újra próbálkozhat — hátha csak átmeneti volt a baj (nem volt hálózat,
 * foglalt volt a kamera, elfogyott a memória).
 */
object ComponentBreaker {

    private const val TAG = "SDL_BREAKER"

    /** Ennyi hiba után kapcsol ki a funkció. */
    private const val FAILURE_THRESHOLD = 3

    /** Ennél régebbi hibát elfelejtünk — csak a SOROZATOS hiba számít. */
    private const val FAILURE_WINDOW_MS = 5 * 60 * 1000L

    private data class Record(var failures: Int, var lastFailureAt: Long)

    private val records = HashMap<String, Record>()

    /** Ki van-e kapcsolva ez a funkció ismétlődő hiba miatt? */
    @Synchronized
    fun isDisabled(component: String): Boolean {
        val record = records[component] ?: return false
        // Ha rég volt az utolsó hiba, tiszta lappal indulunk.
        if (System.currentTimeMillis() - record.lastFailureAt > FAILURE_WINDOW_MS) {
            records.remove(component)
            return false
        }
        return record.failures >= FAILURE_THRESHOLD
    }

    /**
     * Hibát jelentünk egy funkcióra.
     * @return igaz, ha ezzel a hibával a funkció KIKAPCSOLT
     */
    @Synchronized
    fun reportFailure(component: String): Boolean {
        val now = System.currentTimeMillis()
        val record = records.getOrPut(component) { Record(0, now) }
        // Ha rég volt az előző hiba, nem tekintjük sorozatnak.
        if (now - record.lastFailureAt > FAILURE_WINDOW_MS) {
            record.failures = 0
        }
        record.failures++
        record.lastFailureAt = now
        val justDisabled = record.failures == FAILURE_THRESHOLD
        Log.w(TAG, "$component hiba (${record.failures}/$FAILURE_THRESHOLD)" +
            if (justDisabled) " -> IDEIGLENESEN KIKAPCSOLVA" else "")
        return justDisabled
    }

    /** A funkció hibátlanul lefutott — a számláló nullázódik. */
    @Synchronized
    fun reportSuccess(component: String) {
        records.remove(component)
    }

    /** Hány funkció van épp kikapcsolva (a diagnosztikához). */
    @Synchronized
    fun disabledCount(): Int = records.count { it.value.failures >= FAILURE_THRESHOLD }

    /** Minden letiltás feloldása — a menüből. */
    @Synchronized
    fun resetAll() {
        records.clear()
        Log.i(TAG, "minden letiltas feloldva")
    }

    /** Felolvasható állapot. */
    @Synchronized
    fun speakStatus(): String {
        val disabled = records.filter { it.value.failures >= FAILURE_THRESHOLD }
        return if (disabled.isEmpty()) {
            "Minden funkció működik, nincs kikapcsolt rész."
        } else {
            "${disabled.size} funkció van ideiglenesen kikapcsolva ismétlődő hiba miatt. " +
                "A visszakapcsoláshoz söpörj jobbra, vagy indítsd újra a programot."
        }
    }
}
