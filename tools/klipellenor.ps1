chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$line = & $adb devices | Select-String -Pattern '\sdevice$' | Select-Object -First 1
if ($line -eq $null) { Write-Output "NINCS keszulek."; exit }
$id = $line.ToString().Split("`t")[0].Trim()
$pkg = 'com.superdl.launcher.debug'

Write-Output "--- ROSSZ MERES: sima shell (a shell nem lathat be a program mappajaba) ---"
& $adb -s $id shell "ls /data/user_de/0/$pkg/cache/zarhang 2>&1 | head -3"

Write-Output ""
Write-Output "--- HELYES MERES: run-as, vagyis a program sajat joga ---"
& $adb -s $id shell "run-as $pkg ls cache/zarhang 2>&1 | head -5"
Write-Output "  darab:"
& $adb -s $id shell "run-as $pkg ls cache/zarhang 2>/dev/null | wc -l"
