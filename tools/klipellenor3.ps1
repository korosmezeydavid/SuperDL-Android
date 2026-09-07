chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$pkg = 'com.superdl.launcher.debug'
$line = & $adb devices | Select-String -Pattern '\sdevice$' | Select-Object -First 1
if ($line -eq $null) { Write-Output "NINCS keszulek."; exit }
$id = $line.ToString().Split("`t")[0].Trim()

Write-Output "--- HITELESITETT (CE) tarolo: files ---"
& $adb -s $id shell "run-as $pkg ls files 2>&1 | head -12"
Write-Output ""
Write-Output "--- ESZKOZ-VEDETT (DE) tarolo tartalma ---"
& $adb -s $id shell "run-as $pkg ls /data/user_de/0/$pkg 2>&1 | head -12"
Write-Output ""
Write-Output "--- DE / files ---"
& $adb -s $id shell "run-as $pkg ls /data/user_de/0/$pkg/files 2>&1 | head -12"
Write-Output ""
Write-Output "--- DE / files / zarhang darab ---"
& $adb -s $id shell "run-as $pkg ls /data/user_de/0/$pkg/files/zarhang 2>/dev/null | wc -l"
Write-Output ""
Write-Output "--- SDL_APP naplo ---"
& $adb -s $id logcat -d -s SDL_APP 2>&1 | Select-Object -Last 6
