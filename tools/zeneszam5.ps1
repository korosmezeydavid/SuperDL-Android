$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$uri = "content://media/external/audio/media"
$r = & $adb shell content query --uri $uri --projection is_music:duration
$ok = 0
foreach ($line in $r) {
  if ($line -match "is_music=([0-9]+).*duration=([0-9]+)") {
    if ([int]$matches[1] -ne 0 -and [int]$matches[2] -gt 30000) { $ok++ }
  }
}
Write-Output ("SOROK: " + ($r | Measure-Object).Count)
Write-Output ("A LEJATSZO MOSTANTOL ENNYIT LAT: " + $ok)
