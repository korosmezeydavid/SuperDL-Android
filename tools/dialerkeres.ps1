chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
Write-Output "--- ROLE_DIALER elofordulasok ---"
Get-ChildItem -Path $root -Filter *.kt -Recurse |
  Select-String -Pattern 'ROLE_DIALER|ACTION_CHANGE_DEFAULT_DIALER|role_dialer' |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
