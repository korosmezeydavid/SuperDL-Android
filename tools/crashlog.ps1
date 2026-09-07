$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb logcat -d -b crash -v time 2>$null | Select-Object -Last 60
Write-Output '=== superdl FATAL a fo pufferbol ==='
& $adb logcat -d -v time 2>$null | Select-String -Pattern 'FATAL|AndroidRuntime|superdl' | Select-Object -Last 40
