chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$p = 'com.superdl.launcher.debug'

Write-Output "--- NAPLO TORLESE ---"
& $adb logcat -c

Write-Output ""
Write-Output "--- 1. FELIRAT KERESESE INDITASA (Vaci utca) ---"
& $adb shell "am start -n $p/com.superdl.launcher.textreader.TextSearchActivity --es text_query 'Vaci utca'" 2>&1 | Select-Object -First 6
Start-Sleep -Seconds 6
& $adb shell "dumpsys activity activities | grep -m 2 'topResumedActivity\|ResumedActivity'" 2>&1 | Select-Object -First 3

Write-Output ""
Write-Output "--- 2. HIBAK A NAPLOBAN ---"
& $adb logcat -d -b crash 2>&1 | Select-Object -Last 25

Write-Output ""
Write-Output "--- 3. SUPERDL SOROK ---"
& $adb logcat -d 2>&1 | Select-String -Pattern 'SuperDL|TextSearch|GuidanceTone|AndroidRuntime' | Select-Object -Last 25
Write-Output "KESZ."
