package com.superdl.launcher.share

/**
 * IDEIGLENES FELHŐ-TÁRHELYEK — a „feltöltöm, és küldöm a linket" út.
 *
 * MIÉRT TÖBB SZOLGÁLTATÓ: ezek a szolgáltatások jönnek-mennek. A transfer.sh
 * évekig az etalon volt, aztán eltűnt. Egyetlen tárhelyre építeni annyit
 * jelentene, hogy a funkció egy idegen döntéstől él vagy hal. Több tárhely
 * mellett a felhasználó választ — „kinek mi válik be" alapon.
 *
 * MIÉRT MARAD A CÍM A KÓDBAN: kísértés volna a listát letölthető fájlba
 * tenni, hogy egy kiadás nélkül is cserélhető legyen. De egy TÁVOLRÓL
 * ÁLLÍTHATÓ feltöltési cím azt jelentené, hogy a felhasználó fájljai bárhova
 * átirányíthatók. A sorrend és a tapasztalat jöhet adatból, a CÍM nem.
 */
enum class UploadStyle {
    /** multipart/form-data, egy mezővel (a legtöbb tárhely így kéri). */
    MULTIPART,

    /** PUT, a fájl a törzsben, a név az útvonalban (bashupload). */
    RAW_PUT,

    /** POST, a fájl a törzsben, a név az útvonalban (filebin). */
    RAW_POST
}

/**
 * Egy tárhely leírója.
 *
 * A [note] az, amit a felhasználó HALL a választásnál. Nem reklám: a méret,
 * az élettartam és az esetleges csapda.
 */
data class CloudTarget(
    val id: String,
    val name: String,
    /** Felolvasható név — a pontokat és a rövidítéseket kiírva. */
    val spokenName: String,
    val maxBytes: Long,
    val lifetimeHours: Int,
    /** Igaz, ha a fájl az ELSŐ letöltés után eltűnik. */
    val oneTimeDownload: Boolean,
    val style: UploadStyle,
    val fieldName: String,
    val note: String
)

private const val MB = 1024L * 1024L
private const val GB = 1024L * 1024L * 1024L

object CloudTargets {

    /**
     * A SORREND SZÁNDÉKOS: elöl az, ami a legtöbb embernek a legtöbbször jó.
     * A csapdásak (egyszeri letöltés) leghátul — de bent vannak, mert van,
     * akinek pont ez kell: „menjen át egyszer, aztán tűnjön el".
     */
    val ALL: List<CloudTarget> = listOf(
        CloudTarget(
            id = "zerox",
            name = "0x0.st",
            spokenName = "nulla iksz nulla pont es té",
            maxBytes = 512 * MB,
            lifetimeHours = 30 * 24,
            oneTimeDownload = false,
            style = UploadStyle.MULTIPART,
            fieldName = "file",
            note = "Legfeljebb ötszáztizenkét megabájt, legalább harminc napig él, " +
                "és utólag törölni is tudom róla a fájlt."
        ),
        CloudTarget(
            id = "tempsh",
            name = "temp.sh",
            spokenName = "temp pont es há",
            maxBytes = 4 * GB,
            lifetimeHours = 72,
            oneTimeDownload = false,
            style = UploadStyle.MULTIPART,
            fieldName = "file",
            note = "Négy gigabájtig, három napig él. Nagy fájlhoz ez a legjobb."
        ),
        CloudTarget(
            id = "filebin",
            name = "filebin.net",
            spokenName = "filebin pont net",
            maxBytes = 2 * GB,
            lifetimeHours = 7 * 24,
            oneTimeDownload = false,
            style = UploadStyle.RAW_POST,
            fieldName = "",
            note = "Egy hétig él, és utólag törölhető. Akkor jó, ha a másik fél " +
                "nem biztos, hogy hamar hozzájut."
        ),
        CloudTarget(
            id = "tmpfiles",
            name = "tmpfiles.org",
            spokenName = "tmpfiles pont org",
            maxBytes = 100 * MB,
            lifetimeHours = 48,
            oneTimeDownload = false,
            style = UploadStyle.MULTIPART,
            fieldName = "file",
            note = "Száz megabájtig, két napig. Akkor jó, ha csak most kell, " +
                "és ne maradjon utána semmi."
        ),
        CloudTarget(
            id = "bashupload",
            name = "bashupload.com",
            spokenName = "bashupload pont com",
            maxBytes = 20 * GB,
            lifetimeHours = 72,
            oneTimeDownload = true,
            style = UploadStyle.RAW_PUT,
            fieldName = "",
            note = "Nagyon nagy fájl is elfér, de FIGYELEM: a fájl az első " +
                "letöltés után eltűnik. Egy embernek jó, többnek nem."
        ),
        CloudTarget(
            id = "fileio",
            name = "file.io",
            spokenName = "file pont i o",
            maxBytes = 100 * MB,
            lifetimeHours = 14 * 24,
            oneTimeDownload = true,
            style = UploadStyle.MULTIPART,
            fieldName = "file",
            note = "Száz megabájtig. FIGYELEM: a fájl az első letöltés után " +
                "eltűnik. Egy embernek jó, többnek nem."
        )
    )

    fun byId(id: String): CloudTarget? = ALL.firstOrNull { it.id == id }

    /**
     * Amibe a fájl belefér.
     *
     * MIÉRT SZŰRÜNK ELŐRE: vakon a legrosszabb élmény az, ha a felhasználó
     * végigvár egy feltöltést, és a végén derül ki, hogy a fájl túl nagy volt.
     */
    fun eligible(sizeBytes: Long): List<CloudTarget> =
        ALL.filter { sizeBytes <= it.maxBytes }

    /** „két nap", „hat óra" — felolvasásra. */
    fun lifetimeText(hours: Int): String = when {
        hours >= 48 -> "${hours / 24} napig"
        hours >= 24 -> "egy napig"
        hours == 1 -> "egy óráig"
        else -> "$hours óráig"
    }

    /** Bájt emberi alakban, magyar tizedesvesszővel. */
    fun sizeText(bytes: Long): String {
        val n = if (bytes < 0) 0 else bytes
        return when {
            n >= GB -> String.format(java.util.Locale("hu", "HU"), "%.1f gigabájt", n.toDouble() / GB)
            n >= MB -> String.format(java.util.Locale("hu", "HU"), "%.1f megabájt", n.toDouble() / MB)
            n >= 1024 -> "${n / 1024} kilobájt"
            else -> "$n bájt"
        }
    }
}
