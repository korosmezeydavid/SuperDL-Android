$ErrorActionPreference = 'Stop'
$zip  = 'C:\Users\msn\Documents\SuperDL-Android\tools\kviz-modulok.zip'
$tmp  = 'C:\Users\msn\Documents\SuperDL-Android\tools\_kviz_tmp'
$cel  = 'C:\Users\msn\Documents\SuperDL-repo\modulok\jatekok'

Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
Expand-Archive -Path $zip -DestinationPath $tmp -Force

$forras = Join-Path $tmp 'kviz'
$db = 0
Get-ChildItem $forras -Filter 'kviz-*.json' | ForEach-Object {
    Copy-Item $_.FullName (Join-Path $cel $_.Name) -Force
    $db++
}
Write-Output "atmasolva: $db modul"
Copy-Item (Join-Path $forras 'katalogus-bejegyzesek.json') 'C:\Users\msn\Documents\SuperDL-Android\tools\katalogus-bejegyzesek.json' -Force
Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
Get-ChildItem $cel -Filter '*.json' | Select-Object Name, Length | Format-Table -AutoSize
