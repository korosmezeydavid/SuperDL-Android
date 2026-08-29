package com.superdl.launcher.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * ALKALMAZÁS-KATEGÓRIÁK.
 *
 * MIÉRT KELL: a telefonon simán lehet száz alkalmazás. Egyetlen hosszú,
 * ömlesztett listában végigsöpörni rajtuk kimerítő — főleg felolvasva.
 * Kategóriákba rendezve viszont pár mozdulat bármelyik.
 *
 * HONNAN TUDJUK A KATEGÓRIÁT: az Android maga besorolja az alkalmazásokat
 * (ApplicationInfo.category) — a fejlesztő adja meg a boltban. Ez ingyen
 * pontos adat, csak eddig nem használtuk ki. Amit a rendszer nem sorol be,
 * azt a csomagnév és a név alapján próbáljuk kitalálni, és ami így sem megy,
 * az az "Egyéb" csoportba kerül.
 */
enum class AppCategory(val label: String) {
    MUSIC("Zene és hang"),
    VIDEO("Videó és film"),
    SOCIAL("Kapcsolattartás"),
    GAMES("Játékok"),
    NEWS("Hírek és olvasás"),
    MAPS("Térkép és közlekedés"),
    PRODUCTIVITY("Munka és ügyintézés"),
    PHOTO("Fénykép és kamera"),
    SHOPPING("Vásárlás és pénzügy"),
    ACCESSIBILITY("Akadálymentesítés"),
    SYSTEM("Rendszer és beállítás"),
    OTHER("Egyéb alkalmazások");

    companion object {

        /** Egy alkalmazás besorolása. */
        fun of(context: Context, packageName: String, label: String): AppCategory {
            fromSystem(context, packageName)?.let { return it }
            return fromNameGuess(packageName, label)
        }

        /** 1. A rendszer saját besorolása — ez a legmegbízhatóbb. */
        private fun fromSystem(context: Context, packageName: String): AppCategory? = try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            when (info.category) {
                ApplicationInfo.CATEGORY_AUDIO -> MUSIC
                ApplicationInfo.CATEGORY_VIDEO -> VIDEO
                ApplicationInfo.CATEGORY_IMAGE -> PHOTO
                ApplicationInfo.CATEGORY_SOCIAL -> SOCIAL
                ApplicationInfo.CATEGORY_NEWS -> NEWS
                ApplicationInfo.CATEGORY_MAPS -> MAPS
                ApplicationInfo.CATEGORY_GAME -> GAMES
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> PRODUCTIVITY
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> ACCESSIBILITY
                else -> null
            }
        } catch (_: Exception) {
            null
        }

        /**
         * 2. Tartalék: a csomagnév és a megjelenő név alapján tippelünk.
         * A rendszer sok alkalmazásnál nem ad besorolást (főleg a régebbieknél),
         * és a puszta "Egyéb" csoport használhatatlanul nagy lenne.
         */
        private fun fromNameGuess(packageName: String, label: String): AppCategory {
            val p = packageName.lowercase()
            val n = label.lowercase()

            fun any(vararg keys: String) = keys.any { p.contains(it) || n.contains(it) }

            return when {
                any("music", "audio", "spotify", "deezer", "podcast", "radio", "zene", "player") -> MUSIC
                any("video", "youtube", "netflix", "film", "movie", "tv", "player.video") -> VIDEO
                any("messenger", "whatsapp", "viber", "telegram", "signal", "facebook",
                    "instagram", "tiktok", "chat", "mail", "gmail", "dialer", "contacts") -> SOCIAL
                any("game", "jatek", "puzzle", "chess", "sudoku", "solitaire", "play.games") -> GAMES
                any("news", "hir", "rss", "reader", "book", "konyv", "kindle") -> NEWS
                any("maps", "waze", "navigation", "transit", "menetrend", "bkk", "mav") -> MAPS
                any("office", "word", "excel", "docs", "sheets", "drive", "note", "calendar",
                    "keep", "pdf", "scan", "bank.doc") -> PRODUCTIVITY
                any("camera", "gallery", "photo", "kamera", "kep") -> PHOTO
                any("bank", "otp", "revolut", "wallet", "pay", "shop", "webshop", "wolt",
                    "foodpanda", "aruhaz") -> SHOPPING
                any("talkback", "accessibility", "screenreader", "commentary", "braille",
                    "magnif") -> ACCESSIBILITY
                any("settings", "com.android.", "com.google.android.gms", "system",
                    "provider", "install") -> SYSTEM
                else -> OTHER
            }
        }

        /**
         * Alkalmazások csoportosítása kategóriánként.
         * Az ÜRES kategóriák kimaradnak, és a csoportok a felsorolás sorrendjében
         * jönnek — a leggyakrabban használtak elöl.
         */
        fun group(context: Context, apps: List<ExternalApp>): List<Pair<AppCategory, List<ExternalApp>>> {
            val map = linkedMapOf<AppCategory, MutableList<ExternalApp>>()
            apps.forEach { app ->
                val category = of(context, app.packageName, app.label)
                map.getOrPut(category) { mutableListOf() }.add(app)
            }
            return entries
                .mapNotNull { cat ->
                    map[cat]?.takeIf { it.isNotEmpty() }?.let { list ->
                        cat to list.sortedBy { it.label.lowercase() }
                    }
                }
        }
    }
}
