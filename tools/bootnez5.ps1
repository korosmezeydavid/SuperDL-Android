chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$log = 'C:\Users\msn\Documents\SuperDL-Android\tools\bootnaplo5.txt'
Write-Output "--- melyik valtozat indult el ---"
Get-Content $log | Select-String -Pattern 'am_proc_start.*superdl' | Select-Object -First 6 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- ablakok ---"
Get-Content $log | Select-String -Pattern 'Window\{.*superdl' | Select-Object -First 4 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- barmilyen osszeomlas ---"
Get-Content $log | Select-String -Pattern 'FATAL EXCEPTION' | Select-Object -First 4 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
