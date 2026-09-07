$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$id = "3116TF1010002416"

Write-Output "--- KEPERNYO MERETE ---"
& $adb -s $id shell wm size

Write-Output ""
Write-Output "--- VISSZA (az ertesitesi sav bezarasa), MAJD A ZAR FELHUZASA ---"
& $adb -s $id shell input keyevent 4
Start-Sleep -Seconds 1
& $adb -s $id logcat -c
# Az ertesitesi sav helyett a keperyo KOZEPEROL huzunk felfele.
& $adb -s $id shell input swipe 540 1400 540 300 400
Start-Sleep -Seconds 4

Write-Output ""
Write-Output "--- MI VAN A KEPERNYON ---"
& $adb -s $id shell "dumpsys window" 2>&1 | Select-String -Pattern "mCurrentFocus" | Select-Object -First 2

Write-Output ""
Write-Output "--- A PIN SEGED NAPLOJA ---"
& $adb -s $id logcat -d -s SDL_PINASSIST:* 2>&1 | Select-Object -Last 20
