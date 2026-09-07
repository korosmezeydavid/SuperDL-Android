$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
Write-Output "--- KESZULEKEK ---"
& $adb devices -l
Write-Output "--- SUPERDL CSOMAGOK ---"
& $adb shell pm list packages | Select-String -Pattern "superdl"
