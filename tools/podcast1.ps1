chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
Write-Output "=== podcast AppFlow allapotok ==="
Select-String -Path (Join-Path $root 'flow\AppFlow.kt') -Pattern 'Podcast' |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== 'varj a diktalasra' tipusu uzenetek ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Select-String -Pattern 'diktál|diktal|Diktál' |
  Select-Object -First 25 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
