chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$pkg = 'com.superdl.launcher'
$apk = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL.apk'
$line = & $adb devices | Select-String -Pattern '\sdevice$' | Select-Object -First 1
if ($line -eq $null) { Write-Output "NINCS keszulek."; exit }
$id = $line.ToString().Split("`t")[0].Trim()

Write-Output "--- TELEPITES (kiadasi valtozat, a debug marad kulon) ---"
& $adb -s $id install -r $apk 2>&1 | Select-Object -Last 2

Write-Output ""
Write-Output "--- UJRAINDITAS ---"
& $adb -s $id reboot
Start-Sleep -Seconds 50
& $adb -s $id wait-for-device
Start-Sleep -Seconds 18

$log = 'C:\Users\msn\Documents\SuperDL-Android\tools\bootnaplo5.txt'
& $adb -s $id logcat -d -b all *:V 2>$null | Out-File -FilePath $log -Encoding UTF8

Write-Output "--- 1. ZAROLVA-E ---"
& $adb -s $id shell "dumpsys trust | grep -i deviceLocked"
Write-Output ""
Write-Output "--- 2. FUT-E A KIADASI VALTOZAT ---"
& $adb -s $id shell "ps -A | grep 'com.superdl.launcher$'"
Write-Output ""
Write-Output "--- 3. OSSZEOMLAS ---"
$c = Get-Content $log | Select-String -Pattern 'am_crash.*superdl'
if ($c -eq $null) { Write-Output "  NINCS" } else { $c | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) } }
Write-Output ""
Write-Output "--- 4. A SEGED ABLAKA A ZARKEPERNYON ---"
Get-Content $log | Select-String -Pattern 'Window\{.*com\.superdl\.launcher\}' | Select-Object -First 4 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- 5. KLIPEK A TARTOS MAPPABAN ---"
& $adb -s $id shell "run-as $pkg ls /data/user_de/0/$pkg/files/zarhang 2>/dev/null | wc -l"
Write-Output "KESZ."
