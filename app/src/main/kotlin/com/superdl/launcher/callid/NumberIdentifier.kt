package com.superdl.launcher.callid

import android.content.Context
import android.util.Log
import com.superdl.launcher.contacts.ContactHelper

/**
 * „SZÁM AZONOSÍTÁSA" — az Android-oldali ragasztó.
 *
 * CSAK LEGÁLIS, CSAK HELYI FORRÁS: a telefon névjegyei, a csomagolt
 * OpenStreetMap-cégindex és a libphonenumber számterve. Hálózatot ez az
 * osztály NEM használ — a hívó száma nem hagyja el a telefont. A tudakozós
 * keresés külön, a felhasználó kérésére, kézzel történik (lásd MainActivity).
 *
 * Az [identify] blokkoló (névjegy-lekérdezés, első híváskor az index
 * betöltése): háttérszálon hívd.
 */
object NumberIdentifier {

    private const val TAG = "NumberIdentifier"
    const val INDEX_ASSET = "telefon_index_hu.tsv"

    @Volatile
    private var cachedIndex: PhoneIndex? = null

    fun identify(context: Context, rawNumber: String): IdentifyReport {
        val info = NumberDescriber.describe(rawNumber)
        if (info.kind == NumberDescriber.Kind.HIDDEN) {
            return IdentifyReport(info, null, emptyList())
        }
        val contact = findContact(context, rawNumber, info.e164)
        if (contact != null) return IdentifyReport(info, contact, emptyList())
        val candidates = try {
            index(context).lookup(info.e164)
        } catch (t: Throwable) {
            Log.w(TAG, "Index kereses sikertelen: ${t.javaClass.simpleName}: ${t.message}")
            emptyList()
        }
        return IdentifyReport(info, null, candidates)
    }

    /**
     * 1. RÉTEG: a névjegyek. A rendszer PhoneLookup-ja a más alakban mentett
     * számot is megtalálja („06 30…" vs „+3630…"); ha a nyers alakkal nem megy,
     * az E.164 alakkal is megpróbáljuk. Engedély nélkül null — nem hiba.
     */
    private fun findContact(context: Context, raw: String, e164: String?): String? = try {
        ContactHelper.findNameByPhone(context, raw)
            ?: e164?.takeIf { it != raw.trim() }?.let { ContactHelper.findNameByPhone(context, it) }
    } catch (t: Throwable) {
        Log.w(TAG, "Nevjegy kereses sikertelen: ${t.javaClass.simpleName}")
        null
    }

    /**
     * 2. RÉTEG: az OSM-index, LUSTÁN és EGYSZER. Az első azonosításkor töltjük
     * be, utána a memóriában marad. Ha az asset hiányzik (fejlesztői build,
     * amibe még nem került bele), üres indexszel megyünk tovább — a leírás
     * akkor is elhangzik.
     */
    fun index(context: Context): PhoneIndex {
        cachedIndex?.let { return it }
        synchronized(this) {
            cachedIndex?.let { return it }
            val loaded = try {
                context.applicationContext.assets.open(INDEX_ASSET).bufferedReader(Charsets.UTF_8).use {
                    PhoneIndex.parse(it.lineSequence())
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Telefonindex nem toltheto: ${t.javaClass.simpleName}: ${t.message}")
                PhoneIndex.EMPTY
            }
            cachedIndex = loaded
            return loaded
        }
    }
}
