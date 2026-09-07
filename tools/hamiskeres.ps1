chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$f = @(
 'C:\Users\msn\Documents\SuperDL-Android\dokumentumok\hirlevel-1.63.2.txt',
 'C:\Users\msn\Documents\SuperDL-Android\dokumentumok\Lorinczrichard3.txt',
 'C:\Users\msn\Documents\SuperDL-Android\tools\commitmsg1632.txt'
)
$pat = 'k' + [char]0xE9 + 'perny' + [char]0x151 + 'z' + [char]0xE1 + 'r|NEM TUDTAM|nem tudtam|reproduk|saj' + [char]0xE1 + 't k' + [char]0xE9 + 'sz'
foreach ($p in $f) {
    Write-Output ("=== " + (Split-Path $p -Leaf) + " ===")
    if (Test-Path $p) {
        Get-Content -Path $p -Encoding UTF8 | Select-String -Pattern $pat | ForEach-Object { Write-Output ("  " + $_.Line.Trim()) }
    } else { Write-Output "  (nincs meg)" }
    Write-Output ""
}
