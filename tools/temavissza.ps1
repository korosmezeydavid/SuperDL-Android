# TEMA LEVETELE A KATALOGUSBOL
#   .\tools\temavissza.ps1 viccestema          (csak a listabol veszi le)
#   .\tools\temavissza.ps1 viccestema -Fajlt   (a tema fajljat is torli)
#
# Alapbol a fajl MEGMARAD: aki mar letoltotte, annal tovabb mukodik.
# A -Fajlt kapcsoloval a tarolobol is torlodik.
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Azonosito,
    [switch]$Fajlt
)
$ErrorActionPreference = 'Continue'
chcp 65001 > $null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONIOENCODING = 'utf-8'
$py = "C:\Users\msn\Documents\SuperDL-Android\tools\kozos_tema.py"
$args = @('-X', 'utf8', $py, 'visszavon', $Azonosito)
if ($Fajlt) { $args += '--fajlt' }
& python @args
