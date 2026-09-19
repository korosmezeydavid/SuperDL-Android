function Vizsgal([string]$path) {
    "=== " + $path + " ==="
    $b = [System.IO.File]::ReadAllBytes($path)
    "meret: " + $b.Length + " bajt"

    # Elso 64 bajt hexaban
    $hex = ($b[0..63] | ForEach-Object { "{0:X2}" -f $_ }) -join " "
    "elso 64 bajt:"
    $hex

    # Nulla bajtok aranya es egyedi bajtertekek szama -> tomoritett/zajos-e
    $zeros = ($b | Where-Object { $_ -eq 0 }).Count
    "nulla bajtok: {0} ({1:N1}%)" -f $zeros, (100.0 * $zeros / $b.Length)
    $uniq = ($b | Sort-Object -Unique).Count
    "kulonbozo bajtertekek: $uniq / 256"

    # Olvashato ASCII szovegek
    $txt = [System.Text.Encoding]::ASCII.GetString($b)
    $str = [regex]::Matches($txt, "[ -~]{4,}") | ForEach-Object { $_.Value }
    "olvashato szovegek szama: " + $str.Count
    if ($str.Count -gt 0) { $str | Select-Object -First 25 }
    ""
}

Vizsgal "C:\Users\msn\Downloads\Addonbrailab\addon\BINAHANK.BIN"
Vizsgal "C:\Users\msn\Downloads\Addonbrailab\addon\BINADATA.BIN"
