chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
Write-Output "=== fajlkezelo AppFlow allapotok ==="
Select-String -Path (Join-Path $root 'flow\AppFlow.kt') -Pattern 'File|Folder|Mappa' |
  Select-Object -First 20 |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== fajlkezelo fuggvenyek ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Select-String -Pattern 'private fun .*File.*Browse|private fun navigateFile|private fun openFolder|private fun enterFolder|FileManager|fileManager' |
  Select-Object -First 25 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
