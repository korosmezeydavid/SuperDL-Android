chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$apk = (Get-ChildItem -Path 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug' -Filter *.apk |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
Write-Output ("APK: " + $apk)
$line = & $adb devices | Select-String -Pattern '\sdevice$' | Select-Object -First 1
if ($line -eq $null) { Write-Output "NINCS keszulek."; exit }
$id = $line.ToString().Split("`t")[0].Trim()
Write-Output "Keszulek: $id"

Write-Output "--- 1. TELEPITES ---"
& $adb -s $id install -r $apk 2>&1 | Select-Object -Last 3

Write-Output ""
Write-Output "--- 2. UJRAINDITAS ---"
& $adb -s $id reboot
Start-Sleep -Seconds 55
& $adb -s $id wait-for-device
Start-Sleep -Seconds 25

Write-Output ""
Write-Output "--- 3. ZAROLVA-E (feloldas ELOTT kell legyen) ---"
& $adb -s $id shell "dumpsys trust | grep -i deviceLocked"

Write-Output ""
Write-Output "--- 4. FUT-E A SUPERDL FOLYAMATA ---"
$p = & $adb -s $id shell "ps -A | grep superdl"
if ([string]::IsNullOrWhiteSpace($p)) { Write-Output "  >>> NEM FUT <<<" } else { Write-Output $p }

Write-Output ""
Write-Output "--- 5. OSSZEOMLAS VOLT-E ---"
& $adb -s $id shell "logcat -d | grep -c 'am_crash.*superdl'" 2>&1 | Select-Object -First 2

Write-Output ""
Write-Output "--- 6. A PIN SEGED NAPLOJA ---"
& $adb -s $id shell "logcat -d | grep 'SDL_PINASSIST\|SDL_SCREENREADER'" 2>&1 | Select-Object -First 30

Write-Output ""
Write-Output "--- 7. BEEPITETT KLIPEK KICSOMAGOLVA ---"
& $adb -s $id shell "ls /data/user_de/0/com.superdl.launcher.debug/cache/zarhang 2>/dev/null | wc -l"

Write-Output ""
Write-Output "--- 8. NAPLO MENTESE ---"
& $adb -s $id logcat -d -b all *:V 2>$null | Out-File -FilePath 'C:\Users\msn\Documents\SuperDL-Android\tools\bootnaplo2.txt' -Encoding UTF8
Write-Output "KESZ."
