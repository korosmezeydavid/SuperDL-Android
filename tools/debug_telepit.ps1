chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'

Write-Output "--- 1. CSATLAKOZTATVA VAN-E ---"
& $adb devices
$line = & $adb devices | Select-String -Pattern '\sdevice$' | Select-Object -First 1
if ($line -eq $null) {
    Write-Output ""
    Write-Output ">>> NINCS csatlakoztatott keszulek. Dugd be a telefont USB-n."
    exit 1
}
$id = $line.ToString().Split("`t")[0].Trim()
Write-Output ("Keszulek: " + $id)

Write-Output ""
Write-Output "--- 2. A LEGFRISSEBB DEBUG APK ---"
$apk = (Get-ChildItem -Path 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug' -Filter *.apk |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1)
Write-Output ("  " + $apk.Name + "   keszult: " + $apk.LastWriteTime.ToString('HH:mm:ss') + "   " + [math]::Round($apk.Length/1MB,1) + " MB")

Write-Output ""
Write-Output "--- 3. TELEPITES (a beallitasok es a kisegito szolgaltatasok megmaradnak) ---"
& $adb -s $id install -r $apk.FullName 2>&1 | Select-Object -Last 3

Write-Output ""
Write-Output "--- 4. MI VAN FENT ---"
& $adb -s $id shell "pm list packages | grep superdl"
& $adb -s $id shell "dumpsys package com.superdl.launcher.debug | grep versionName" 2>&1 | Select-Object -First 1

Write-Output ""
Write-Output "--- 5. KISEGITO SZOLGALTATASOK (megmaradtak-e) ---"
& $adb -s $id shell "settings get secure enabled_accessibility_services"
Write-Output "KESZ."
