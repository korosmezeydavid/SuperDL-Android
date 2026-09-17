Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\debug\SuperDL-1.68.0-debug.apk"
$needles = @("face_camera_video", "media_browse", "/hangoskonyv", "/audiobook", "media_hangcimkek", "is_audiobook")
$found = @{}
foreach ($n in $needles) { $found[$n] = $false }
$zip = [System.IO.Compression.ZipFile]::OpenRead($apk)
try {
    foreach ($e in $zip.Entries) {
        if ($e.FullName -notlike "*.dex") { continue }
        $s = $e.Open()
        $ms = New-Object System.IO.MemoryStream
        $s.CopyTo($ms)
        $s.Close()
        $text = [System.Text.Encoding]::UTF8.GetString($ms.ToArray())
        $ms.Close()
        foreach ($n in $needles) {
            if (-not $found[$n] -and $text.Contains($n)) { $found[$n] = $true }
        }
    }
} finally { $zip.Dispose() }
foreach ($n in $needles) {
    if ($found[$n]) { "OK   " + $n } else { "HIANYZIK " + $n }
}
