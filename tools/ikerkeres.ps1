chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
Write-Output "--- a 'ket SuperDL' felismerese ---"
Get-ChildItem -Path $root -Filter *.kt -Recurse |
  Select-String -Pattern 'KET SUPERDL|KÉT SUPERDL|ikerpeldany|masik valtozat|siblingPackage|otherVariant' |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- hol vizsgaljuk a sajat csomagnev testvereit ---"
Get-ChildItem -Path $root -Filter *.kt -Recurse |
  Select-String -Pattern 'launcher\.debug|getInstalledPackages|packageName ==' |
  Select-Object -First 25 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
