chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
$pat = 'SUPERDL EGY TELEFONON'
Write-Output "--- a diagnosztika sora ---"
Get-ChildItem -Path $root -Filter *.kt -Recurse |
  Select-String -Pattern $pat -SimpleMatch |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber) }
Write-Output ""
$p = Join-Path $root 'setup\SetupDiagnostics.kt'
$lines = Get-Content $p -Encoding UTF8
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -like "*SUPERDL EGY TELEFONON*") {
        $a = [Math]::Max(0, $i - 3)
        for ($j = $a; $j -lt [Math]::Min($lines.Count, $i + 6); $j++) {
            Write-Output ("  " + ($j+1) + ": " + $lines[$j])
        }
    }
}
Write-Output ""
Write-Output "--- a felismero fuggveny ---"
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match 'fun .*[Ii]ker|fun .*[Tt]win|fun .*ketSuperdl|fun .*masodik') {
        for ($j = $i; $j -lt [Math]::Min($lines.Count, $i + 25); $j++) {
            Write-Output ("  " + ($j+1) + ": " + $lines[$j])
        }
    }
}
