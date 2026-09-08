$apk = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL.apk'
$aapt = Get-ChildItem 'C:\Users\msn\AppData\Local\Android\Sdk\build-tools' -Filter aapt2.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1

Write-Output '--- 1. verzio a kiadasi APK-ban ---'
if ($aapt) {
    & $aapt.FullName dump badging $apk 2>$null | Select-String -Pattern "versionName" | Select-Object -First 1
}

Write-Output '--- 2. INDITOPULT: megjelenik-e az alkalmazaslistaban ---'
if ($aapt) {
    $tree = & $aapt.FullName dump xmltree --file AndroidManifest.xml $apk 2>$null
    $launcher = $tree | Select-String -Pattern 'android.intent.category.LAUNCHER'
    # NEM $home: az a PowerShellben csak olvashato, es hibat dob.
    $kezdo = $tree | Select-String -Pattern 'android.intent.category.HOME'
    Write-Output ("    LAUNCHER kategoria: " + $(if ($launcher) { "MEGVAN" } else { "HIANYZIK" }))
    Write-Output ("    HOME kategoria:     " + $(if ($kezdo) { "MEGVAN" } else { "HIANYZIK" }))
    Write-Output '    TTS_SERVICE action:'
    $tree | Select-String -Pattern 'TTS_SERVICE' | ForEach-Object { Write-Output ("      " + $_.Line.Trim()) }
}

Write-Output '--- 3. hangok a KIADASI APK-ban ---'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
$k = $zip.Entries | Where-Object { $_.FullName -like "assets/zarhang/*" }
Write-Output ("zarhang: $($k.Count) klip")
$e = $zip.Entries | Where-Object { $_.FullName -like "assets/hangtemak/elena/*" }
Write-Output ("Elena: $($e.Count) klip")
$zip.Dispose()

Write-Output '--- 4. ALAIRAS ---'
$apksigner = Get-ChildItem 'C:\Users\msn\AppData\Local\Android\Sdk\build-tools' -Filter apksigner.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
if ($apksigner) {
    & $apksigner.FullName verify --print-certs $apk 2>&1 | Select-String -Pattern 'Signer #1 certificate SHA-256|Verified using' | ForEach-Object { Write-Output ("    " + $_.Line.Trim()) }
}
Write-Output 'KESZ.'
