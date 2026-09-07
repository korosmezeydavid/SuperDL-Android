chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\files'
Write-Output "=== FileItem.speakPreview teljes ==="
Get-ChildItem -Path $root -Filter *.kt | ForEach-Object {
    $lines = Get-Content $_.FullName -Encoding UTF8
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match 'fun speakPreview') {
            Write-Output ("--- " + $_.Name + " ---")
            for ($j = $i; $j -lt [Math]::Min($lines.Count, $i + 30); $j++) { Write-Output ("  " + ($j+1) + ": " + $lines[$j]) }
        }
    }
}
