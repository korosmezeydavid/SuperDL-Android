param([switch]$TorolhetemADebugot)

# =====================================================================
#  VERZIOCSERE A TELEFONON
#
#  MIERT NEM TOROL MAGATOL.
#
#  2026-09-09: ez a szkript kerdes nelkul letorolte az Ulefone-rol a
#  fejlesztoi peldanyt, es Alph MINDEN beallitasa elveszett rajta.
#  A ket csomag (com.superdl.launcher es .debug) az Android szemeben ket
#  kulon alkalmazas: a torles a beallitasokat, kedvenceket, hangtemakat
#  is viszi, es NEM hozhato vissza.
#
#  A hiba nem a torles volt, hanem hogy megkerdezes nelkul tortent.
#  Ezert innentol a szkript alapbol CSAK TELEPIT, es ha talal masik
#  peldanyt, megall es szol. A torles kulon kapcsolot igenyel:
#
#      .\csere1635.ps1 -TorolhetemADebugot
#
#  Ezt a kapcsolot CSAK akkor szabad hasznalni, ha a felhasznalo
#  kifejezetten azt mondta, hogy a fejlesztoi peldany mehet.
# =====================================================================

chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$apk = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL.apk'

Write-Output "--- 1. MI VAN MOST FENT ---"
$fent = & $adb shell "pm list packages | grep superdl"
$fent
$vanDebug = ($fent -join "`n") -match 'com\.superdl\.launcher\.debug'

Write-Output ""
if ($vanDebug -and -not $TorolhetemADebugot) {
    Write-Output "--- MEGALLOK ---"
    Write-Output "Ezen a telefonon a FEJLESZTOI peldany van fent."
    Write-Output "A torlese MINDEN beallitast elvinne, es nem hozhato vissza."
    Write-Output ""
    Write-Output "Kerdezd meg a felhasznalot. Ha azt mondja, mehet:"
    Write-Output "    .\csere1635.ps1 -TorolhetemADebugot"
    Write-Output ""
    Write-Output "Ha marad a fejlesztoi, akkor a debug_telepit.ps1 kell,"
    Write-Output "nem ez a szkript."
    exit 2
}

if ($vanDebug) {
    Write-Output "--- 2. A DEBUG PELDANY LEVETELE (kifejezett engedellyel) ---"
    & $adb uninstall com.superdl.launcher.debug 2>&1 | Select-Object -Last 1
} else {
    Write-Output "--- 2. Nincs fejlesztoi peldany, nincs mit levenni ---"
}

Write-Output ""
Write-Output "--- 3. A KIADASI VERZIO FRISSITESE (1.63.5) ---"
& $adb install -r $apk 2>&1 | Select-Object -Last 2

Write-Output ""
Write-Output "--- 4. MI VAN FENT UTANA ---"
& $adb shell "pm list packages | grep superdl"
& $adb shell "dumpsys package com.superdl.launcher | grep versionName" 2>&1 | Select-Object -First 1

Write-Output ""
Write-Output "--- 5. KISEGITO SZOLGALTATASOK ---"
& $adb shell "settings get secure enabled_accessibility_services"

Write-Output ""
Write-Output "--- 6. MEGJELENIK-E AZ ALKALMAZASLISTABAN ---"
& $adb shell "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LAUNCHER" 2>&1 |
    Select-String -Pattern 'packageName=com.superdl' | Select-Object -First 3
Write-Output "KESZ."
