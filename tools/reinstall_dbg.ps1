$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { Write-Output "NINCS ADB"; exit 1 }

Write-Output "=== Eszkozok ==="
& $adb devices -l

Write-Output "=== Ami most fent van ==="
& $adb shell "pm list packages | grep superdl"
& $adb shell "dumpsys package com.superdl.launcher | grep versionName"

Write-Output "=== Eltavolitas ==="
& $adb uninstall com.superdl.launcher

$apk = Get-ChildItem 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug' -Filter *.apk |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { Write-Output 'NINCS DEBUG APK'; exit 1 }
Write-Output ("=== Telepites: " + $apk.Name + "  " + $apk.LastWriteTime + " ===")
& $adb install $apk.FullName

Write-Output "=== Ellenorzes ==="
& $adb shell "dumpsys package com.superdl.launcher | grep versionName"
