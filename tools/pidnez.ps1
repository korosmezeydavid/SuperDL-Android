chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$log = 'C:\Users\msn\Documents\SuperDL-Android\tools\bootnaplo2.txt'
Write-Output ("osszes sor: " + (Get-Content $log).Count)
Write-Output ""
Write-Output "--- a 2060-as folyamat sorai ---"
Get-Content $log | Select-String -Pattern '\s2060\s+\d+\s' | Select-Object -First 30 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "--- barmi SDL / PINASSIST (kis-nagybetu nelkul) ---"
Get-Content $log | Select-String -Pattern 'pinassist|screenreader|superdl' -CaseSensitive:$false | Select-Object -First 25 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
