chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$m = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\AndroidManifest.xml'
Write-Output "--- van-e <queries> blokk ---"
$lines = Get-Content $m
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '<queries|</queries>|TTS_SERVICE|QUERY_ALL_PACKAGES') {
        Write-Output ("  " + ($i+1) + ": " + $lines[$i].Trim())
    }
}
Write-Output ""
Write-Output "--- a queries blokk teljes tartalma (ha van) ---"
$a = ($lines | Select-String -Pattern '<queries' | Select-Object -First 1)
if ($a -ne $null) {
    $start = $a.LineNumber - 1
    for ($j = $start; $j -lt [Math]::Min($lines.Count, $start + 40); $j++) {
        Write-Output ("  " + ($j+1) + ": " + $lines[$j])
        if ($lines[$j] -match '</queries>') { break }
    }
} else { Write-Output "  NINCS <queries> BLOKK" }
Write-Output ""
Write-Output "--- InCallService / DIAL szandekszurok ---"
Select-String -Path $m -Pattern 'InCallService|action.DIAL|CALL_BUTTON' | ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
