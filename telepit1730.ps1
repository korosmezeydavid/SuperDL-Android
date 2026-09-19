$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug\SuperDL-1.73.0-debug.apk"
& $adb devices
& $adb install -r $apk
"---- felteleptett verzio ----"
& $adb shell "dumpsys package com.superdl.launcher.debug | findstr versionName"
