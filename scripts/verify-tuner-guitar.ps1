param([Parameter(Mandatory = $true)][string]$FixtureDirectory)

$ErrorActionPreference = 'Stop'
$fixturePath = (Resolve-Path -LiteralPath $FixtureDirectory).Path
foreach ($note in @('E2', 'A2', 'D3', 'G3', 'B3', 'E4')) {
    if (-not (Test-Path -LiteralPath (Join-Path $fixturePath "$note.wav") -PathType Leaf)) {
        throw "Missing $note.wav in $fixturePath"
    }
}
$previousFixtures = $env:TUNER_GUITAR_FIXTURES
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $env:TUNER_GUITAR_FIXTURES = $fixturePath
    & .\gradlew.bat :domain:test --rerun-tasks --tests '*GuitarPitchDetectorTest.recordedGuitarFixtures*'
    $testExit = $LASTEXITCODE
    $resultsPath = 'domain/build/test-results/test/TEST-com.pekochan069.guitarlearner.domain.GuitarPitchDetectorTest.xml'
    if (Test-Path -LiteralPath $resultsPath) {
        [xml]$results = Get-Content -LiteralPath $resultsPath -Raw
        Write-Output $results.testsuite.'system-out'.InnerText
    }
    if ($testExit -ne 0) { throw "Recorded-guitar verification failed with exit $testExit" }
} finally {
    $env:TUNER_GUITAR_FIXTURES = $previousFixtures
    Pop-Location
}
