$ErrorActionPreference = "Continue"
$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$apk = "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug\SuperDL-1.62.0-debug.apk"
& $adb devices
Write-Output "--- INSTALL ---"
& $adb install -r $apk
Write-Output "EXIT=$LASTEXITCODE"
Write-Output "--- START ---"
& $adb shell am start -n com.superdl.launcher/.MainActivity
