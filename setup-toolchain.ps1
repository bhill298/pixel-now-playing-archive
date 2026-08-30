[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$kitRoot = $PSScriptRoot
$toolchainRoot = Join-Path $kitRoot '.toolchain'
$downloads = Join-Path $toolchainRoot 'downloads'
$jdkContainer = Join-Path $toolchainRoot 'jdk'
$gradleContainer = Join-Path $toolchainRoot 'gradle'
$sdkRoot = Join-Path $toolchainRoot 'android-sdk'
$cliExtract = Join-Path $toolchainRoot 'android-cli'

New-Item -ItemType Directory -Path $downloads,$jdkContainer,$gradleContainer,$sdkRoot,$cliExtract -Force | Out-Null

function Get-VerifiedDownload {
    param(
        [Parameter(Mandatory)] [string]$Url,
        [Parameter(Mandatory)] [string]$Destination,
        [Parameter(Mandatory)] [string]$Sha256
    )
    if (-not (Test-Path -LiteralPath $Destination)) {
        Write-Host "Downloading $Url"
        Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
    }
    $actual = (Get-FileHash -LiteralPath $Destination -Algorithm SHA256).Hash
    if ($actual -ne $Sha256) {
        throw "Checksum mismatch for $Destination. Expected $Sha256; got $actual"
    }
}

$jdkZip = Join-Path $downloads 'microsoft-jdk-17.0.20.1-windows-x64.zip'
$gradleZip = Join-Path $downloads 'gradle-9.4.1-bin.zip'
$androidZip = Join-Path $downloads 'commandlinetools-win-15859902_latest.zip'

Get-VerifiedDownload `
    -Url 'https://aka.ms/download-jdk/microsoft-jdk-17.0.20.1-windows-x64.zip' `
    -Destination $jdkZip `
    -Sha256 '3D9006956FC8AF5601CD24FFC4F468BEF48279C7EBD8171B9BDF90D0AABFBF1F'
Get-VerifiedDownload `
    -Url 'https://services.gradle.org/distributions/gradle-9.4.1-bin.zip' `
    -Destination $gradleZip `
    -Sha256 '2AB2958F2A1E51120C326CAD6F385153BB11EE93B3C216C5FCCEBFDFBB7EC6CB'
Get-VerifiedDownload `
    -Url 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' `
    -Destination $androidZip `
    -Sha256 '90AE805D20434428BFFCB699C290860F19BB5F66A67E6B330067E3DE801FB04A'

if (-not (Get-ChildItem -LiteralPath $jdkContainer -Directory -ErrorAction SilentlyContinue)) {
    Expand-Archive -LiteralPath $jdkZip -DestinationPath $jdkContainer
}
if (-not (Test-Path -LiteralPath (Join-Path $gradleContainer 'gradle-9.4.1\bin\gradle.bat'))) {
    Expand-Archive -LiteralPath $gradleZip -DestinationPath $gradleContainer
}

$androidCli = Join-Path $sdkRoot 'cmdline-tools\latest\bin\android.exe'
if (-not (Test-Path -LiteralPath $androidCli)) {
    Expand-Archive -LiteralPath $androidZip -DestinationPath $cliExtract
    $latest = Join-Path $sdkRoot 'cmdline-tools\latest'
    New-Item -ItemType Directory -Path $latest -Force | Out-Null
    Copy-Item -Path (Join-Path $cliExtract 'cmdline-tools\*') -Destination $latest -Recurse -Force
}

$jdkRoot = Get-ChildItem -LiteralPath $jdkContainer -Directory | Select-Object -First 1
$env:JAVA_HOME = $jdkRoot.FullName
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $toolchainRoot 'android-user'
New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME -Force | Out-Null

Write-Host 'Installing Android platform and build tools...'
& $androidCli "--sdk=$sdkRoot" sdk install `
    'platform-tools' 'platforms/android-37.0' 'build-tools/36.0.0'
if ($LASTEXITCODE -ne 0) { throw "android sdk install exited with $LASTEXITCODE" }

Write-Host ''
Write-Host 'Toolchain ready. Run .\build.ps1 -Variant Release'
