$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$apk = "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug\SuperDL-1.62.1-debug.apk"
Write-Output "--- APK ---"
Get-ChildItem $apk | Select-Object Name, Length | Format-Table -AutoSize
Write-Output "--- KESZULEK ---"
& $adb devices
Write-Output "--- FRISSITES (adatok megmaradnak) ---"
& $adb install -r $apk
Write-Output "EXIT=$LASTEXITCODE"
Write-Output "--- ELLENORZES ---"
& $adb shell dumpsys package com.superdl.launcher.debug | Select-String -Pattern "versionName"
& $adb shell settings get secure enabled_accessibility_services
& $adb shell cmd role get-role-holders android.app.role.HOME
