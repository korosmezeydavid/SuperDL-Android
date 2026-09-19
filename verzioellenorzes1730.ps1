$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$out = & $adb shell "dumpsys package com.superdl.launcher.debug"
$out | Select-String -Pattern "versionName", "versionCode" | Select-Object -First 4
