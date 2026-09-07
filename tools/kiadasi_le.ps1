chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$line = & $adb devices | Select-String -Pattern '\sdevice$' | Select-Object -First 1
if ($line -eq $null) { Write-Output "NINCS keszulek."; exit }
$id = $line.ToString().Split("`t")[0].Trim()
Write-Output "Keszulek: $id"

Write-Output ""
Write-Output "--- 1. MI VAN FENT MOST ---"
& $adb -s $id shell "pm list packages | grep superdl"

Write-Output ""
Write-Output "--- 2. A KIADASI VALTOZAT ELTAVOLITASA (com.superdl.launcher) ---"
& $adb -s $id uninstall com.superdl.launcher 2>&1 | Select-Object -First 3

Write-Output ""
Write-Output "--- 3. MI MARADT ---"
& $adb -s $id shell "pm list packages | grep superdl"

Write-Output ""
Write-Output "--- 4. KI A KEZDOKEPERNYO MOST ---"
& $adb -s $id shell "cmd shortcut get-default-launcher" 2>&1 | Select-Object -First 2
& $adb -s $id shell "dumpsys package preferred-activities 2>/dev/null | grep -A2 -i 'HOME' | head -8" 2>&1 | Select-Object -First 8

Write-Output ""
Write-Output "--- 5. A DEBUG VALTOZAT KISEGITO SZOLGALTATASAI ---"
& $adb -s $id shell "settings get secure enabled_accessibility_services"
Write-Output "KESZ."
