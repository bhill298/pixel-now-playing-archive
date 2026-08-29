[CmdletBinding()]
param(
    [string]$Serial,
    [string]$Output,
    [int]$MaxScrolls = 20000
)

$ErrorActionPreference = 'Stop'
$kitRoot = $PSScriptRoot
$platformTools = Join-Path $kitRoot '.toolchain\android-sdk\platform-tools'
if (Test-Path -LiteralPath (Join-Path $platformTools 'adb.exe')) {
    $env:PATH = "$platformTools;$env:PATH"
}

$script = Join-Path $kitRoot 'export_now_playing.py'
$arguments = @($script, '--max-scrolls', $MaxScrolls)
if ($Serial) { $arguments += @('--serial', $Serial) }
if ($Output) {
    $arguments += @('--output', $Output)
} else {
    $archiveDir = Join-Path $kitRoot 'archive'
    New-Item -ItemType Directory -Path $archiveDir -Force | Out-Null
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $arguments += @('--output', (Join-Path $archiveDir "now-playing-export-$timestamp.json"))
}

if (Get-Command py -ErrorAction SilentlyContinue) {
    & py -3 @arguments
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    & python @arguments
} else {
    throw 'Python 3 was not found. Install Python 3 and ensure py.exe or python.exe is available.'
}
if ($LASTEXITCODE -ne 0) { throw "Exporter exited with $LASTEXITCODE" }
