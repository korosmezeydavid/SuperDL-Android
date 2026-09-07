chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$log = 'C:\Users\msn\Documents\SuperDL-Android\tools\bootnaplo2.txt'
Write-Output "--- FATAL / am_crash ---"
Get-Content $log | Select-String -Pattern 'FATAL EXCEPTION|am_crash|Caused by' | Select-Object -First 15 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- a kivetel elso 12 verem-sora ---"
Get-Content $log | Select-String -Pattern 'E AndroidRuntime' | Select-Object -First 16 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- superdl folyamat elete ---"
Get-Content $log | Select-String -Pattern 'am_proc_start.*superdl|am_proc_died.*superdl|Scheduling restart.*superdl' | Select-Object -First 15 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- SDL naplok ---"
Get-Content $log | Select-String -Pattern 'SDL_PINASSIST|SDL_SCREENREADER|SDL_APP|SDL_SAFEPREFS' | Select-Object -First 30 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
