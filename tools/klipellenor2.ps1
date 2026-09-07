chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$pkg = 'com.superdl.launcher.debug'
$line = & $adb devices | Select-String -Pattern '\sdevice$' | Select-Object -First 1
if ($line -eq $null) { Write-Output "NINCS keszulek."; exit }
$id = $line.ToString().Split("`t")[0].Trim()
Write-Output "klipek a TARTOS mappaban (files/zarhang):"
& $adb -s $id shell "run-as $pkg ls files/zarhang 2>/dev/null | wc -l"
Write-Output "elso par fajl:"
& $adb -s $id shell "run-as $pkg ls files/zarhang 2>/dev/null | head -4"
Write-Output ""
Write-Output "kicsomagolasi naplo:"
& $adb -s $id logcat -d 2>&1 | Select-String -Pattern 'kicsomagol' | Select-Object -Last 5 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
