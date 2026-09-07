chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$line = & $adb devices | Select-String -Pattern '\sdevice$' | Select-Object -First 1
if ($line -eq $null) { Write-Output "NINCS keszulek."; exit }
$id = $line.ToString().Split("`t")[0].Trim()
Write-Output "Keszulek: $id"
$log = 'C:\Users\msn\Documents\SuperDL-Android\tools\bootnaplo.txt'
& $adb -s $id logcat -d -b all *:V 2>$null | Out-File -FilePath $log -Encoding UTF8
Write-Output ("Napló sorok: " + (Get-Content $log).Count)

Write-Output ""
Write-Output "--- 1. SDL_PINASSIST ---"
Get-Content $log | Select-String -Pattern 'SDL_PINASSIST' | Select-Object -First 20 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }

Write-Output ""
Write-Output "--- 2. SDL_APP ---"
Get-Content $log | Select-String -Pattern 'SDL_APP' | Select-Object -First 20 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }

Write-Output ""
Write-Output "--- 3. non-encryption-aware / directBoot elutasitas ---"
Get-Content $log | Select-String -Pattern 'encryption-aware|encryption aware|directBoot|Direct boot' | Select-Object -First 20 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }

Write-Output ""
Write-Output "--- 4. AccessibilityManagerService a superdl-rol ---"
Get-Content $log | Select-String -Pattern 'superdl' | Select-String -Pattern 'ccessibility|Binding|bind' | Select-Object -First 25 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }

Write-Output ""
Write-Output "--- 5. barmi a csomagrol az elso 40 sorban ---"
Get-Content $log | Select-String -Pattern 'com.superdl.launcher' | Select-Object -First 40 | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
Write-Output "KESZ."
