# BEKULDOTT BESZEDTEMA MEGNEZESE
#
#   .\tools\temanezd.ps1                       <- a VAGOLAPROL veszi a linket
#   .\tools\temanezd.ps1 https://0x0.st/x.json
#   .\tools\temanezd.ps1 C:\Users\msn\Downloads\tema-vicces.json
#
# Letolti, ellenorzi, es kicsomagolja a hangokat egy mappaba, hogy meg tudd
# hallgatni. NEM tesz kozze semmit.
#
# A VAGOLAPOS MOD a legkenyelmesebb: a bekuldo leveleben kijelolod a
# linket (vagy akar az EGESZ levelet), Ctrl+C, es itt eleg ennyit beirni:
# temanezd. A szkript kiszedi belole az elso letoltesi cimet.
param(
    [Parameter(Position = 0)][string]$Forras = "",
    [switch]$NeNyisd
)
$ErrorActionPreference = 'Continue'
chcp 65001 > $null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONIOENCODING = 'utf-8'

if ([string]::IsNullOrWhiteSpace($Forras)) {
    $vagolap = ""
    try { $vagolap = (Get-Clipboard -Raw) } catch { }
    if ([string]::IsNullOrWhiteSpace($vagolap)) {
        Write-Output "A vagolap ures, es nem adtal meg linket sem."
        Write-Output "Masold ki a bekuldo levelebol a linket (vagy az egesz levelet),"
        Write-Output "es futtasd ujra: .\tools\temanezd.ps1"
        exit 1
    }
    # Az elso http vagy https cim a szovegbol. Igy egy EGESZ levelet is be
    # lehet masolni, nem kell a linket kulon kivadaszni.
    $talalat = [regex]::Match($vagolap, 'https?://[^\s<>"'')]+')
    if (-not $talalat.Success) {
        Write-Output "A vagolapon nem talaltam letoltesi cimet."
        Write-Output "Ha helyi fajlt neznel meg, add meg az utvonalat:"
        Write-Output "  .\tools\temanezd.ps1 C:\utvonal\tema.json"
        exit 1
    }
    $Forras = $talalat.Value.TrimEnd('.', ',', ';', ')')
    Write-Output "A vagolaprol: $Forras"
}

$py = "C:\Users\msn\Documents\SuperDL-Android\tools\kozos_tema.py"
$args = @('-X', 'utf8', $py, 'nezd', $Forras)
if ($NeNyisd) { $args += '--nenyisd' }
& python @args
