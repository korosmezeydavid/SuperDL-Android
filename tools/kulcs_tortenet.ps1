# UGYANAZ A KULCS ALAIRTA A KORABBI KIADASOKAT IS?
#
# MIERT EZ A LEGFONTOSABB KERDES: ha egy tesztelo telefonjan olyan SuperDL
# van, amit MAS kulccsal irtak ala, akkor a frissites nem tud megtortenni.
# Az Android ilyenkor INSTALL_FAILED_UPDATE_INCOMPATIBLE hibaval all le —
# es a telepito PONTOSAN ugy viselkedik, ahogy panaszkodnak: elindul,
# azt mondja telepiti, aztan leall. Az egyetlen kiut a regi eltavolitasa.
$ErrorActionPreference = 'Continue'
chcp 65001 > $null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Start-Transcript -Path 'C:\Users\msn\Documents\SuperDL-Android\tools\_kulcs.log' -Force | Out-Null

$signer = "C:\Users\msn\AppData\Local\Android\Sdk\build-tools\37.0.0\apksigner.bat"
$repo = "korosmezeydavid/SuperDL-Android"
$temp = "$env:TEMP\superdl_kulcs"
New-Item -ItemType Directory -Force -Path $temp | Out-Null

Write-Output "--- A KIADASOK ---"
$tagek = (gh release list --repo $repo --limit 12 --json tagName --jq '.[].tagName') -split "`n" |
    Where-Object { $_ -ne '' }
$tagek | ForEach-Object { Write-Output "  $_" }
Write-Output ""

$ProgressPreference = 'SilentlyContinue'
foreach ($tag in $tagek) {
    $ki = Join-Path $temp "$tag.apk"
    Write-Output "=== $tag ==="
    try {
        gh release download $tag --repo $repo --pattern 'SuperDL.apk' --output $ki --clobber 2>&1 | Out-Null
        if (-not (Test-Path $ki)) { Write-Output "  (nincs SuperDL.apk ebben a kiadasban)"; continue }
        $sor = & $signer verify --print-certs $ki 2>&1 |
            Select-String -Pattern "certificate SHA-256 digest" | Select-Object -First 1
        $meret = (Get-Item $ki).Length
        Write-Output ("  meret: " + $meret + " bajt")
        Write-Output ("  " + $sor.ToString().Trim())
        Remove-Item $ki -Force -ErrorAction SilentlyContinue
    } catch {
        Write-Output ("  hiba: " + $_.Exception.Message)
    }
}
Write-Output ""
Write-Output "HA MINDEN SOR UGYANAZT A SHA-256 UJJLENYOMATOT MUTATJA,"
Write-Output "akkor a kulcs vegig ugyanaz volt, es a hiba NEM a kulcs korul van."
Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
Stop-Transcript | Out-Null
