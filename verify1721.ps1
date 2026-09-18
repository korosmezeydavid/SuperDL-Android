Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug\SuperDL-1.72.1-debug.apk"
"APK letezik: " + (Test-Path $apk)
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
$needles = @("A CSEND NEM HIBA","noteSelfStop","lastSelfStop","Elena figyeles onkikapcsolasa")
$found = @{}
foreach ($n in $needles) { $found[$n] = 0 }
foreach ($e in $zip.Entries) {
  if ($e.FullName -like "classes*.dex") {
    $s = $e.Open(); $ms = New-Object System.IO.MemoryStream
    $s.CopyTo($ms); $s.Close()
    $text = [System.Text.Encoding]::UTF8.GetString($ms.ToArray())
    foreach ($n in $needles) { if ($text.Contains($n)) { $found[$n] = $found[$n] + 1 } }
    $ms.Dispose()
  }
}
$zip.Dispose()
foreach ($n in $needles) { "$n = " + $found[$n] }
"---- TELEPITES ----"
& "C:\Users\msn\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r $apk
