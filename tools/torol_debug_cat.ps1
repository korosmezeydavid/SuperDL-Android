$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$id = "S62Pro2208000115"

Write-Output "--- ELOTTE: verziok ---"
& $adb -s $id shell dumpsys package com.superdl.launcher | Select-String -Pattern "versionName" | Select-Object -First 1
& $adb -s $id shell dumpsys package com.superdl.launcher.debug | Select-String -Pattern "versionName" | Select-Object -First 1

Write-Output "--- ELOTTE: szerepkorok es kisegito szolgaltatasok ---"
& $adb -s $id shell cmd role get-role-holders android.app.role.HOME
& $adb -s $id shell settings get secure enabled_accessibility_services
& $adb -s $id shell settings get secure default_input_method

Write-Output "--- MI VAN A FEJLESZTOI VALTOZAT SAJAT MAPPAJABAN (mielott torlodik) ---"
& $adb -s $id shell ls /sdcard/Android/data/com.superdl.launcher.debug/files/ 2>$null

Write-Output "--- TORLES: com.superdl.launcher.debug ---"
& $adb -s $id uninstall com.superdl.launcher.debug
Write-Output "EXIT=$LASTEXITCODE"

Write-Output "--- UTANA: mi maradt ---"
& $adb -s $id shell pm list packages | Select-String -Pattern "com.superdl.launcher"
& $adb -s $id shell dumpsys package com.superdl.launcher | Select-String -Pattern "versionName" | Select-Object -First 1
& $adb -s $id shell cmd role get-role-holders android.app.role.HOME
& $adb -s $id shell settings get secure enabled_accessibility_services
& $adb -s $id shell settings get secure default_input_method
