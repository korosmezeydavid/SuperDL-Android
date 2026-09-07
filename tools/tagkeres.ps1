chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$dir = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\lock\keyguard'
foreach ($n in @('KeyguardVoice.kt','KeyguardPinOverlayController.kt')) {
    Write-Output ("=== " + $n + " ===")
    Select-String -Path (Join-Path $dir $n) -Pattern 'Log\.' | ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
}
