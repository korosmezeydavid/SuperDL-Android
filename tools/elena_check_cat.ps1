$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$id = "S62Pro2208000115"
& $adb -s $id logcat -c
& $adb -s $id shell am start -n com.superdl.launcher/.MainActivity
Start-Sleep -Seconds 12
Write-Output "--- ELENA HANGOK ---"
& $adb -s $id shell ls -l /sdcard/Android/data/com.superdl.launcher/files/hangtemak/elena/
Write-Output "--- TEMAK GYOKERE ---"
& $adb -s $id shell ls /sdcard/Android/data/com.superdl.launcher/files/hangtemak/
Write-Output "--- NAPLO (beszedtema + katalogus + osszeomlas) ---"
& $adb -s $id logcat -d -s SDL_VOICETHEME:* SDL_CATALOG:* SDL_APP:* AndroidRuntime:E | Select-Object -Last 30
