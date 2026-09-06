package com.superdl.launcher.setup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * A BEÁLLÍTÁS VARÁZSLÓ TELJES ÁLLAPOTA — EGYETLEN SZÖVEGBEN.
 *
 * MIÉRT KELL, ÉS MIÉRT PONT EZ VAN BENNE:
 *
 * A 2026-09-05-i tesztelői felvétel megmutatta, hogy a legdrágább hiba az,
 * amiről a felhasználó csak annyit tud mondani: „nem megy". Egy vak tesztelő
 * nem tudja megnézni, hogy a rendszer megnyitotta-e az ablakot, azt sem, hogy
 * a szándék egyáltalán feloldható-e a készüléken. Nekünk viszont pont ez a
 * két adat hiányzott — és emiatt ment el napokban mérhető idő.
 *
 * Ezért a napló nem azt írja le, hogy MI HIÁNYZIK (azt a felhasználó is
 * elmondja), hanem azt, hogy MIÉRT NEM SIKERÜLT:
 *
 *  - feloldható-e a rendszer-szándék ezen a készüléken (`resolveActivity`) —
 *    ha nem, akkor a program hiába indítja el, soha nem nyílik meg semmi;
 *  - hányszor próbálta már a felhasználó (a varázsló számolja);
 *  - ki BIRTOKOLJA most a szerepkört (üzenet, telefon, kezdőképernyő) —
 *    ebből derül ki az az eset, amikor a rendszerben be van állítva, a
 *    program mégsem látja;
 *  - honnan lett telepítve a program — mert Android 13 óta az áruházon
 *    KÍVÜLRŐL telepített alkalmazásnak a rendszer letiltja a kisegítő
 *    szolgáltatást, az értesítés-hozzáférést és a fölérajzolást, és erről
 *    vakon szinte semmit nem mond.
 *
 * ADATVÉDELEM: ebben a naplóban nincs személyes adat. Csomagnevek,
 * engedélyállapotok és rendszerverzió van benne, semmi más.
 */
object SetupDiagnostics {

    /**
     * @param attempts a varázsló próbálkozás-számlálója (tétel azonosító → darab)
     */
    fun build(context: Context, attempts: Map<String, Int> = emptyMap()): String = buildString {
        appendLine("BEÁLLÍTÁS VARÁZSLÓ — RÉSZLETES NAPLÓ")
        appendLine("========================================")
        appendLine("telepítés forrása: ${installSource(context)}")
        appendLine("korlátozott beállítás érintheti: ${if (isSideloaded(context)) "IGEN" else "nem"}")
        appendLine()

        appendLine("SZEREPKÖRÖK — ki birtokolja MOST:")
        appendLine("  alapértelmezett üzenet app: ${defaultSms(context)}")
        appendLine("  alapértelmezett telefon app: ${defaultDialer(context)}")
        appendLine("  kezdőképernyő: ${defaultHome(context)}")
        appendLine("  saját csomagnév: ${context.packageName}")
        appendLine()

        appendLine("KÉT SUPERDL EGY TELEFONON: ${twoBuilds(context)}")
        appendLine()

        // A SZEREPKÖR-KEZELŐ KÜLÖN — MERT A KÉT RÉTEG NEM MINDIG ÉRT EGYET.
        //
        // 2026-09-06, Galaxy S24 Ultra: a tesztelő beállította a SuperDL-t
        // üzenet alkalmazásnak, az SMS-ek működtek is, a varázsló mégis
        // hiányzónak mutatta. A régi Telephony-lekérdezés üresen tért vissza,
        // miközben a szerepkör a miénk volt. A naplóból ez akkor NEM látszott,
        // csak következtetni lehetett rá — ezért kerül ide külön sorba.
        appendLine("SZEREPKÖR-KEZELŐ (Android 10 óta ez a mérvadó):")
        for ((cimke, szerep) in roleNames()) {
            appendLine("  $cimke: ${roleState(context, szerep)}")
        }
        appendLine()

        appendLine("ÉRTESÍTÉS-HOZZÁFÉRÉS ENGEDÉLYEZETT SZOLGÁLTATÁSOK:")
        appendLine("  ${secure(context, "enabled_notification_listeners")}")
        appendLine("KISEGÍTŐ SZOLGÁLTATÁSOK (engedélyezett):")
        appendLine("  ${secure(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)}")
        appendLine("  érintés-felfedezés: ${secure(context, "touch_exploration_enabled")}")
        appendLine()

        appendLine("TÉTELEK — mind, a megadottak is:")
        appendLine("  jelmagyarázat: [megvan] [hiányzik] [kihagyva]")
        appendLine("  „szándék\": megnyitható-e a rendszer képernyője ezen a telefonon")
        appendLine()

        val all = try {
            SetupRequirements.all(context)
        } catch (_: Exception) {
            emptyList()
        }
        if (all.isEmpty()) {
            appendLine("  (a tételek felmérése nem sikerült)")
        }
        for (req in all) {
            val state = when {
                req.granted -> "megvan"
                SetupPrefs.isSkipped(context, req.id) -> "kihagyva"
                else -> "HIÁNYZIK"
            }
            appendLine("- ${req.id} — ${req.title}")
            appendLine("    állapot: $state   szint: ${req.severity}   mód: ${req.kind}")
            val tries = attempts[req.id] ?: 0
            if (tries > 0) appendLine("    próbálkozás: $tries")
            if (req.kind != SetupRequirements.RequestKind.RUNTIME) {
                appendLine("    szándék: ${intentState(context, req)}")
            } else if (req.permissions.isNotEmpty()) {
                appendLine("    engedélyek: ${req.permissions.joinToString(", ") { it.substringAfterLast('.') }}")
            }
        }
        appendLine()

        // DIRECT BOOT: fel van-e oldva a készülék, és él-e a beszédmotor.
        //
        // MIÉRT KELL: az első feloldás ELŐTT a rendszer beszédmotorja nem
        // indul el, ezért a PIN segéd némán jelenik meg. Ezt a tesztelő csak
        // úgy tudja leírni, hogy „nem beszél" — a naplóban viszont látszik,
        // hogy a fázis miatt van-e, vagy másért.
        appendLine("BESZÉD ÉS DIRECT BOOT:")
        appendLine("  a készülék fel van oldva: ${userUnlocked(context)}")
        appendLine("  beszédmotorok a rendszerben: ${ttsEngines(context)}")
        appendLine("  beépített zárképernyő-hangok: ${lockClips(context)}")
        appendLine()

        appendLine("RENDSZER: ${Build.MANUFACTURER} ${Build.MODEL}, Android " +
            "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
            "build ${Build.DISPLAY}")
    }

