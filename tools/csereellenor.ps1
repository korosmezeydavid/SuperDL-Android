chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
Write-Output "--- Elgepelt minta: valami.com.superdl... ---"
Get-ChildItem -Path $root -Filter *.kt -Recurse |
  Select-String -Pattern '[A-Za-z_][A-Za-z0-9_]*\.com\.superdl' |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- SafePrefs.get( hivasok szama fajlonkent ---"
Get-ChildItem -Path $root -Filter *.kt -Recurse |
  ForEach-Object {
    $c = (Select-String -Path $_.FullName -Pattern 'SafePrefs\.get\(' | Measure-Object).Count
    if ($c -gt 0) { Write-Output ("  " + $c + "  " + $_.Name) }
  }
