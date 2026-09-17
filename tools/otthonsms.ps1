$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$out = & $adb shell content query --uri content://sms/sent --projection "body,date" 2>&1
$out | Out-File -FilePath "C:\Users\msn\Documents\SuperDL-Android\tools\smsdump.txt" -Encoding utf8
$t = Get-Content "C:\Users\msn\Documents\SuperDL-Android\tools\smsdump.txt"
"sorok: " + $t.Count
$t | Select-Object -Last 8
