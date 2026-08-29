package com.superdl.launcher.store

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log

/**
 * A TELEPÍTETT SUPERDL-MODULOK MEGKERESÉSE.
 *
 * HOGYAN JELÖLI MEG MAGÁT EGY MODUL:
 * A modul alkalmazás manifestjében kell lennie egy tevékenységnek ezzel a
 * szűrővel és leíró adatokkal:
 *
 *   <activity android:name=".MainActivity" android:exported="true">
 *       <intent-filter>
 *           <action android:name="com.superdl.launcher.MODULE" />
 *           <category android:name="android.intent.category.DEFAULT" />
 *       </intent-filter>
 *       <meta-data android:name="superdl.module.name"        android:value="Lépésszámláló" />
 *       <meta-data android:name="superdl.module.description" android:value="Megszámolja a napi lépéseidet" />
 *       <meta-data android:name="superdl.module.category"    android:value="egeszseg" />
 *   </activity>
 *
 * A SuperDL ezt megtalálja, ELLENŐRZI AZ ALÁÍRÁST, és felveszi a menüjébe.
 */
object ModuleDiscovery {

    private const val TAG = "SDL_STORE"

    /** Erre a jelzésre keresünk. */
    const val MODULE_ACTION = "com.superdl.launcher.MODULE"

    private const val META_NAME = "superdl.module.name"
    private const val META_DESCRIPTION = "superdl.module.description"
    private const val META_CATEGORY = "superdl.module.category"

    /** A telepített modulok listája. */
    fun findModules(context: Context): List<SuperDlModule> {
        val pm = context.packageManager
        val intent = Intent(MODULE_ACTION)
        val out = mutableListOf<SuperDlModule>()

        val resolved = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            }
        } catch (e: Exception) {
            Log.w(TAG, "modul-kereses hiba: ${e.message}")
            return emptyList()
        }

        resolved.forEach { info ->
            val pkg = info.activityInfo?.packageName ?: return@forEach
            // A SAJÁT alkalmazásunkat kihagyjuk.
            if (pkg == context.packageName) return@forEach

            val meta = info.activityInfo.metaData
            val name = meta?.getString(META_NAME)
                ?: info.loadLabel(pm).toString()
            val description = meta?.getString(META_DESCRIPTION).orEmpty()
            val category = ModuleMenuCategory.fromKey(meta?.getString(META_CATEGORY))
            val versionName = try {
                pm.getPackageInfo(pkg, 0).versionName ?: "?"
            } catch (_: Exception) {
                "?"
            }

            out.add(
                SuperDlModule(
                    packageName = pkg,
                    name = name,
                    description = description,
                    category = category,
                    versionName = versionName,
                    trusted = hasSameSignature(context, pkg)
                )
            )
        }
        Log.i(TAG, "talalt modulok: ${out.size}")
        return out.sortedBy { it.name.lowercase() }
    }

    /**
     * ALÁÍRÁS-ELLENŐRZÉS: ugyanazzal a kulccsal írták-e alá, mint a SuperDL-t?
     *
     * MIÉRT LÉTFONTOSSÁGÚ: enélkül bárki készíthetne egy alkalmazást, ami
     * SuperDL-modulnak adja ki magát, és bekerülne a menübe. A felhasználó —
     * főleg vakon — nem tudná megkülönböztetni a valóditól.
     *
     * Az azonos aláírás azt bizonyítja, hogy a modul UGYANATTÓL a fejlesztőtől
     * származik, mint maga a SuperDL. Ezt a rendszer garantálja, nem mi.
     */
    fun hasSameSignature(context: Context, packageName: String): Boolean = try {
        val own = signaturesOf(context, context.packageName)
        val other = signaturesOf(context, packageName)
        own.isNotEmpty() && other.isNotEmpty() &&
            own.any { a -> other.any { b -> a == b } }
    } catch (e: Exception) {
        Log.w(TAG, "alairas-ellenorzes hiba ($packageName): ${e.message}")
        false
    }

    private fun signaturesOf(context: Context, packageName: String): List<String> {
        val pm = context.packageManager
        return try {
            val signatures: Array<Signature> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val info = pm.getPackageInfo(
                        packageName, PackageManager.GET_SIGNING_CERTIFICATES
                    )
                    val signing = info.signingInfo ?: return emptyList()
                    if (signing.hasMultipleSigners()) {
                        signing.apkContentsSigners
                    } else {
                        signing.signingCertificateHistory
                    }
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
                        ?: return emptyList()
                }
            signatures.map { hashOf(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun hashOf(signature: Signature): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(signature.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
    }

    /** Egy modul elindítása. */
    fun launch(context: Context, module: SuperDlModule): Boolean = try {
        val intent = Intent(MODULE_ACTION).apply {
            `package` = module.packageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "modul inditas hiba (${module.packageName}): ${e.message}")
        false
    }

    /** Egy modul eltávolítása (a rendszer kéri a megerősítést). */
    fun requestUninstall(context: Context, module: SuperDlModule): Boolean = try {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = android.net.Uri.parse("package:${module.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
