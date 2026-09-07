chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$p = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\files\FileManagerHelper.kt'
$lines = Get-Content $p -Encoding UTF8
Write-Output "=== listDir ==="
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match 'fun listDir') {
        for ($j = $i; $j -lt [Math]::Min($lines.Count, $i + 40); $j++) { Write-Output ("  " + ($j+1) + ": " + $lines[$j]) }
    }
}
Write-Output ""
Write-Output "=== speakPreview a FileItem-ben (elemszam) ==="
Get-ChildItem -Path (Split-Path $p) -Filter *.kt |
  Select-String -Pattern 'fun speakPreview|listFiles|elem' |
  Select-Object -First 25 |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
