chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
Write-Output "--- ESSENTIAL kihagyhatosag ---"
Get-ChildItem -Path $root -Filter *.kt -Recurse |
  Select-String -Pattern 'ESSENTIAL' |
  Select-Object -First 25 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- skipSetupRequirement ---"
Get-ChildItem -Path $root -Filter *.kt -Recurse |
  Select-String -Pattern 'fun skipSetupRequirement|canSkip|kihagyhat|attempts|probalkozas' |
  Select-Object -First 25 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
