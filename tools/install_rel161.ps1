$adb = 'C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$apk = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL-1.61.0-release.apk'

Write-Output "=== Eszkozok ==="
& $adb devices -l

Write-Output "=== Keszulek ==="
& $adb shell "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release"

Write-Output "=== Ami most fent van ==="
& $adb shell "pm list packages com.superdl.launcher"

Write-Output "=== Eltavolitas (ha van) ==="
& $adb uninstall com.superdl.launcher

Write-Output "=== Telepites: kiadasi 1.61.0 ==="
& $adb install $apk

Write-Output "=== Inditas ==="
& $adb shell "monkey -p com.superdl.launcher -c android.intent.category.LAUNCHER 1"
