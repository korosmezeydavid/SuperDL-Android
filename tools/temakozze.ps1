# A MAR MEGNEZETT TEMA KOZZETETELE
#   .\tools\temakozze.ps1 viccestema
#   .\tools\temakozze.ps1 viccestema -Felulir
#
# Feltolti a tema fajlt a tarolo mobil agara, es felveszi a katalogus-sort.
# Innentol MINDENKI telefonjan ott van, alkalmazas-frissites nelkul.
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Azonosito,
    [switch]$Felulir
)
$ErrorActionPreference = 'Continue'
chcp 65001 > $null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONIOENCODING = 'utf-8'
$py = "C:\Users\msn\Documents\SuperDL-Android\tools\kozos_tema.py"
$args = @('-X', 'utf8', $py, 'kozzetesz', $Azonosito)
if ($Felulir) { $args += '--felulir' }
& python @args
