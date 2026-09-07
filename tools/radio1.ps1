chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
Write-Output "=== radio modul fajljai ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Where-Object { $_.FullName -match 'radio' } |
  ForEach-Object { Write-Output ("  " + $_.FullName.Substring($root.Length+1) + "   " + [math]::Round($_.Length/1024) + " kB") }
Write-Output ""
Write-Output "=== adok a listaban ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Select-String -Pattern 'Petofi|Kossuth|Bartok|radio\.hu|mediaklikk|http.*stream|\.m3u|icecast' |
  Select-Object -First 30 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
