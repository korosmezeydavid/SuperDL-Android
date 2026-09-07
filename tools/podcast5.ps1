chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$m = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\MainActivity.kt'
$lines = Get-Content $m -Encoding UTF8
Write-Output "=== a negy gesztus-diszpecser fejlece ==="
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match 'private fun (handleSwipe|onSwipe|navigateUp|navigateDown|activateCurrent|goBack|onUp|onDown|onActivate|onBack)') {
        Write-Output ("  " + ($i+1) + ": " + $lines[$i].Trim())
    }
}
Write-Output ""
Write-Output "=== RadioListBrowse mint minta (hol szerepel) ==="
Select-String -Path $m -Pattern 'is AppFlow.LocationProfileBrowse' |
  ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
