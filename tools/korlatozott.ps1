chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$p = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\setup\SetupDiagnostics.kt'
$lines = Get-Content $p -Encoding UTF8
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match 'korlatozott|korlátozott|restricted|installSource|telepites forrasa|Installer') {
        $a = [Math]::Max(0, $i - 4)
        for ($j = $a; $j -lt [Math]::Min($lines.Count, $i + 30); $j++) {
            Write-Output ("  " + ($j+1) + ": " + $lines[$j])
        }
        Write-Output "  ---"
        break
    }
}
