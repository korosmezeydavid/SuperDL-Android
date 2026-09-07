$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$id = "3116TF1010002416"
$apk = "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug\SuperDL-1.63.2-debug.apk"
Write-Output "--- TELEPITES ---"
& $adb -s $id install -r $apk
Write-Output "EXIT=$LASTEXITCODE"
& $adb -s $id shell dumpsys package com.superdl.launcher.debug | Select-String "versionName" | Select-Object -First 1
Write-Output ""
Write-Output "--- A ZARHANG KLIPEK AZ APK-BAN ---"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
$k = $zip.Entries | Where-Object { $_.FullName -like "assets/zarhang/*" }
Write-Output "$($k.Count) klip"
$zip.Dispose()
