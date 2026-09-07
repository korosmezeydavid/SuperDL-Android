$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb kill-server
Start-Sleep -Seconds 2
& $adb start-server
Start-Sleep -Seconds 3
Write-Output "=== 1. probalkozas ==="
& $adb devices -l
Start-Sleep -Seconds 12
Write-Output "=== 2. probalkozas (12 mp mulva) ==="
& $adb devices -l
Start-Sleep -Seconds 15
Write-Output "=== 3. probalkozas (tovabbi 15 mp mulva) ==="
& $adb devices -l
