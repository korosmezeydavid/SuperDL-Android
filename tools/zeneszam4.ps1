$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$uri = "content://media/external/audio/media"
$r = & $adb shell content query --uri $uri --projection duration
$vals = @()
foreach ($line in $r) {
  if ($line -match "duration=([0-9]+)") { $vals += [int]$matches[1] }
  elseif ($line -match "duration=NULL") { $vals += -1 }
}
Write-Output ("SOROK: " + ($r | Measure-Object).Count)
Write-Output ("ERTEKEK: " + $vals.Count)
Write-Output ("NULL: " + (($vals | Where-Object { $_ -lt 0 }) | Measure-Object).Count)
Write-Output ("0..30000: " + (($vals | Where-Object { $_ -ge 0 -and $_ -le 30000 }) | Measure-Object).Count)
Write-Output ("30s felett: " + (($vals | Where-Object { $_ -gt 30000 }) | Measure-Object).Count)
