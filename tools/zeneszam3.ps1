$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$uri = "content://media/external/audio/media"
function Cnt($w) {
  if ($w -eq "") { $r = & $adb shell content query --uri $uri --projection _id }
  else { $r = & $adb shell content query --uri $uri --projection _id --where "`"$w`"" }
  return ($r | Measure-Object).Count
}
Write-Output ("OSSZES:            " + (Cnt ""))
Write-Output ("is_music!=0:       " + (Cnt "is_music!=0"))
Write-Output ("hosszu (>30s):     " + (Cnt "duration NOT BETWEEN 0 AND 30000"))
Write-Output ("duration IS NULL:  " + (Cnt "duration IS NULL"))
Write-Output ("is_music AND hosszu: " + (Cnt "is_music!=0 AND duration NOT BETWEEN 0 AND 30000"))
