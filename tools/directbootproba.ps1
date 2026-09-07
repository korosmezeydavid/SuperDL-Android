chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$id = (& $adb devices | Select-String -Pattern '\sdevice$' | Select-Object -First 1).ToString().Split("`t")[0].Trim()
Write-Output "Keszulek: $id"

Write-Output "--- 1. ELOZETES: van-e egyaltalan SDL_PINASSIST sor a pufferben ---"
$elozetes = & $adb -s $id logcat -d -s SDL_PINASSIST 2>&1
Write-Output ("  sorok szama: " + ($elozetes | Measure-Object).Count)

Write-Output ""
Write-Output "--- 2. UJRAINDITAS ---"
& $adb -s $id reboot
Start-Sleep -Seconds 55

Write-Output "--- 3. VARAKOZAS a keszulekre ---"
& $adb -s $id wait-for-device
Start-Sleep -Seconds 25

Write-Output ""
Write-Output "--- 4. ALLAPOT (feloldas ELOTT kell legyen) ---"
& $adb -s $id shell "dumpsys trust | grep -i deviceLocked"
& $adb -s $id shell "dumpsys user | grep -i 'Running.*unlocked\|unlocked'" 2>&1 | Select-Object -First 5

Write-Output ""
Write-Output "--- 5. A PIN SEGED NAPLOJA AZ INDULASTOL ---"
& $adb -s $id logcat -d -s SDL_PINASSIST 2>&1 | Select-Object -First 60

Write-Output ""
Write-Output "--- 6. ELINDULT-E EGYALTALAN AZ ALKALMAZAS FOLYAMATA ---"
& $adb -s $id shell "ps -A | grep superdl"

Write-Output ""
Write-Output "--- 7. BEEPITETT KLIPEK GYORSITOTARA (eszkoz-vedett tarolo) ---"
& $adb -s $id shell "ls /data/user_de/0/com.superdl.launcher.debug/cache/zarhang 2>/dev/null | wc -l"
Write-Output "KESZ."
