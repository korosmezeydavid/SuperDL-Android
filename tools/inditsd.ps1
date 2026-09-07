$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
& $adb shell am start -n com.superdl.launcher.debug/com.superdl.launcher.MainActivity
Write-Output "--- BILLENTYUZET ---"
& $adb shell settings get secure default_input_method
Write-Output "--- CSOMAGOK ---"
& $adb shell pm list packages | Select-String -Pattern "superdl"
