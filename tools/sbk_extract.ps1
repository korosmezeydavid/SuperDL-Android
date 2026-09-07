Add-Type -AssemblyName System.IO.Compression.FileSystem
$hit = Get-ChildItem -Path 'G:\' -Recurse -Filter 'Soft-Braille-Keyboard-master.zip' -ErrorAction SilentlyContinue | Select-Object -First 1
$dest = 'C:\Users\msn\Documents\SuperDL-Android\sbk'
if (-not (Test-Path $dest)) { New-Item -ItemType Directory -Path $dest | Out-Null }
$a = [IO.Compression.ZipFile]::OpenRead($hit.FullName)
$want = @('Pad.java','VerticalPad.java','HorizontalPad.java','PadUtilities.java','BrailleView.java','KeyboardListener.java','Options.java')
foreach ($e in $a.Entries) {
  $n = Split-Path $e.FullName -Leaf
  if ($want -contains $n) {
    [IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $dest $n), $true)
  }
  if ($e.FullName -eq 'Soft-Braille-Keyboard-master/res/xml/ime_preferences.xml') {
    [IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $dest 'ime_preferences.xml'), $true)
  }
}
$a.Dispose()
Get-ChildItem $dest | ForEach-Object { $_.Name }
