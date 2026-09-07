chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$m = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\AndroidManifest.xml'
Write-Output "--- directBootAware elofordulasok ---"
Select-String -Path $m -Pattern 'directBootAware' | ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- KeyguardPinAccessibilityService blokk ---"
$lines = Get-Content $m
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match 'KeyguardPinAccessibilityService') {
        $a = [Math]::Max(0, $i - 2)
        $b = [Math]::Min($lines.Count - 1, $i + 14)
        for ($j = $a; $j -le $b; $j++) { Write-Output ("  " + ($j+1) + ": " + $lines[$j]) }
    }
}
Write-Output ""
Write-Output "--- ScreenReaderService blokk ---"
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match 'ScreenReaderService') {
        $a = [Math]::Max(0, $i - 2)
        $b = [Math]::Min($lines.Count - 1, $i + 12)
        for ($j = $a; $j -le $b; $j++) { Write-Output ("  " + ($j+1) + ": " + $lines[$j]) }
    }
}
