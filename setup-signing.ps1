[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$kitRoot = $PSScriptRoot
$jdkRoot = Get-ChildItem -LiteralPath (Join-Path $kitRoot '.toolchain\jdk') `
    -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jdkRoot) {
    throw 'Portable JDK is missing. Run .\setup-toolchain.ps1 first.'
}

$keytool = Join-Path $jdkRoot.FullName 'bin\keytool.exe'
$signingDir = Join-Path $kitRoot 'signing'
$keystore = Join-Path $signingDir 'now-playing-archive-release.jks'
$properties = Join-Path $kitRoot 'keystore.properties'

if ((Test-Path -LiteralPath $keystore) -and (Test-Path -LiteralPath $properties)) {
    Write-Host 'Using the existing local release signing key.'
    exit 0
}
if ((Test-Path -LiteralPath $keystore) -or (Test-Path -LiteralPath $properties)) {
    throw 'Signing setup is incomplete. Restore both signing/now-playing-archive-release.jks and keystore.properties, or move the remaining file aside and run this script again.'
}

New-Item -ItemType Directory -Path $signingDir -Force | Out-Null
$passwordBytes = New-Object byte[] 24
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $random.GetBytes($passwordBytes)
} finally {
    $random.Dispose()
}
$password = -join ($passwordBytes | ForEach-Object { $_.ToString('x2') })

& $keytool -genkeypair -v `
    -keystore $keystore `
    -storepass $password `
    -alias 'now-playing-archive' `
    -keypass $password `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -dname 'CN=Now Playing Archive, OU=Personal Apps, O=Local Build'
if ($LASTEXITCODE -ne 0) { throw "keytool exited with $LASTEXITCODE" }

$lines = @(
    'storeFile=signing/now-playing-archive-release.jks'
    "storePassword=$password"
    'keyAlias=now-playing-archive'
    "keyPassword=$password"
)
[IO.File]::WriteAllLines($properties, $lines, [Text.Encoding]::ASCII)

Write-Host 'Created a local release signing key and keystore.properties.'
Write-Host 'Both are ignored by Git. Back them up together to preserve update compatibility.'
