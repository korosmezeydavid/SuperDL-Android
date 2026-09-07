# AZ ALAIRAS ATVIZSGALASA — miert allhat le a telepito
$ErrorActionPreference = 'Continue'
chcp 65001 > $null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$apk = "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL.apk"
$sdk = "C:\Users\msn\AppData\Local\Android\Sdk"

Write-Output "--- 0. AZ APK ---"
Get-Item $apk | Select-Object Name, Length, LastWriteTime | Format-List

Write-Output "--- 1. apksigner keresese ---"
$signer = Get-ChildItem "$sdk\build-tools" -Recurse -Filter 'apksigner.bat' -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $signer) { Write-Output "NINCS apksigner a build-tools alatt."; }
else {
    Write-Output $signer.FullName
    Write-Output ""
    Write-Output "--- 2. ALAIRAS-ELLENORZES (v1/v2/v3/v4) ---"
    & $signer.FullName verify --verbose --print-certs $apk 2>&1
}

Write-Output ""
Write-Output "--- 3. MIT MOND A MANIFEST (aapt) ---"
$aapt = Get-ChildItem "$sdk\build-tools" -Recurse -Filter 'aapt2.exe' -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if ($aapt) {
    & $aapt.FullName dump badging $apk 2>&1 |
        Select-String -Pattern "^package:|sdkVersion|targetSdkVersion|application-label:|native-code" 
}

Write-Output ""
Write-Output "--- 4. ZIP-SZERKEZET: az elso bejegyzes igazitasa szamit ---"
$zipalign = Get-ChildItem "$sdk\build-tools" -Recurse -Filter 'zipalign.exe' -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if ($zipalign) { & $zipalign.FullName -c -v 4 $apk 2>&1 | Select-Object -Last 3 }
