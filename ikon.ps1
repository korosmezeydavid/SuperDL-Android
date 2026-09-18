Add-Type -AssemblyName System.Drawing
$res = "C:\Users\msn\Documents\SuperDL-Android\app\src\main\res"

function New-Icon([int]$size, [string]$path, [bool]$round) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.Clear([System.Drawing.Color]::Transparent)

    $bg = [System.Drawing.Color]::FromArgb(255, 26, 26, 46)
    $brushBg = New-Object System.Drawing.SolidBrush($bg)

    if ($round) {
        $g.FillEllipse($brushBg, 0, 0, ($size - 1), ($size - 1))
    } else {
        $r = [int]($size * 0.22)
        $gp = New-Object System.Drawing.Drawing2D.GraphicsPath
        $d = $r * 2
        $gp.AddArc(0, 0, $d, $d, 180, 90)
        $gp.AddArc(($size - 1 - $d), 0, $d, $d, 270, 90)
        $gp.AddArc(($size - 1 - $d), ($size - 1 - $d), $d, $d, 0, 90)
        $gp.AddArc(0, ($size - 1 - $d), $d, $d, 90, 90)
        $gp.CloseFigure()
        $g.FillPath($brushBg, $gp)
        $gp.Dispose()
    }

    $gold = [System.Drawing.Color]::FromArgb(255, 245, 179, 1)
    $penArc = New-Object System.Drawing.Pen($gold, [float]($size * 0.05))
    $penArc.StartCap = 'Round'
    $penArc.EndCap = 'Round'
    $m = $size * 0.5
    $cx = $size * 0.38
    foreach ($f in @(0.30, 0.52, 0.74)) {
        $rr = $size * $f
        $g.DrawArc($penArc, ($cx - $rr / 2), ($m - $rr / 2), $rr, $rr, -52, 104)
    }

    $dotR = $size * 0.13
    $brushDot = New-Object System.Drawing.SolidBrush($gold)
    $g.FillEllipse($brushDot, ($cx - $dotR / 2 - $size * 0.10), ($m - $dotR / 2), $dotR, $dotR)

    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    "{0}  ({1}x{1})" -f $path, $size
}

$sizes = @{ "mdpi" = 48; "hdpi" = 72; "xhdpi" = 96; "xxhdpi" = 144; "xxxhdpi" = 192 }
foreach ($k in $sizes.Keys) {
    $dir = Join-Path $res ("mipmap-" + $k)
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    New-Icon $sizes[$k] (Join-Path $dir "ic_launcher.png") $false
    New-Icon $sizes[$k] (Join-Path $dir "ic_launcher_round.png") $true
}
