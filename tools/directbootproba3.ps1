chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$line = & $adb devices | Select-String -Pattern '\sdevice$' | Select-Object -First 1
if ($line -eq $null) { Write-Output "NINCS keszulek."; exit }
$id = $line.ToString().Split("`t")[0].Trim()
Write-Output "Keszulek: $id"

Write-Output "--- UJRAINDITAS (harmadik proba) ---"
& $adb -s $id reboot
Start-Sleep -Seconds 50
& $adb -s $id wait-for-device
Start-Sleep -Seconds 18

$log = 'C:\Users\msn\Documents\SuperDL-Android\tools\bootnaplo3.txt'
& $adb -s $id logcat -d -b all *:V 2>$null | Out-File -FilePath $log -Encoding UTF8
Write-Output ("naplo sorok: " + (Get-Content $log).Count)

Write-Output ""
Write-Output "--- 1. ZAROLVA-E ---"
& $adb -s $id shell "dumpsys trust | grep -i deviceLocked"

Write-Output ""
Write-Output "--- 2. FUT-E A FOLYAMAT ---"
$p = & $adb -s $id shell "ps -A | grep superdl"
if ([string]::IsNullOrWhiteSpace($p)) { Write-Output "  >>> NEM FUT <<<" } else { Write-Output $p }

Write-Output ""
Write-Output "--- 3. SUPERDL OSSZEOMLAS ---"
$c = Get-Content $log | Select-String -Pattern 'am_crash.*superdl'
if ($c -eq $null) { Write-Output "  NINCS" } else { $c | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) } }

Write-Output ""
Write-Output "--- 4. MEGJELENT-E A SEGED ABLAKA (type=2032 overlay) ---"
Get-Content $log | Select-String -Pattern 'Setting back callback OnBackInvoked.*superdl' | Select-Object -First 5 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }

Write-Output ""
Write-Output "--- 5. BESZED / HANGLEJATSZAS ---"
Get-Content $log | Select-String -Pattern 'ITextToSpeechService.*superdl|MediaPlayer.*superdl' | Select-Object -First 5 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }

Write-Output ""
Write-Output "--- 6. SDL NAPLOK ---"
Get-Content $log | Select-String -Pattern 'SDL_PINASSIST|SDL_SCREENREADER|SDL_APP|SDL_SAFEPREFS' | Select-Object -First 12 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }

Write-Output ""
Write-Output "--- 7. BEEPITETT KLIPEK ---"
& $adb -s $id shell "find /data/user_de/0/com.superdl.launcher.debug -name '*.m4a' 2>/dev/null | wc -l"
Write-Output "KESZ."
