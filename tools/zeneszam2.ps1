$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$w = "is_music!=0 AND duration NOT BETWEEN 0 AND 30000"
$r = & $adb shell content query --uri content://media/external/audio/media --projection _id --where "`"$w`""
Write-Output ("SZURT DARAB: " + ($r | Measure-Object).Count)
$r2 = & $adb shell content query --uri content://media/external/audio/media --projection _id
Write-Output ("OSSZES AUDIO: " + ($r2 | Measure-Object).Count)
