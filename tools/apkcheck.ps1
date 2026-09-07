$apk = "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug\SuperDL-1.60.0-debug.apk"
Write-Output ("APK: {0:N1} MB" -f ((Get-Item $apk).Length/1MB))
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
$zip.Entries | Where-Object { $_.FullName -like "*gojni*" -or $_.FullName -like "*whmobile*" } |
    ForEach-Object { Write-Output ("{0}  {1:N1} MB" -f $_.FullName, ($_.Length/1MB)) }
$zip.Dispose()
