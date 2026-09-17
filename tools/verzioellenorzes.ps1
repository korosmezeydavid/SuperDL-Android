$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$pkgs = & $adb shell pm list packages 2>&1 | Where-Object { $_ -match "superdl" }
"csomagok:"
$pkgs
foreach ($p in $pkgs) {
    $name = $p -replace "package:", ""
    $dump = & $adb shell dumpsys package $name.Trim() 2>&1
    $v = $dump | Where-Object { $_ -match "versionName|versionCode" } | Select-Object -First 2
    "--- " + $name
    $v
}
