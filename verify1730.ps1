Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug\SuperDL-1.73.0-debug.apk"
"APK: " + (Test-Path $apk)
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
"--- ikon bejegyzesek az APK-ban ---"
foreach ($e in $zip.Entries) {
  if ($e.FullName -match "ic_launcher") { "{0}  ({1} bajt)" -f $e.FullName, $e.Length }
}
"--- regi shape ikon maradt-e? ---"
$shape = 0
foreach ($e in $zip.Entries) { if ($e.FullName -eq "res/drawable/ic_launcher.xml") { $shape = 1 } }
if ($shape -eq 1) { "IGEN - MEG MINDIG OTT VAN" } else { "nem, kikerult" }
$zip.Dispose()
