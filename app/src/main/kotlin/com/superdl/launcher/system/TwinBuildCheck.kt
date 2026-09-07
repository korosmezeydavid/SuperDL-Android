package com.superdl.launcher.system

import android.content.Context
import android.content.pm.PackageManager

/**
 * KÉT SUPERDL EGY TELEFONON — FELISMERÉS ÉS FIGYELMEZTETÉS.
 *
 * A HIBA, AMIT EZ JAVÍT (Alph, 2026-09-05, péntek):
 *
 *   „megszívat, amikor nappal a kettő egyszerre akar reagálni, mintha két
 *    portás lenne egy fülkében és azon verekednének, ki fogadhatja a
 *    hívásomat. Cserébe egyiknek se ment, alig tudtam magam kiküzdeni."
 *
 * Vagyis: nem kényelmetlenség, hanem EGY NEM FOGADOTT HÍVÁS. Vakon, a
 * kezdőképernyőn ragadva.
 *
 * MIÉRT NEM VETTE ÉSZRE A PROGRAM: a korábbi felismerés (SetupDiagnostics)
 * csak azt nézte, hogy a másik példány BE VAN-E KAPCSOLVA kisegítő
 * szolgáltatásként, értesítés-olvasóként vagy billentyűzetként. Csakhogy a
 * hívásokért nem ezek felelnek: mindkét példány önálló
 * `InCallService`-t, hívás- és SMS-vevőt és kezdőképernyőt hoz magával —
 * pusztán attól, hogy TELEPÍTVE van. A kapcsolók állása ehhez nem kell.
 *
 * Ezért a felismerés a TELEPÍTETTSÉGET nézi, nem a bekapcsoltságot.
 *
 * MIÉRT KELL KIMONDANI: két azonos nevű, azonos ikonú alkalmazás a
 * telefonon vakon MEGKÜLÖNBÖZTETHETETLEN. Aki nem látja őket, annak esélye
 * sincs rájönni, mi ütközik — a tünet csak annyi, hogy „nem megy a hívás".
 * A programnak kell szólnia, mert csak ő tudja.
 */
object TwinBuildCheck {

    /**
     * A testvér-példány csomagneve, ha telepítve van; egyébként null.
     *
     * A `.debug` utótag a fejlesztői változaté. Ha mi vagyunk az, akkor a
     * rendes változatot keressük, és fordítva.
     */
    fun installedTwin(context: Context): String? {
        val mine = context.packageName
        val other = if (mine.endsWith(".debug")) {
            mine.removeSuffix(".debug")
        } else {
            "$mine.debug"
        }
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(other, 0)
            other
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            // Ha nem tudjuk megállapítani, NEM riasztunk feleslegesen.
            null
        }
    }

    /**
     * SZÁNDÉKOSAN NINCS INDULÁSI HANGOS FIGYELMEZTETÉS.
     *
     * Volt egy változat, ami minden induláskor bemondta. Kikerült: két
     * példány egy telefonon a gyakorlatban CSAK FEJLESZTÉS KÖZBEN fordul
     * elő, a tesztelőknél nem. Egy ritka esetre épített, minden indulásnál
     * megszólaló figyelmeztetés több kárt okoz, mint hasznot — és pont azt a
     * bizalmat kezdi ki, amivel a felhasználó a program hangjára hallgat.
     *
     * A felismerés viszont marad, mert a HIBAJELENTÉSNEK igazat kell
     * mondania: lásd SetupDiagnostics.twoBuilds(). Ha valaha kiderül, hogy
     * ez tesztelőknél is előfordul, a figyelmeztetés innen egy hívással
     * visszatehető.
     */
}
