# EP-E A KIADASBAN LEVO FAJL — bajtra azonos-e azzal, amit epitettunk
$ErrorActionPreference = 'Continue'
chcp 65001 > $null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Start-Transcript -Path 'C:\Users\msn\Documents\SuperDL-Android\tools\_epseg.log' -Force | Out-Null

$apk = "C:\Users\msn\Documents\SuperDL-Android\app\build\outputs\apk\release\SuperDL.apk"
$tmp = "$env:TEMP\superdl_letoltott_$(Get-Random).apk"

$h1 = Get-FileHash $apk -Algorithm SHA256
Write-Output "AMIT EPITETTUNK:"
Write-Output ("  SHA-256: " + $h1.Hash)
Write-Output ("  meret:   " + (Get-Item $apk).Length + " bajt")
Write-Output ""

Write-Output "AMIT A TESZTELO LETOLT (a kiadasi linkrol):"
try {
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri 'https://github.com/korosmezeydavid/SuperDL-Android/releases/latest/download/SuperDL.apk' -OutFile $tmp -UseBasicParsing
    $h2 = Get-FileHash $tmp -Algorithm SHA256
    Write-Output ("  SHA-256: " + $h2.Hash)
    Write-Output ("  meret:   " + (Get-Item $tmp).Length + " bajt")
    Write-Output ""
    if ($h1.Hash -eq $h2.Hash) {
        Write-Output "EGYEZIK. A kiadasban pontosan az a fajl van, amit epitettunk."
        Write-Output "Tehat a hiba NEM a kiadott fajlban van."
    } else {
        Write-Output "NEM EGYEZIK! A kiadasban MAS fajl van. Ez maga a hiba."
    }
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
} catch {
    Write-Output ("  letoltes hiba: " + $_.Exception.Message)
}
Stop-Transcript | Out-Null
