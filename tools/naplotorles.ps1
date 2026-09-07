$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$id = "3116TF1010002416"
& $adb -s $id logcat -c
Write-Output "A naplo torolve. Most oldd fel a telefont."
