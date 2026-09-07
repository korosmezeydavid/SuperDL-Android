$adb = "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
Write-Output "--- KESZULEKEK ---"
& $adb devices -l
Write-Output "--- MELYIK SUPERDL VAN FENT (keszulekenkent) ---"
$ids = (& $adb devices) | Select-String -Pattern "\tdevice$" | ForEach-Object { ($_ -split "`t")[0] }
foreach ($id in $ids) {
    $model = (& $adb -s $id shell getprop ro.product.model).Trim()
    $brand = (& $adb -s $id shell getprop ro.product.brand).Trim()
    Write-Output ("=== " + $id + "  " + $brand + " " + $model + " ===")
    & $adb -s $id shell pm list packages | Select-String -Pattern "com.superdl.launcher"
}
