$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
Write-Output "--- KISEGITO SZOLGALTATASOK ---"
& $adb shell settings get secure enabled_accessibility_services
Write-Output "--- ERTESITES-HOZZAFERES ---"
& $adb shell settings get secure enabled_notification_listeners
Write-Output "--- BILLENTYUZET ---"
& $adb shell settings get secure default_input_method
Write-Output "--- ALAPERTELMEZETT SMS ---"
& $adb shell settings get secure sms_default_application
Write-Output "--- KEZDOKEPERNYO (roles) ---"
& $adb shell cmd role get-role-holders android.app.role.HOME
Write-Output "--- TELEFON (roles) ---"
& $adb shell cmd role get-role-holders android.app.role.DIALER
Write-Output "--- VERZIOK ---"
& $adb shell dumpsys package com.superdl.launcher | Select-String -Pattern "versionName"
& $adb shell dumpsys package com.superdl.launcher.debug | Select-String -Pattern "versionName"
