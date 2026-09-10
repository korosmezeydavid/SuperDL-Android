$ErrorActionPreference = 'Stop'
$kat = 'C:\Users\msn\Documents\SuperDL-repo\mobil-katalogus.json'
$uj  = 'C:\Users\msn\Documents\SuperDL-Android\tools\katalogus-bejegyzesek.json'

$d = Get-Content $kat -Raw -Encoding UTF8 | ConvertFrom-Json
$bejegyzesek = Get-Content $uj -Raw -Encoding UTF8 | ConvertFrom-Json

$lista = New-Object System.Collections.ArrayList
foreach ($m in $d.modules) { [void]$lista.Add($m) }

$meglevo = @{}
foreach ($m in $lista) { $meglevo[$m.id] = $true }

$hozzaadva = 0
foreach ($b in $bejegyzesek) {
    if ($meglevo.ContainsKey($b.id)) { continue }
    [void]$lista.Add($b)
    $hozzaadva++
}

$d.modules = $lista.ToArray()
$json = $d | ConvertTo-Json -Depth 12
[System.IO.File]::WriteAllText($kat, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "hozzaadva: $hozzaadva | katalogus osszesen: $($lista.Count) modul"
