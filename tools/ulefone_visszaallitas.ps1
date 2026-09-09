chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$p = 'com.superdl.launcher.debug'

function Probald($cimke, $parancs) {
    $ki = & $adb shell $parancs 2>&1
    $szoveg = ($ki -join ' ').Trim()
    if ($szoveg -match 'Error|Exception|Failure|not found|Unknown|denied') {
        Write-Output ("  [NEM MENT] " + $cimke + "  ->  " + $szoveg)
    } else {
        Write-Output ("  [OK]       " + $cimke)
    }
}

Write-Output "=== 1. FUTASIDEJU ENGEDELYEK ==="
$engedelyek = @(
    @('Telefonalas',      'android.permission.CALL_PHONE'),
    @('Nevjegyek olvasas','android.permission.READ_CONTACTS'),
    @('Nevjegyek iras',   'android.permission.WRITE_CONTACTS'),
    @('SMS kuldes',       'android.permission.SEND_SMS'),
    @('SMS olvasas',      'android.permission.READ_SMS'),
    @('SMS fogadas',      'android.permission.RECEIVE_SMS'),
    @('Mikrofon',         'android.permission.RECORD_AUDIO'),
    @('Ertesitesek',      'android.permission.POST_NOTIFICATIONS'),
    @('Helymeghatarozas', 'android.permission.ACCESS_FINE_LOCATION'),
    @('Helymeghatarozas durva','android.permission.ACCESS_COARSE_LOCATION'),
    @('Kamera',           'android.permission.CAMERA'),
    @('Naptar olvasas',   'android.permission.READ_CALENDAR'),
    @('Naptar iras',      'android.permission.WRITE_CALENDAR'),
    @('Zene es hangfajlok','android.permission.READ_MEDIA_AUDIO'),
    @('Hivasnaplo',       'android.permission.READ_CALL_LOG'),
    @('Telefon allapot',  'android.permission.READ_PHONE_STATE'),
    @('Bluetooth',        'android.permission.BLUETOOTH_CONNECT')
)
foreach ($e in $engedelyek) { Probald $e[0] "pm grant $p $($e[1])" }

Write-Output ""
Write-Output "=== 2. TELJES FAJLHOZZAFERES ==="
Probald 'Osszes fajl kezelese' "appops set $p MANAGE_EXTERNAL_STORAGE allow"

Write-Output ""
Write-Output "=== 3. PONTOS EBRESZTO ==="
Probald 'Pontos ebreszto' "appops set $p SCHEDULE_EXACT_ALARM allow"

Write-Output ""
Write-Output "=== 4. KEPERNYO FOLE RAJZOLAS (sotet mod) ==="
Probald 'Kepernyo fole rajzolas' "appops set $p SYSTEM_ALERT_WINDOW allow"

Write-Output ""
Write-Output "=== 5. KORLATLAN HATTERFUTAS ==="
Probald 'Akku-optimalizalas alol' "dumpsys deviceidle whitelist +$p"

Write-Output ""
Write-Output "=== 6. SZEREPKOROK ==="
Probald 'Kezdokepernyo' "cmd package set-home-activity $p/com.superdl.launcher.MainActivity"
Probald 'Alapertelmezett SMS' "cmd role add-role-holder android.app.role.SMS $p"
Probald 'Alapertelmezett telefon' "cmd role add-role-holder android.app.role.DIALER $p"

Write-Output ""
Write-Output "=== 7. ERTESITESEK OLVASASA ==="
$nl = "$p/com.superdl.launcher.notifications.NotificationReaderService"
Probald 'Ertesites-figyelo' "settings put secure enabled_notification_listeners '$nl'"

Write-Output ""
Write-Output "=== ALLAPOT ==="
Write-Output "--- kezdokepernyo ---"
& $adb shell "cmd shortcut get-default-launcher" 2>&1 | Select-Object -First 2
Write-Output "--- szerepkorok ---"
& $adb shell "cmd role get-role-holders android.app.role.SMS; cmd role get-role-holders android.app.role.DIALER"
Write-Output "--- kisegito szolgaltatasok ---"
& $adb shell "settings get secure enabled_accessibility_services"
Write-Output "--- akku ---"
& $adb shell "dumpsys deviceidle whitelist | grep superdl"
Write-Output "KESZ."
