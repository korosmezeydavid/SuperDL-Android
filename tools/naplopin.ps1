$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$id = "3116TF1010002416"
Write-Output "--- A PIN SEGED NAPLOJA ---"
& $adb -s $id logcat -d -s SDL_PINASSIST:* 2>&1 | Select-Object -Last 40
Write-Output ""
Write-Output "--- SZOLALT-E MEG BEEPITETT KLIP (a zarhang gyorsitotar) ---"
& $adb -s $id shell "run-as com.superdl.launcher.debug ls cache/zarhang 2>/dev/null | wc -l"
Write-Output "(0 = a beepitett klipeket nem hasznaltuk, a rendes beszed szolt)"
Write-Output ""
Write-Output "--- ALLAPOT ---"
& $adb -s $id shell "dumpsys trust" 2>&1 | Select-String -Pattern "deviceLocked" | Select-Object -First 1
& $adb -s $id shell uptime
