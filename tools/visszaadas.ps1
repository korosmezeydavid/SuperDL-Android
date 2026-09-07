$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'

Write-Output "=== 1. TalkBack kikapcsolasa ==="
& $adb shell "settings put secure enabled_accessibility_services ''"
& $adb shell "settings put secure accessibility_enabled 0"
& $adb shell "settings put secure touch_exploration_enabled 0"

Write-Output "=== 2. SuperDL eltavolitasa ==="
& $adb uninstall com.superdl.launcher

Write-Output "=== 3. ELLENORZES ==="
Write-Output "-- kisegito szolgaltatasok (uresnek kell lennie) --"
& $adb shell "settings get secure enabled_accessibility_services"
Write-Output "-- kisegito fokapcsolo (0) --"
& $adb shell "settings get secure accessibility_enabled"
Write-Output "-- talkback erintes-felfedezes (0) --"
& $adb shell "settings get secure touch_exploration_enabled"
Write-Output "-- superdl csomag (nem szabad talalatnak lennie) --"
& $adb shell "pm list packages com.superdl.launcher"
Write-Output "-- kezdokepernyo --"
& $adb shell "cmd shortcut get-default-launcher"
Write-Output "-- telefon app --"
& $adb shell "cmd telecom get-default-dialer"
Write-Output "-- SMS szerepkor --"
& $adb shell "cmd role get-role-holders android.app.role.SMS"
