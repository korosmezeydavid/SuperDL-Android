$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb shell "dumpsys package com.superdl.launcher | findstr versionName"
Write-Output "--- android verzio / gyarto ---"
& $adb shell "getprop ro.build.version.release; getprop ro.product.manufacturer; getprop ro.product.model"
Write-Output "--- alapertelmezett szerepkorok ---"
& $adb shell "cmd role get-role-holders android.app.role.SMS"
& $adb shell "cmd role get-role-holders android.app.role.DIALER"
& $adb shell "cmd role get-role-holders android.app.role.HOME"
