Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = 'C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL.apk'
$needles = @(
    "callback_list", "sms_pending", "kesobbi_emlekeztetok",
    "HOME_WATCH_FOLLOWUP", "LATER_FIRE",
    "face_camera_video", "media_browse", "media_hangcimkek",
    "/hangoskonyv", "is_audiobook",
    "Itt voltam ", "holnaputan",
    "csatolt f", "hibajelentesek"
)
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
$hiany = 0
foreach ($n in $needles) {
    if ($found[$n]) { "OK   " + $n } else { "HIANYZIK " + $n; $hiany++ }
}
Write-Output "--- hianyzo: $hiany ---"
