Set-Location "C:\Users\msn\Documents\SuperDL-Android"
git add -A
git commit -F "C:\Users\msn\Documents\SuperDL-Android\commitmsg1740.txt" --quiet
git log --oneline -1
$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug\SuperDL-1.74.0-debug.apk"
$out = & $adb shell "dumpsys package com.superdl.launcher.debug"
$out | Select-String -Pattern "versionName" | Select-Object -First 1
