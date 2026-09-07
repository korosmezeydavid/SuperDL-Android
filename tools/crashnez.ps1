chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$log = 'C:\Users\msn\Documents\SuperDL-Android\tools\bootnaplo2.txt'
Write-Output "--- AndroidRuntime sorok ---"
Get-Content $log | Select-String -Pattern 'AndroidRuntime|am_crash' | Select-Object -First 45 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- superdl folyamat elete ---"
Get-Content $log | Select-String -Pattern 'am_proc_start.*superdl|am_proc_died.*superdl|Scheduling restart.*superdl' | Select-Object -First 20 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
