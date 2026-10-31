param(
    [string]$ArtworkPath = (Join-Path $PSScriptRoot 'generated-artwork.png'),
    [string]$OutputDirectory = $PSScriptRoot
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$layout = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'layout.json') -Raw | ConvertFrom-Json
$source = [System.Drawing.Bitmap]::new($ArtworkPath)
$panel = [System.Drawing.Bitmap]::new($layout.canvas.width, $layout.canvas.height)

# Measured artwork boundaries mapped onto the actual rendered vanilla GUI.
# The generated image has exterior padding and irregular grid spacing.
$sourceX = @(0, 44, 141, 237, 334, 430, 526, 623, 719, 816, 912, 952)
$targetX = @(0, 7, 25, 43, 61, 79, 97, 115, 133, 151, 169, 176)
$sourceY = @(150, 260, 361, 463, 563, 665, 766, 867, 1000, 1102, 1204, 1298, 1344, 1440, 1478)
$targetY = @(0, 17, 35, 53, 71, 89, 107, 125, 138, 156, 174, 192, 196, 214, 222)

function Get-SamplingCoordinates([int[]]$From, [int[]]$To) {
    $segment = 0
    for ($pixel = 0; $pixel -lt $To[-1]; $pixel++) {
        while ($pixel -ge $To[$segment + 1]) { $segment++ }
        $fraction = ($pixel + 0.5 - $To[$segment]) / ($To[$segment + 1] - $To[$segment])
        [int][math]::Floor($From[$segment] + $fraction * ($From[$segment + 1] - $From[$segment]))
    }
}

function Fill-Pixels([int]$X, [int]$Y, [int]$Width, [int]$Height, [string]$Hex) {
    $color = [System.Drawing.ColorTranslator]::FromHtml($Hex)
    for ($py = $Y; $py -lt $Y + $Height; $py++) {
        for ($px = $X; $px -lt $X + $Width; $px++) {
            $panel.SetPixel($px, $py, $color)
        }
    }
}

# Small bitmap glyphs keep the labels legible at Minecraft's native GUI scale.
# A case-sensitive dictionary is needed for E/e and P/p.
$glyphs = [System.Collections.Generic.Dictionary[string,string]]::new([System.StringComparer]::Ordinal)
$glyphs.Add('E', '11111/10000/11110/10000/10000/10000/11111/00000')
$glyphs.Add('s', '00000/00000/01111/10000/01110/00001/11110/00000')
$glyphs.Add('c', '00000/00000/01110/10001/10000/10001/01110/00000')
$glyphs.Add('a', '00000/00000/01110/00001/01111/10001/01111/00000')
$glyphs.Add('p', '00000/00000/11110/10001/10001/11110/10000/10000')
$glyphs.Add('e', '00000/00000/01110/10001/11111/10000/01111/00000')
$glyphs.Add('P', '11110/10001/11110/10000/10000/10000/10000/00000')
$glyphs.Add('o', '00000/00000/01110/10001/10001/10001/01110/00000')
$glyphs.Add('d', '00001/00001/01101/10011/10001/10001/01111/00000')
$glyphs.Add('I', '111/010/010/010/010/010/111/000')
$glyphs.Add('n', '00000/00000/11110/10001/10001/10001/10001/00000')
$glyphs.Add('v', '00000/00000/10001/10001/10001/01010/00100/00000')
$glyphs.Add('t', '010/010/111/010/010/010/001/000')
$glyphs.Add('r', '00000/00000/10110/11001/10000/10000/10000/00000')
$glyphs.Add('y', '00000/00000/10001/10001/10001/01111/00001/11110')

function Draw-PixelText([string]$Text, [int]$X, [int]$Y, [string]$Hex) {
    $color = [System.Drawing.ColorTranslator]::FromHtml($Hex)
    $cursor = $X
    foreach ($letter in $Text.ToCharArray()) {
        if ($letter -eq ' ') { $cursor += 4; continue }
        $rows = $glyphs[[string]$letter].Split('/')
        for ($gy = 0; $gy -lt $rows.Length; $gy++) {
            for ($gx = 0; $gx -lt $rows[$gy].Length; $gx++) {
                if ($rows[$gy][$gx] -eq '1') { $panel.SetPixel($cursor + $gx, $Y + $gy, $color) }
            }
        }
        $cursor += $rows[0].Length + 1
    }
}

try {
    if ($source.Width -ne 952 -or $source.Height -ne 1652) {
        throw 'This alignment map requires the original 952 x 1652 generated artwork.'
    }
    $sampleX = @(Get-SamplingCoordinates $sourceX $targetX)
    $sampleY = @(Get-SamplingCoordinates $sourceY $targetY)
    for ($y = 0; $y -lt $panel.Height; $y++) {
        for ($x = 0; $x -lt $panel.Width; $x++) {
            $sample = $source.GetPixel($sampleX[$x], $sampleY[$y])
            # Binary alpha avoids a blurred fringe around the stepped frame.
            if ($sample.A -ge 128) {
                $panel.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $sample.R, $sample.G, $sample.B))
            }
        }
    }

    # Reconstruct the slot wells at exact coordinates: one-pixel bevel and
    # a clear 16 x 16 interior. No generated spacing survives inside the grids.
    $itemRows = @($layout.chestItemRows) + @($layout.playerItemRows) + @($layout.hotbarItemRow)
    foreach ($itemY in $itemRows) {
        foreach ($itemX in $layout.itemColumns) {
            Fill-Pixels ($itemX - 1) ($itemY - 1) 18 18 '#526675'
            Fill-Pixels ($itemX - 1) ($itemY - 1) 18 1 '#0B151E'
            Fill-Pixels ($itemX - 1) ($itemY - 1) 1 18 '#0B151E'
            Fill-Pixels $itemX $itemY 16 16 '#263740'
        }
    }

    # Replace resampled lettering with crisp native-size glyphs.
    Fill-Pixels 6 4 75 11 '#1C2B35'
    Fill-Pixels 7 14 73 1 '#304B58'
    Fill-Pixels 6 126 63 11 '#90A6AF'
    Fill-Pixels 7 136 61 1 '#496775'
    $panel.Save((Join-Path $OutputDirectory 'escape-pods-unlabelled.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    Draw-PixelText $layout.title.text $layout.title.x $layout.title.y '#A7F5FC'
    Draw-PixelText $layout.inventoryLabel.text $layout.inventoryLabel.x $layout.inventoryLabel.y '#263740'
    $panel.Save((Join-Path $OutputDirectory 'escape-pods.png'), [System.Drawing.Imaging.ImageFormat]::Png)

    # Integer nearest-neighbour scaling: each source pixel becomes exactly 4x4.
    $preview = [System.Drawing.Bitmap]::new($panel.Width * 4, $panel.Height * 4)
    $graphics = [System.Drawing.Graphics]::FromImage($preview)
    try {
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.DrawImage($panel, [System.Drawing.Rectangle]::new(0, 0, $preview.Width, $preview.Height), 0, 0, $panel.Width, $panel.Height, [System.Drawing.GraphicsUnit]::Pixel)
        $preview.Save((Join-Path $OutputDirectory 'escape-pods-4x.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $preview.Dispose()
    }
} finally {
    $source.Dispose()
    $panel.Dispose()
}

Write-Output 'Saved 176x222 overlay, unlabelled variant, and 704x888 preview.'