    /** A varázslóban szereplő szerepkörök, felolvasható névvel. */
    private fun roleNames(): List<Pair<String, String>> {
        if (Build.VERSION.SDK_INT < 29) return emptyList()
        return listOf(
            "üzenet (SMS)" to android.app.role.RoleManager.ROLE_SMS,
            "telefon (DIALER)" to android.app.role.RoleManager.ROLE_DIALER,
            "kezdőképernyő (HOME)" to android.app.role.RoleManager.ROLE_HOME,
            "asszisztens (ASSISTANT)" to android.app.role.RoleManager.ROLE_ASSISTANT,
            "hívásszűrő (CALL_SCREENING)" to android.app.role.RoleManager.ROLE_CALL_SCREENING
        )
    }

    /**
     * Egy szerepkör állapota HÁROM adattal: elérhető-e, miénk-e, és kérhető-e.
     *
     * A „kérhető" a döntő: az Android a szerepköröket egy jelzővel írja le, és
     * amelyik nem kérhető, ott a felugró ablak MEG SEM JELENIK. Ezen bukott el
     * az asszisztens az 1.62.1 előtt.
     */
    private fun roleState(context: Context, role: String): String = try {
        if (Build.VERSION.SDK_INT < 29) {
            "nincs szerepkör-kezelő ezen az Android verzión"
        } else {
            val rm = context.getSystemService(android.app.role.RoleManager::class.java)
            if (rm == null) {
                "a szerepkör-kezelő nem érhető el"
            } else {
                val elerheto = rm.isRoleAvailable(role)
                val mienk = elerheto && rm.isRoleHeld(role)
                buildString {
                    append(if (elerheto) "elérhető" else "NEM ELÉRHETŐ ezen a készüléken")
                    append(", ")
                    append(if (mienk) "MIÉNK" else "nem a miénk")
                }
            }
        }
    } catch (e: Exception) {
        "a vizsgálat hibára futott: ${e.javaClass.simpleName}"
    }

    private fun userUnlocked(context: Context): String = try {
        val um = context.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
        when (um?.isUserUnlocked) {
            true -> "igen"
            false -> "NEM (Direct Boot fázis — a beszédmotor ilyenkor nem él)"
            else -> "nem lekérdezhető"
        }
    } catch (_: Exception) {
        "nem lekérdezhető"
    }

