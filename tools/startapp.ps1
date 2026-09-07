$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
Write-Output "=== Felhasznalok ==="
& $adb shell "pm list users"
Write-Output "=== Telepitve van-e (aktualis felhasznalo) ==="
& $adb shell "cmd package list packages --user current com.superdl.launcher"
Write-Output "=== Verzio ==="
& $adb shell "dumpsys package com.superdl.launcher | grep versionName"
Write-Output "=== Inditas: MainActivity ==="
& $adb shell "am start -n com.superdl.launcher/.MainActivity"
Start-Sleep -Seconds 3
Write-Output "=== Mi van elol ==="
& $adb shell "dumpsys activity activities | grep -m 3 mResumedActivity"
