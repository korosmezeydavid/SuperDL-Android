$f = 'C:\Users\msn\Documents\SuperDL-Android\tools\_fuggosegek.txt'
$sorok = Get-Content $f
Write-Output '=== media / support- talalatok ==='
$sorok | Where-Object { $_ -match 'media|support-' } | ForEach-Object {
    Write-Output ($_ -replace '[|\\+\- ]{2,}', '  ')
} | Select-Object -Unique
Write-Output ''
Write-Output '=== van-e androidx.media ==='
$m = $sorok | Where-Object { $_ -match 'androidx\.media' }
if ($m) { $m | Select-Object -Unique } else { Write-Output '  NINCS androidx.media a fuggosegek kozott' }