    private fun ttsEngines(context: Context): String = try {
        val intent = Intent("android.speech.tts.engine.INTENT_ACTION_TTS_SERVICE")
        val list = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentServices(
                intent, PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, 0)
        }
        if (list.isEmpty()) "egy sem található"
        else list.joinToString(", ") { it.serviceInfo?.packageName.orEmpty() }
    } catch (_: Exception) {
        "nem lekérdezhető"
    }

    private fun lockClips(context: Context): String = try {
        val n = context.assets.list("zarhang")?.size ?: 0
        if (n > 0) "$n klip a programcsomagban" else "NINCSENEK"
    } catch (_: Exception) {
        "nem lekérdezhető"
    }

    // ── Az egyes adatok ─────────────────────────────────────────────────────

    /**
     * MEGNYÍLNA-E EGYÁLTALÁN. Ez a napló legfontosabb sora.
     *
     * Ha itt „nincs szándék" vagy „NEM oldható fel" áll, akkor a program
     * hiába indítja el a kérést: azon a készüléken ez a képernyő nem
     * létezik ezen a néven. Ezt kívülről semmiből nem lehet megtudni.
     */
    private fun intentState(context: Context, req: SetupRequirements.Requirement): String = try {
        val intent = SetupRequirements.systemIntentFor(context, req)
        when {
            intent == null && req.granted -> "nincs szándék (már megvan, ez rendben van)"
            intent == null -> "NINCS SZÁNDÉK — a program az app beállítás-oldalára navigálna"
            resolves(context, intent) -> "feloldható (${intent.action ?: "nincs művelet"})"
            else -> "NEM OLDHATÓ FEL EZEN A KÉSZÜLÉKEN (${intent.action ?: "nincs művelet"})"
        }
    } catch (e: Exception) {
        "a vizsgálat hibára futott: ${e.javaClass.simpleName}"
    }

    private fun resolves(context: Context, intent: Intent): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(0L)
            ) != null
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, 0) != null
        }
    } catch (_: Exception) {
        false
    }

    private fun installSource(context: Context): String = try {
        val pm = context.packageManager
        val name = if (Build.VERSION.SDK_INT >= 30) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
        name ?: "ismeretlen (kézi telepítés vagy adb)"
    } catch (_: Exception) {
        "nem lekérdezhető"
    }

    /**
     * Android 13 óta az áruházon kívülről telepített alkalmazásnak a rendszer
     * letiltja a kisegítő szolgáltatást, az értesítés-hozzáférést és a
     * fölérajzolást, amíg a felhasználó fel nem oldja („Korlátozott
     * beállítások engedélyezése").
     */
    private fun isSideloaded(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val src = installSource(context)
        return !src.startsWith("com.android.vending") && !src.startsWith("com.google.android.packageinstaller")
    }

    private fun defaultSms(context: Context): String = try {
        android.provider.Telephony.Sms.getDefaultSmsPackage(context) ?: "nincs"
    } catch (_: Exception) {
        "nem lekérdezhető"
    }

    private fun defaultDialer(context: Context): String = try {
        val tm = context.getSystemService(android.telecom.TelecomManager::class.java)
        tm?.defaultDialerPackage ?: "nincs"
    } catch (_: Exception) {
        "nem lekérdezhető"
    }

    private fun defaultHome(context: Context): String = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        info?.activityInfo?.packageName ?: "nincs"
    } catch (_: Exception) {
        "nem lekérdezhető"
    }

    /**
     * FENT VAN-E A MÁSIK VÁLTOZAT IS.
     *
     * A fejlesztői és a kiadási változat KÜLÖN alkalmazás (a csomagnév végén
     * `.debug`), tehát egymás mellett is felférnek. Ilyenkor viszont KÉT
     * képernyőolvasó és két PIN segéd van a rendszerben, és a kettő
     * egymásra beszél — a felhasználó azt hallja, hogy „összevissza beszél a
     * telefon". A rendszer listái ezt elárulják, ezért innen olvassuk ki:
     * más alkalmazás lekérdezéséhez Android 11 óta külön jog kellene.
     */
    private fun twoBuilds(context: Context): String {
        val mine = context.packageName
        val other = if (mine.endsWith(".debug")) mine.removeSuffix(".debug") else "$mine.debug"
        val haystack = listOf(
            secure(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            secure(context, "enabled_notification_listeners"),
            secure(context, Settings.Secure.DEFAULT_INPUT_METHOD)
        ).joinToString(":")
        val seen = haystack.split(':').any { it.substringBefore('/').trim() == other }
        return if (seen) {
            "IGEN — a $other is fent van és be van kapcsolva. Ez ütközést okoz " +
                "(két képernyőolvasó egymásra beszél), az egyiket el kell távolítani."
        } else {
            "nem látszik"
        }
    }

    private fun secure(context: Context, key: String): String = try {
        val value = Settings.Secure.getString(context.contentResolver, key)
        if (value.isNullOrBlank()) "(üres)" else value
    } catch (_: Exception) {
        "nem lekérdezhető"
    }
}
