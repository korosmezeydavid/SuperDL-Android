Add-Type -AssemblyName System.IO.Compression.FileSystem
$hit = Get-ChildItem -Path 'G:\' -Recurse -Filter 'Soft-Braille-Keyboard-master.zip' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $hit) { 'NOT FOUND' | Out-File 'C:\Users\msn\Documents\SuperDL-Android\sbk_list.txt' -Encoding UTF8; exit 1 }
$a = [IO.Compression.ZipFile]::OpenRead($hit.FullName)
$out = @($hit.FullName)
$out += $a.Entries | ForEach-Object { "{0}`t{1}" -f $_.Length, $_.FullName }
$out | Out-File -FilePath 'C:\Users\msn\Documents\SuperDL-Android\sbk_list.txt' -Encoding UTF8
$a.Dispose()
