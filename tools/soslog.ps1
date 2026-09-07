$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb devices
Write-Output '--- SOS / SMS sorok a naploban ---'
& $adb logcat -d -v time 2>$null | Select-String -Pattern 'SOS|Vesz-SMS|SDL_SMS|SmsHelper' | Select-Object -Last 40
