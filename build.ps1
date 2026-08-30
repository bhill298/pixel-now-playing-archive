[CmdletBinding()]
param(
    [ValidateSet('Debug', 'Release')]
    [string]$Variant = 'Release'
)

$ErrorActionPreference = 'Stop'
$kitRoot = $PSScriptRoot
$toolchainRoot = Join-Path $kitRoot '.toolchain'
$jdkRoot = Get-ChildItem -LiteralPath (Join-Path $toolchainRoot 'jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
$gradleRoot = Get-ChildItem -LiteralPath (Join-Path $toolchainRoot 'gradle') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
$sdkRoot = Join-Path $toolchainRoot 'android-sdk'

if (-not $jdkRoot -or -not $gradleRoot -or -not (Test-Path -LiteralPath $sdkRoot)) {
    throw 'Portable build tools are missing. Run .\setup-toolchain.ps1 first.'
}

$env:JAVA_HOME = $jdkRoot.FullName
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $toolchainRoot 'android-user'
$env:GRADLE_USER_HOME = Join-Path $toolchainRoot 'gradle-home'
New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME,$env:GRADLE_USER_HOME -Force | Out-Null
$gradle = Join-Path $gradleRoot.FullName 'bin\gradle.bat'

if ($Variant -eq 'Release') {
    & (Join-Path $kitRoot 'setup-signing.ps1')
    if ($LASTEXITCODE -ne 0) { throw "Signing setup exited with $LASTEXITCODE" }
}

Push-Location $kitRoot
try {
    & $gradle ("assemble$Variant")
    if ($LASTEXITCODE -ne 0) { throw "Gradle exited with $LASTEXITCODE" }
} finally {
    Pop-Location
}

$variantLower = $Variant.ToLowerInvariant()
$sourceApk = Join-Path $kitRoot "app\build\outputs\apk\$variantLower\app-$variantLower.apk"
$releaseDir = Join-Path $kitRoot 'releases'
New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
$friendlyApk = Join-Path $releaseDir "NowPlayingArchive-$variantLower.apk"
Copy-Item -LiteralPath $sourceApk -Destination $friendlyApk -Force
Write-Host "APK: $friendlyApk"
