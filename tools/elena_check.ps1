$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
& $adb shell monkey -p com.superdl.launcher.debug -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 8
Write-Output "--- ELENA HANGOK A TELEFONON ---"
& $adb shell ls -l /sdcard/Android/data/com.superdl.launcher.debug/files/hangtemak/elena/
Write-Output "--- NAPLO ---"
& $adb logcat -d -s SDL_VOICETHEME:* SDL_APP:* | Select-Object -Last 25
