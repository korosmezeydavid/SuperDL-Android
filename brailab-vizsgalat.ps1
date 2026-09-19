$dir = "C:\Users\msn\Downloads\Addonbrailab\addon"
Get-ChildItem $dir -File | Select-Object Name, Length | Format-Table -AutoSize

function Get-PeMachine([string]$path) {
    $fs = [System.IO.File]::OpenRead($path)
    $br = New-Object System.IO.BinaryReader($fs)
    $fs.Position = 0x3C
    $peOffset = $br.ReadInt32()
    $fs.Position = $peOffset
    $sig = $br.ReadUInt32()
    $machine = $br.ReadUInt16()
    $br.Close(); $fs.Close()
    switch ($machine) {
        0x014c { "x86 (32 bites Windows)" }
        0x8664 { "x64 (64 bites Windows)" }
        0x01c4 { "ARM32" }
        0xAA64 { "ARM64" }
        default { "ismeretlen: 0x{0:X}" -f $machine }
    }
}

"TTS.dll  -> " + (Get-PeMachine (Join-Path $dir "TTS.dll"))
"brailab_host.exe -> " + (Get-PeMachine (Join-Path $dir "brailab_host.exe"))
