$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$id = "3116TF1010002416"

Write-Output "--- BEKOTOTT-E A KET SZOLGALTATAS A RENDSZERBEN ---"
& $adb -s $id shell "dumpsys accessibility" 2>&1 |
    Select-String -Pattern "superdl" | Select-Object -First 8

Write-Output ""
Write-Output "--- A SUPERDL FOLYAMAT ---"
& $adb -s $id shell "ps -A | grep superdl" 2>&1 | Select-Object -First 4

Write-Output ""
Write-Output "--- BENNE VAN-E A JAVITAS A TELEFONON LEVO APK-BAN ---"
& $adb -s $id shell "ls /data/user_de/0/com.superdl.launcher.debug/cache/ 2>/dev/null"
Write-Output "(a zarhang mappa csak akkor jon letre, ha mar szolt)"

Write-Output ""
Write-Output "--- TELJES PIN SEGED NAPLO (nem csak a bekapcsolas ota) ---"
& $adb -s $id logcat -d -b all -s SDL_PINASSIST:* 2>&1 | Select-Object -Last 15

Write-Output ""
Write-Output "--- A KEPERNYOZAR TIPUSA RESZLETESEN ---"
& $adb -s $id shell "cmd lock_settings get-disabled" 2>&1 | Select-Object -First 2
& $adb -s $id shell "dumpsys window | grep -i 'keyguard'" 2>&1 | Select-Object -First 5
