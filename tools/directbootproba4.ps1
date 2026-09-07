chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$pkg = 'com.superdl.launcher.debug'
$apk = (Get-ChildItem -Path 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug' -Filter *.apk |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
$line = & $adb devices | Select-String -Pattern '\sdevice$' | Select-Object -First 1
if ($line -eq $null) { Write-Output "NINCS keszulek."; exit }
$id = $line.ToString().Split("`t")[0].Trim()

Write-Output "--- TELEPITES ---"
& $adb -s $id install -r $apk 2>&1 | Select-Object -Last 2

Write-Output ""
Write-Output "--- INDITAS, hogy a feloldas utani kicsomagolas lefusson ---"
& $adb -s $id shell "monkey -p $pkg -c android.intent.category.LAUNCHER 1" 2>&1 | Select-Object -Last 1
Start-Sleep -Seconds 12
Write-Output "klipek a TARTOS mappaban (kicsomagolas utan):"
& $adb -s $id shell "run-as $pkg ls files/zarhang 2>/dev/null | wc -l"

Write-Output ""
Write-Output "--- UJRAINDITAS ---"
& $adb -s $id reboot
Start-Sleep -Seconds 50
& $adb -s $id wait-for-device
Start-Sleep -Seconds 18

$log = 'C:\Users\msn\Documents\SuperDL-Android\tools\bootnaplo4.txt'
& $adb -s $id logcat -d -b all *:V 2>$null | Out-File -FilePath $log -Encoding UTF8

Write-Output "--- 1. ZAROLVA-E ---"
& $adb -s $id shell "dumpsys trust | grep -i deviceLocked"
Write-Output ""
Write-Output "--- 2. FUT-E ---"
$p = & $adb -s $id shell "ps -A | grep superdl"
if ([string]::IsNullOrWhiteSpace($p)) { Write-Output "  >>> NEM FUT <<<" } else { Write-Output $p }
Write-Output ""
Write-Output "--- 3. OSSZEOMLAS ---"
$c = Get-Content $log | Select-String -Pattern 'am_crash.*superdl'
if ($c -eq $null) { Write-Output "  NINCS" } else { $c | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) } }
Write-Output ""
Write-Output "--- 4. KLIPEK TULELTEK-E AZ UJRAINDITAST ---"
& $adb -s $id shell "run-as $pkg ls files/zarhang 2>/dev/null | wc -l"
Write-Output ""
Write-Output "--- 5. SEGED ABLAKA A ZARKEPERNYON ---"
Get-Content $log | Select-String -Pattern 'Window\{.*superdl.*\}: Setting back callback OnBackInvoked' | Select-Object -First 3 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- 6. SDL NAPLOK ---"
Get-Content $log | Select-String -Pattern 'SDL_PINASSIST|SDL_SCREENREADER|SDL_APP' | Select-Object -First 12 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
Write-Output "KESZ."
