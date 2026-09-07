$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$id = "3116TF1010002416"

Write-Output "--- KEPERNYOKEP ---"
& $adb -s $id shell screencap -p /sdcard/_kep.png
& $adb -s $id pull /sdcard/_kep.png "C:\Users\msn\Documents\SuperDL-Android\tools\kepernyo.png"
& $adb -s $id shell rm /sdcard/_kep.png

Write-Output ""
Write-Output "--- VAN-E VALODI KEPERNYOZAR (a HELYES kerdes) ---"
Write-Output "isDeviceSecure a KeyguardManager szerint:"
& $adb -s $id shell "dumpsys trust" 2>&1 | Select-String -Pattern "Trust|secure|StrongAuth|deviceLocked" | Select-Object -First 8

Write-Output ""
Write-Output "--- A ZAR TIPUSA ---"
& $adb -s $id shell "settings get secure lockscreen.password_type" 2>&1 | Select-Object -First 1
& $adb -s $id shell "dumpsys lock_settings" 2>&1 | Select-String -Pattern "credential|quality|type" | Select-Object -First 8

Write-Output ""
Write-Output "--- MI VAN MOST A KEPERNYON ---"
& $adb -s $id shell "dumpsys window" 2>&1 | Select-String -Pattern "mCurrentFocus|mShowingLockscreen|mDreamingLockscreen|isStatusBarKeyguard" | Select-Object -First 6
