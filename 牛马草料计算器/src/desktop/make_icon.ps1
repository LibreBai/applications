Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap 256,256
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias

$bg = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(18,24,38))
$g.FillRectangle($bg,0,0,256,256)
$pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(52,211,153)),12
$g.DrawRectangle($pen,6,6,244,244)

$f = New-Object System.Drawing.Font ('Microsoft YaHei',150,[System.Drawing.FontStyle]::Bold)
$b = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(52,211,153))
$fmt = New-Object System.Drawing.StringFormat
$fmt.Alignment = [System.Drawing.StringAlignment]::Center
$fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
$g.DrawString([char]0xFFE5,$f,$b,(New-Object System.Drawing.RectangleF 0,0,256,256),$fmt)

$ico = [System.Drawing.Icon]::FromHandle($bmp.GetHicon())
$fs = [System.IO.File]::Create((Join-Path $PSScriptRoot 'icon.ico'))
$ico.Save($fs)
$fs.Close()
$g.Dispose()
Write-Host 'icon.ico OK'
