param([switch]$Check)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$repoRoot = Split-Path -Parent $PSScriptRoot
$iconSpec = @{
    Source = Join-Path $repoRoot 'artwork/launcher/guitar-learner-icon.png'
    LayerDp = 108
    ContentDp = 48
    OutputPx = 432
    SafeRadiusDp = 33
}
$output = Join-Path $repoRoot 'app/src/main/res/drawable-nodpi/ic_launcher_foreground.png'
$temporary = Join-Path ([IO.Path]::GetTempPath()) ('guitarlearner-launcher-' + [Guid]::NewGuid().ToString('N') + '.png')
$contentPx = [int]($iconSpec.ContentDp * $iconSpec.OutputPx / $iconSpec.LayerDp)

try {
    magick $iconSpec.Source -alpha on -background none -bordercolor none -border 1 -trim +repage `
        -filter Lanczos -resize "${contentPx}x${contentPx}" -gravity center `
        -extent "$($iconSpec.OutputPx)x$($iconSpec.OutputPx)" -depth 8 -strip "PNG32:$temporary"
    if ($LASTEXITCODE -ne 0) { throw "ImageMagick generation failed with exit code $LASTEXITCODE." }

    $normalizedRadius = magick $temporary -alpha extract `
        -fx 'u>0 ? sqrt((i-(w-1)/2)^2+(j-(h-1)/2)^2)/w : 0' -format '%[fx:maxima]' info:
    if ($LASTEXITCODE -ne 0) { throw "ImageMagick alpha measurement failed with exit code $LASTEXITCODE." }
    $maxRadiusDp = [double]::Parse($normalizedRadius, [Globalization.CultureInfo]::InvariantCulture) * $iconSpec.LayerDp +
        0.707107 * $iconSpec.LayerDp / $iconSpec.OutputPx
    if ($maxRadiusDp -gt $iconSpec.SafeRadiusDp) { throw "Launcher icon alpha exceeds the $($iconSpec.SafeRadiusDp)dp safe circle." }

    if ($Check) {
        magick compare $output $temporary null:
        switch ($LASTEXITCODE) {
            0 { Write-Output 'Launcher icon matches regenerated pixels.' }
            1 { throw 'Launcher icon differs. Run scripts/generate-launcher-icon.ps1 to regenerate it.' }
            default { throw "ImageMagick comparison failed with exit code $LASTEXITCODE." }
        }
    } else {
        New-Item -ItemType Directory -Path (Split-Path -Parent $output) -Force | Out-Null
        Copy-Item -LiteralPath $temporary -Destination $output -Force
        Write-Output 'Generated app/src/main/res/drawable-nodpi/ic_launcher_foreground.png.'
    }
    Write-Output ('Maximum alpha pixel-corner radius is {0:F2}dp.' -f $maxRadiusDp)
} finally {
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
}
