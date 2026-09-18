Set-Location "C:\Users\msn\Documents\SuperDL-Android"
git add -A
git commit -F "C:\Users\msn\Documents\SuperDL-Android\commitmsg1730.txt" --quiet
git log --oneline -1
"--- telepites ---"
$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug\SuperDL-1.73.0-debug.apk"
