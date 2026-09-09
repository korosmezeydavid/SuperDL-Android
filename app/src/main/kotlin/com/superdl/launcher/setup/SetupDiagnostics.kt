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
        if (isSideloaded(context)) {
            appendLine("korlátozott beállítás érintheti: IGEN")
            appendLine("  >>> Android 13 óta az áruházon kívülről telepített programnál a")
            appendLine("  >>> rendszer NÉMÁN letiltja a kisegítő szolgáltatást (PIN segéd,")
            appendLine("  >>> képernyőolvasó), az értesítés-olvasást és a fölérajzolást.")
            appendLine("  >>> A kapcsoló látszik, meg is nyomható, de nem történik semmi.")
            appendLine("  >>> FELOLDÁS: Beállítások, Alkalmazások, Super DL, jobbra fent a")
            appendLine("  >>> három pont, majd „Korlátozott beállítások engedélyezése\".")
        } else {
            appendLine("korlátozott beállítás érintheti: nem")
        }
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
            // A TÁROLT SZÁMLÁLÓ A MÉRVADÓ, HA NAGYOBB. A memóriában tartott
            // térkép a program minden leállításakor nullázódik — MIUI-n ez
            // óránként többször megtörténik —, a lemezre írt szám viszont
            // túléli. A kettő közül a nagyobb az igaz.
            val tries = maxOf(attempts[req.id] ?: 0, SetupPrefs.attemptCount(context, req.id))
            if (tries > 0) appendLine("    próbálkozás: $tries")
            // MI TÖRTÉNT AZ UTOLSÓ PRÓBÁLKOZÁSKOR.
            //
            // Enélkül egy „próbálkozás: 11" sorból nem derül ki, hogy a
            // felhasználó tizenegyszer mondott-e nemet, vagy a rendszer
            // tizenegyszer meg sem kérdezte. A két eset két különböző hiba,
            // és eddig egyformán néztek ki. (szonye48, Xiaomi, Android 16.)
            SetupPrefs.lastOutcome(context, req.id)?.let {
                appendLine("    utolsó próbálkozás: $it")
            }
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
        // AZ ACTION NEVE NEM AZ ÉRTÉKE.
        //
        // A HIBA, AMIT EZ JAVÍT (Xiaomi M2103K19G, 1.63.2-es hibajelentés):
        // a napló azt írta, hogy „beszédmotorok a rendszerben: egy sem
        // található" — egy olyan telefonon, amin fut a TalkBack, tehát
        // biztosan VAN beszédmotor.
        //
        // Az ok: itt a konstans NEVE szerepelt szövegként
        // ("android.speech.tts.engine.INTENT_ACTION_TTS_SERVICE"), miközben az
        // ÉRTÉKE "android.intent.action.TTS_SERVICE". Nem létező szándékra
        // kérdeztünk rá, és az üres választ tényként olvastuk.
        //
        // Ugyanez a hiba volt a manifest <queries> blokkjában is, ott viszont
        // nem csak a naplót rontotta el: az Android 11 óta kötelező
        // láthatósági bejegyzés sem ért semmit.
        //
        // A többi hívási hely (TtsEngineHelper, BookTtsPrefs) végig helyesen a
        // konstanst használta — ezért nem tűnt fel korábban.
        val intent = Intent(android.speech.tts.TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
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
    /**
     * A CSOMAGTELEPÍTŐ NEM ÁRUHÁZ — ÉPP ELLENKEZŐLEG.
     *
     * A HIBA, AMIT EZ JAVÍT (Géza, Xiaomi M2103K19G, Android 13, 1.63.4):
     * a jelentése szerint „telepítés forrása: com.google.android.packageinstaller"
     * és „korlátozott beállítás érintheti: nem" — miközben a PIN segédet és a
     * képernyőolvasót kétszeri próbálkozásra sem tudta bekapcsolni.
     *
     * A korábbi feltétel a `com.google.android.packageinstaller`-t is
     * megbízható forrásnak vette. Csakhogy az a RENDSZER CSOMAGTELEPÍTŐJE:
     * pontosan azt jelenti, hogy a felhasználó kézzel nyitott meg egy APK
     * fájlt — vagyis EZ maga az áruházon kívüli telepítés, ami az Android 13
     * korlátozott beállításait életbe lépteti.
     *
     * Megbízható forrás egyedül az áruház (`com.android.vending`). A gyártói
     * áruházak (Galaxy Store, Huawei AppGallery) szintén azok, de a
     * telepítőik nem.
     *
     * Miért fájt ez ennyire: a felhasználó a Kisegítő lehetőségeknél LÁTJA a
     * kapcsolót, meg is nyomja, és a rendszer némán nem engedi. Nem hibaüzenet
     * jön, hanem semmi. Vakon ez teljesen kifürkészhetetlen — és a
     * hibajelentésünk, ami megmondhatta volna, épp az ellenkezőjét állította.
     */
    private fun isSideloaded(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val src = installSource(context)
        val aruhazak = listOf(
            "com.android.vending",          // Google Play
            "com.sec.android.app.samsungapps", // Galaxy Store
            "com.huawei.appmarket",         // AppGallery
            "com.amazon.venezia"            // Amazon Appstore
        )
        return aruhazak.none { src.startsWith(it) }
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
    /**
     * A TELEPÍTETTSÉGET NÉZZÜK, NEM A BEKAPCSOLTSÁGOT.
     *
     * A HIBA, AMIT EZ JAVÍT (Alph, 2026-09-05): a korábbi változat csak azt
     * vizsgálta, hogy a másik példány BE VAN-E KAPCSOLVA kisegítő
     * szolgáltatásként, értesítés-olvasóként vagy billentyűzetként. Ezért
     * „nem látszik"-ot írt olyan telefonon, amin KÉT SuperDL volt fent — és
     * közben a két példány a bejövő hívásokon veszekedett, úgy, hogy
     * egyiknek sem sikerült fogadnia.
     *
     * A hívásokért nem a kisegítő kapcsolók felelnek: mindkét példány saját
     * InCallService-t, hívás- és SMS-vevőt és kezdőképernyőt hoz magával,
     * pusztán attól, hogy telepítve van.
     */
    private fun twoBuilds(context: Context): String {
        val other = try {
            com.superdl.launcher.system.TwinBuildCheck.installedTwin(context)
        } catch (_: Exception) {
            null
        } ?: return "nem látszik (csak ez az egy példány van telepítve)"

        // Külön kiírjuk, hogy a másik BE IS van-e kapcsolva valahol — a
        // hívásütközéshez ez nem kell, de a képernyőolvasó-ütközéshez igen.
        val haystack = listOf(
            secure(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            secure(context, "enabled_notification_listeners"),
            secure(context, Settings.Secure.DEFAULT_INPUT_METHOD)
        ).joinToString(":")
        val bekapcsolva = haystack.split(':').any { it.substringBefore('/').trim() == other }

        return "IGEN — a $other IS TELEPÍTVE VAN. " +
            "Mindkét példány jelentkezik a bejövő hívásra, az SMS-re és a " +
            "kezdőképernyőre, ezért a hívásfogadás nem megbízható. Az egyiket " +
            "el kell távolítani. " +
            if (bekapcsolva) {
                "Ráadásul kisegítő szolgáltatásként is be van kapcsolva, " +
                    "tehát két képernyőolvasó beszél egymásra."
            } else {
                "(Kisegítő szolgáltatásként nincs bekapcsolva — de a " +
                    "hívásütközéshez ez nem is kell.)"
            }
    }

    private fun secure(context: Context, key: String): String = try {
        val value = Settings.Secure.getString(context.contentResolver, key)
        if (value.isNullOrBlank()) "(üres)" else value
    } catch (_: Exception) {
        "nem lekérdezhető"
    }
}
