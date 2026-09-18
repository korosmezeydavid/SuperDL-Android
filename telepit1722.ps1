$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug\SuperDL-1.72.2-debug.apk"
"APK letezik: " + (Test-Path $apk)
& $adb devices
& $adb install -r $apk
