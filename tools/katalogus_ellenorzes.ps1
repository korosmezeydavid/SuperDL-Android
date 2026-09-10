$kat = 'C:\Users\msn\Documents\SuperDL-repo\mobil-katalogus.json'
$d = Get-Content $kat -Raw -Encoding UTF8 | ConvertFrom-Json
Write-Output ("modulok osszesen: " + $d.modules.Count)
Write-Output "--- kviz modulok es a hozzajuk tartozo fajl ---"
$hiba = 0
foreach ($m in $d.modules) {
    if ($m.tipus -ne 'quiz') { continue }
    $p = Join-Path 'C:\Users\msn\Documents\SuperDL-repo' ($m.fajl -replace '/','\')
    $van = Test-Path $p
    if (-not $van) { $hiba++ }
    $db = ''
    if ($van) {
        $j = Get-Content $p -Raw -Encoding UTF8 | ConvertFrom-Json
        $db = $j.kerdesek.Count
    }
    Write-Output ("  " + $m.id.PadRight(30) + $(if ($van) { "OK  " } else { "HIANYZIK " }) + $db.ToString().PadLeft(6) + " kerdes  " + $m.nev)
}
Write-Output ("hianyzo fajl: " + $hiba)
