chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$apk = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL.apk'

Write-Output "--- 1. MI VAN MOST FENT ---"
& $adb shell "pm list packages | grep superdl"

Write-Output ""
Write-Output "--- 2. A DEBUG PELDANY LEVETELE ---"
& $adb uninstall com.superdl.launcher.debug 2>&1 | Select-Object -Last 1

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
