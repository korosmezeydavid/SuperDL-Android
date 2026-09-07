$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$id = "S62Pro2208000115"
$apk = "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL-1.63.1.apk"
Write-Output "--- TELEPITES (adatok megmaradnak) ---"
& $adb -s $id install -r $apk
Write-Output "EXIT=$LASTEXITCODE"
Write-Output "--- VERZIO ---"
& $adb -s $id shell dumpsys package com.superdl.launcher | Select-String -Pattern "versionName" | Select-Object -First 1
