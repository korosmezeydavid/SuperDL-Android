$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
Write-Output "--- ELTAVOLITAS: com.superdl.launcher (kiadasi) ---"
& $adb uninstall com.superdl.launcher
Write-Output "EXIT=$LASTEXITCODE"
Write-Output "--- MI MARADT ---"
& $adb shell pm list packages | Select-String -Pattern "superdl"
Write-Output "--- KISEGITO SZOLGALTATASOK ---"
& $adb shell settings get secure enabled_accessibility_services
Write-Output "--- KEZDOKEPERNYO ---"
& $adb shell cmd role get-role-holders android.app.role.HOME
Write-Output "--- BILLENTYUZET ---"
& $adb shell settings get secure default_input_method
