$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
Start-Transcript -Path 'C:\Users\msn\Documents\SuperDL-Android\tools\_pin.log' -Force | Out-Null

Write-Output "--- 1. KESZULEK ---"
& $adb devices
$id = ((& $adb devices) | Select-String -Pattern "\tdevice$" | ForEach-Object { ($_ -split "`t")[0] } | Select-Object -First 1)
if (-not $id) { Write-Output "NINCS KESZULEK"; Stop-Transcript | Out-Null; exit 1 }

Write-Output ""
Write-Output "--- 2. MELYIK SUPERDL, MILYEN VERZIO ---"
& $adb -s $id shell pm list packages | Select-String "com.superdl.launcher"
& $adb -s $id shell dumpsys package com.superdl.launcher.debug | Select-String "versionName" | Select-Object -First 1

Write-Output ""
Write-Output "--- 3. ENGEDELYEZETT KISEGITO SZOLGALTATASOK ---"
& $adb -s $id shell settings get secure enabled_accessibility_services
Write-Output "--- kisegito fokapcsolo ---"
& $adb -s $id shell settings get secure accessibility_enabled

Write-Output ""
Write-Output "--- 4. VAN-E EGYALTALAN KEPERNYOZAR (ez a kulcskerdes) ---"
& $adb -s $id shell "dumpsys device_policy | grep -i 'password quality'" 2>&1 | Select-Object -First 2
& $adb -s $id shell "dumpsys trust" 2>&1 | Select-String -Pattern "deviceLocked|secure" | Select-Object -First 3

Write-Output ""
Write-Output "--- 5. A PIN SEGED NAPLOJA A BEKAPCSOLAS OTA ---"
& $adb -s $id logcat -d -s SDL_PINASSIST:* 2>&1 | Select-Object -Last 30

Write-Output ""
Write-Output "--- 6. UPTIME (mikor indult ujra) ---"
& $adb -s $id shell uptime
Stop-Transcript | Out-Null
