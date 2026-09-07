Add-Type -AssemblyName System.IO.Compression.FileSystem
$hit = Get-ChildItem -Path 'G:\' -Recurse -Filter 'Soft-Braille-Keyboard-master.zip' -ErrorAction SilentlyContinue | Select-Object -First 1
$a = [IO.Compression.ZipFile]::OpenRead($hit.FullName)
$want = @('Soft-Braille-Keyboard-master/LICENSE',
          'Soft-Braille-Keyboard-master/AUTHORS',
          'Soft-Braille-Keyboard-master/README')
$out = @()
foreach ($w in $want) {
  $e = $a.Entries | Where-Object { $_.FullName -eq $w }
  if ($e) {
    $out += "===== $w ====="
    $sr = New-Object IO.StreamReader($e.Open())
    if ($w -like '*LICENSE') { $out += ($sr.ReadToEnd() -split "`n" | Select-Object -First 25) }
    else { $out += $sr.ReadToEnd() }
    $sr.Close()
  }
}
$out | Out-File 'C:\Users\msn\Documents\SuperDL-Android\sbk_read.txt' -Encoding UTF8
$a.Dispose()
