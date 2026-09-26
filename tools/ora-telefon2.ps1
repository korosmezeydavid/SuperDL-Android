$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$rcv = 'com.superdl.launcher.debug/com.superdl.launcher.patrol.TalkingClockDebugReceiver'
$ki = 'C:\Users\msn\Documents\SuperDL-Android\idohang\leda\_telefon'
New-Item -ItemType Directory -Force $ki | Out-Null
$idok = @('14:30','00:45','12:00','19:55','08:10','12:05','23:00')
foreach ($i in $idok) { & $adb shell am broadcast -n $rcv --es mod wav --es ido $i | Out-Null }
Start-Sleep 4
foreach ($i in $idok) {
  $n = 'ora-' + $i.Replace(':','-') + '.wav'
  cmd /c "`"$adb`" exec-out run-as com.superdl.launcher.debug cat files/$n > `"$ki\$n`""
  $f = Get-Item "$ki\$n"; "$n $($f.Length)"
}
& $adb logcat -d -s SuperDL.OraProba | Select-Object -Last 8
