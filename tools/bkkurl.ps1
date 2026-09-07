chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$p = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\transit\TransitHelper.kt'
$lines = Get-Content $p -Encoding UTF8
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match 'fun bkkUrl|BKK_BASE|apiKey|API_KEY') {
        $a = [Math]::Max(0, $i - 2)
        for ($j = $a; $j -lt [Math]::Min($lines.Count, $i + 14); $j++) {
            Write-Output ("  " + ($j+1) + ": " + $lines[$j])
        }
        Write-Output "  ---"
    }
}
