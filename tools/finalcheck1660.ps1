$apk = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL.apk'
$aapt = Get-ChildItem 'C:\Users\msn\AppData\Local\Android\Sdk\build-tools' -Filter aapt2.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1

Write-Output '--- 1. verzio a kiadasi APK-ban ---'
if ($aapt) {
    & $aapt.FullName dump badging $apk 2>$null | Select-String -Pattern "versionName" | Select-Object -First 1
}

Write-Output '--- 2. INDITOPULT ---'
if ($aapt) {
    $tree = & $aapt.FullName dump xmltree --file AndroidManifest.xml $apk 2>$null
    $launcher = $tree | Select-String -Pattern 'android.intent.category.LAUNCHER'
    $kezdo = $tree | Select-String -Pattern 'android.intent.category.HOME'
    Write-Output ("    LAUNCHER kategoria: " + $(if ($launcher) { "MEGVAN" } else { "HIANYZIK" }))
    Write-Output ("    HOME kategoria:     " + $(if ($kezdo) { "MEGVAN" } else { "HIANYZIK" }))
}

Write-Output '--- 3. SZEREPKOROK, SZOLGALTATASOK, ENGEDELYEK ---'
if ($aapt) {
    $tree = & $aapt.FullName dump xmltree --file AndroidManifest.xml $apk 2>$null
    foreach ($p in @('SMS_DELIVER', 'WAP_PUSH_DELIVER', 'RESPOND_VIA_MESSAGE', 'BROADCAST_WAP_PUSH', 'SEND_RESPOND_VIA_MESSAGE', 'ScreenReaderService', 'WAKE_LOCK', 'FOREGROUND_SERVICE_DATA_SYNC', 'FOREGROUND_SERVICE_LOCATION', 'ACCESS_FINE_LOCATION', 'ACCESS_WIFI_STATE', 'READ_PHONE_STATE', 'CatalogDownloadService', 'SmsSendReceiver', 'ScheduledSmsReceiver', 'HomeWatchReceiver', 'HomeWatchService', 'HomeWatchAlertActivity', 'BootReceiver', 'SCHEDULE_EXACT_ALARM')) {
        $van = $tree | Select-String -Pattern $p
        Write-Output ("    " + $p.PadRight(30) + $(if ($van) { "MEGVAN" } else { "HIANYZIK" }))
    }
}

Write-Output '--- 3b. AMI SZANDEKOSAN NINCS BENNE ---'
# AZ OTTHON-FIGYELES NEM GEOKERITES: hataridos ellenorzes, nem folyamatos
# figyeles. Ezert NEM kerunk hatter-helyzet engedelyt. Ha ez egyszer
# beszivarogna a manifestbe, az azt jelentene, hogy valaki folyamatos
# kovetesse alakitotta at a funkciot - ezert ellenorizzuk.
if ($aapt) {
    $tree = & $aapt.FullName dump xmltree --file AndroidManifest.xml $apk 2>$null
    $bg = $tree | Select-String -Pattern 'ACCESS_BACKGROUND_LOCATION'
    Write-Output ("    ACCESS_BACKGROUND_LOCATION: " + $(if ($bg) { "BENNE VAN - EZ BAJ!" } else { "helyesen nincs benne" }))
}

Write-Output '--- 3c. AZ S.O.S. BEALLITO RECEIVER VEDELME ---'
if ($aapt) {
    $tree = & $aapt.FullName dump xmltree --file AndroidManifest.xml $apk 2>$null
    $van = $tree | Select-String -Pattern 'permission.SET_SOS'
    Write-Output ("    SET_SOS engedely a receiveren: " + $(if ($van) { "MEGVAN" } else { "HIANYZIK" }))
}

Write-Output '--- 4. hangok ---'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
$k = $zip.Entries | Where-Object { $_.FullName -like "assets/zarhang/*" }
Write-Output ("zarhang: $($k.Count) klip")
$e = $zip.Entries | Where-Object { $_.FullName -like "assets/hangtemak/elena/*" }
Write-Output ("Elena: $($e.Count) klip")
$zip.Dispose()

Write-Output '--- 5. ALAIRAS ---'
$apksigner = Get-ChildItem 'C:\Users\msn\AppData\Local\Android\Sdk\build-tools' -Filter apksigner.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
if ($apksigner) {
    & $apksigner.FullName verify --print-certs $apk 2>&1 | Select-String -Pattern 'Signer #1 certificate SHA-256|Verified using' | ForEach-Object { Write-Output ("    " + $_.Line.Trim()) }
}

Write-Output '--- 6. MEDIA OSZTALYOK (1.63.8) ---'
& powershell -NoProfile -ExecutionPolicy Bypass -File 'C:\Users\msn\Documents\SuperDL-Android\tools\media_ellenorzes1638.ps1'

Write-Output '--- 7. YOUTUBE-DIAGNOSZTIKA (1.63.9) ---'
& powershell -NoProfile -ExecutionPolicy Bypass -File 'C:\Users\msn\Documents\SuperDL-Android\tools\youtube_diag_ellenorzes1639.ps1'

Write-Output '--- 8. KATALOGUS-JAVITAS (1.63.10) ---'
& powershell -NoProfile -ExecutionPolicy Bypass -File 'C:\Users\msn\Documents\SuperDL-Android\tools\katalogus_ellenorzes16310.ps1'

Write-Output '--- 9. MK-V M0 + M1 (1.64.0) ---'
& powershell -NoProfile -ExecutionPolicy Bypass -File 'C:\Users\msn\Documents\SuperDL-Android\tools\mkv_ellenorzes1640.ps1'

Write-Output '--- 10. MK-V M2 + M3 (1.65.0) ---'
& powershell -NoProfile -ExecutionPolicy Bypass -File 'C:\Users\msn\Documents\SuperDL-Android\tools\mkv_ellenorzes1650.ps1'

Write-Output '--- 11. MK-V M4 OTTHON-FIGYELES (ez a kiadas lenyege) ---'
& powershell -NoProfile -ExecutionPolicy Bypass -File 'C:\Users\msn\Documents\SuperDL-Android\tools\otthon_ellenorzes1660.ps1'
Write-Output 'KESZ.'
