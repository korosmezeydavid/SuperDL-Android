Set-Location 'C:\Users\msn\Documents\SuperDL-Android'
$raw = gh api repos/korosmezeydavid/SuperDL-Android/releases
$all = $raw | ConvertFrom-Json
$rel = $all | Where-Object { $_.tag_name -eq 'v1.69.0' }
if (-not $rel) { Write-Output 'NINCS ilyen kiadas'; exit }
Write-Output ("id = " + $rel.id)
Write-Output ("draft = " + $rel.draft)
Write-Output '--- csatolmanyok ---'
foreach ($a in $rel.assets) {
    Write-Output ($a.name + "  " + $a.size + "  " + $a.state)
}
