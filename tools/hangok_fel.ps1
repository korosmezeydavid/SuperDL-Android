$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
$src = "C:\Users\msn\Documents\SuperDL-Android\tools\_push"
$dst = "/sdcard/Android/data/com.superdl.launcher.debug/files/elena"
& $adb shell mkdir -p $dst
foreach ($f in Get-ChildItem "$src\*.wav") {
  & $adb push $f.FullName "$dst/$($f.Name)"
}
Write-Output "--- MI VAN A MAPPABAN ---"
& $adb shell ls -l $dst
