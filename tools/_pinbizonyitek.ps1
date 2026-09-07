$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$id = "3116TF1010002416"

& $adb -s $id shell am start -n com.superdl.launcher.debug/com.superdl.launcher.MainActivity | Out-Null
Start-Sleep -Seconds 5
& $adb -s $id logcat -c

Write-Output "--- KEPERNYO LEZARASA ---"
& $adb -s $id shell input keyevent 26
Start-Sleep -Seconds 3
Write-Output "--- FELEBRESZTES (itt jonne a PIN keperyo, ha volna kod) ---"
& $adb -s $id shell input keyevent 26
Start-Sleep -Seconds 5

Write-Output ""
Write-Output "--- MIT LAT A PIN SEGED ---"
& $adb -s $id logcat -d -s SDL_PINASSIST:* 2>&1 | Select-Object -Last 15
