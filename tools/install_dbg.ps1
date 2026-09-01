$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { Write-Output "NO ADB at $adb"; exit 1 }
$apk = Get-ChildItem 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug' -Filter *.apk |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { Write-Output 'NO DEBUG APK'; exit 1 }
Write-Output ("APK: " + $apk.FullName + "  " + $apk.LastWriteTime)
& $adb devices
& $adb install -r $apk.FullName
