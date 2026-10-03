$ErrorActionPreference = 'Stop'
$taskRepository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskBuildFile = [IO.Path]::GetFullPath((Join-Path $taskRepository 'ui/build.gradle.kts'))
if (-not $taskBuildFile.StartsWith($taskRepository + [IO.Path]::DirectorySeparatorChar)) {
    throw 'Boundary fixture escaped the repository'
}
$taskOriginal = [IO.File]::ReadAllText($taskBuildFile)
$taskEncoding = [Text.UTF8Encoding]::new($false)
$taskWrapper = if ($IsWindows) { Join-Path $taskRepository 'gradlew.bat' } else { Join-Path $taskRepository 'gradlew' }
function Assert-ForbiddenDependency([string] $declaration, [string] $expectedMessage) {
    [IO.File]::WriteAllText($taskBuildFile, $taskOriginal + "`ndependencies { $declaration }`n", $taskEncoding)
    $taskOutput = & $taskWrapper -p $taskRepository verifyModuleBoundaries --console=plain 2>&1
    if ($LASTEXITCODE -eq 0 -or -not ($taskOutput -join "`n").Contains($expectedMessage)) {
        throw "The forbidden declaration did not fail as expected: $declaration`n$($taskOutput -join "`n")"
    }
    Write-Output "Verified rejected dependency: $declaration"
}
try {
    Assert-ForbiddenDependency 'implementation(project(":domain"))' ':ui declares implementation(:domain)'
    Assert-ForbiddenDependency 'debugApi(project(":presentation:contract"))' ':ui declares debugApi(:presentation:contract)'
    Assert-ForbiddenDependency 'implementation(files("build.gradle.kts"))' 'Unsupported declared production dependency'
} finally {
    [IO.File]::WriteAllText($taskBuildFile, $taskOriginal, $taskEncoding)
}
