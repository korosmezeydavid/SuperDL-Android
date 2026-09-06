# BEKULDOTT BESZEDTEMA MEGNEZESE
#   .\tools\temanezd.ps1 https://0x0.st/valami.json
#   .\tools\temanezd.ps1 C:\Users\msn\Downloads\tema-vicces.json
#
# Letolti, ellenorzi, es kicsomagolja a hangokat egy mappaba, hogy meg tudd
# hallgatni. NEM tesz kozze semmit.
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Forras,
    [switch]$NeNyisd
)
$ErrorActionPreference = 'Continue'
chcp 65001 > $null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONIOENCODING = 'utf-8'
$py = "C:\Users\msn\Documents\SuperDL-Android\tools\kozos_tema.py"
$args = @('-X', 'utf8', $py, 'nezd', $Forras)
if ($NeNyisd) { $args += '--nenyisd' }
& python @args
