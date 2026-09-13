$ProgressPreference = 'SilentlyContinue'
$v = Invoke-RestMethod 'https://raw.githubusercontent.com/korosmezeydavid/SuperDL/mobil/verzio.json'
Write-Output ('verzio.json verzio: ' + $v.verzio)
Write-Output ('verzio.json kiadva: ' + $v.kiadva)
$rel = gh api repos/korosmezeydavid/SuperDL-Android/releases/latest | ConvertFrom-Json
Write-Output ('kiadas cimke: ' + $rel.tag_name)
foreach ($a in $rel.assets) {
    Write-Output ('  ' + $a.name + '  ' + $a.size + ' bajt  allapot: ' + $a.state)
}
