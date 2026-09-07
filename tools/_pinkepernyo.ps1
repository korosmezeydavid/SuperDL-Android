$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$id = "3116TF1010002416"

Write-Output "--- 1. FELEBRESZTES ES A ZARKEPERNYO FELHUZASA (igy jon elo a PIN mezo) ---"
& $adb -s $id shell input keyevent 224
Start-Sleep -Seconds 2
& $adb -s $id logcat -c
& $adb -s $id shell input swipe 540 1600 540 400 300
Start-Sleep -Seconds 4

Write-Output ""
Write-Output "--- 2. MI VAN A KEPERNYON ---"
& $adb -s $id shell "dumpsys window" 2>&1 | Select-String -Pattern "mCurrentFocus" | Select-Object -First 2

Write-Output ""
Write-Output "--- 3. MIT LAT MOST A PIN SEGED ---"
& $adb -s $id logcat -d -s SDL_PINASSIST:* 2>&1 | Select-Object -Last 20

Write-Output ""
Write-Output "--- 4. KEPERNYOKEP A PIN KEPERNYORODL ---"
& $adb -s $id shell screencap -p /sdcard/_pin.png
& $adb -s $id pull /sdcard/_pin.png "C:\Users\msn\Documents\SuperDL-Android\tools\pinkepernyo.png"
& $adb -s $id shell rm /sdcard/_pin.png
Get-Item "C:\Users\msn\Documents\SuperDL-Android\tools\pinkepernyo.png" | Select-Object Name, Length
