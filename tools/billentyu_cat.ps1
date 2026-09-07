$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$id = "S62Pro2208000115"
$rel = "com.superdl.launcher/.keyboard.MatrixKeyboardService"

Write-Output "--- ELERHETO BILLENTYUZETEK ---"
& $adb -s $id shell ime list -s -a
Write-Output "--- ENGEDELYEZES + BEALLITAS: $rel ---"
& $adb -s $id shell ime enable $rel
& $adb -s $id shell ime set $rel
Write-Output "--- UTANA ---"
& $adb -s $id shell settings get secure default_input_method
& $adb -s $id shell settings get secure enabled_input_methods
