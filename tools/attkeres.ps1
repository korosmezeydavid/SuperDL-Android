chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$m = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\MainActivity.kt'
Write-Output "--- setupAttempts hasznalata ---"
Select-String -Path $m -Pattern 'setupAttempts' | ForEach-Object { Write-Output ("  " + $_.LineNumber + ": " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- SetupPrefs tartalma ---"
Get-Content 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher\setup\SetupPrefs.kt' -Encoding UTF8 | Select-Object -First 60 | ForEach-Object { Write-Output ("  " + $_) }
