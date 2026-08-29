[CmdletBinding()]
param(
    [string]$Serial,
    [string]$Apk,
    [string]$Json,
    [switch]$SkipJsonCopy
)

$ErrorActionPreference = 'Stop'
$kitRoot = $PSScriptRoot
$portableAdb = Join-Path $kitRoot '.toolchain\android-sdk\platform-tools\adb.exe'
$adb = if (Test-Path -LiteralPath $portableAdb) { $portableAdb } else { 'adb' }

if (-not $Serial) {
    $devices = @(& $adb devices | Where-Object { $_ -match '^(\S+)\s+device$' } |
        ForEach-Object { $Matches[1] })
    if ($devices.Count -ne 1) {
        & $adb devices -l
        throw 'Attach exactly one authorized destination device, or pass -Serial.'
    }
    $Serial = $devices[0]
}

if (-not $Apk) {
    $stable = Join-Path $kitRoot 'releases\NowPlayingArchive-release.apk'
    if (Test-Path -LiteralPath $stable) {
        $Apk = $stable
    } else {
        $Apk = (Get-ChildItem -LiteralPath (Join-Path $kitRoot 'releases') -Filter '*-release.apk' -File |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
    }
}
if (-not $Apk -or -not (Test-Path -LiteralPath $Apk)) {
    throw 'No release APK found. Run .\build.ps1 -Variant Release or pass -Apk.'
}

& $adb -s $Serial install -r $Apk
if ($LASTEXITCODE -ne 0) { throw "ADB install exited with $LASTEXITCODE" }

if (-not $SkipJsonCopy) {
    if (-not $Json) {
        $latestJson = Get-ChildItem -LiteralPath (Join-Path $kitRoot 'archive') -Filter '*.json' -File |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($latestJson) { $Json = $latestJson.FullName }
    }
    if ($Json -and (Test-Path -LiteralPath $Json)) {
        & $adb -s $Serial shell mkdir -p /sdcard/Download/NowPlayingArchive
        & $adb -s $Serial push $Json /sdcard/Download/NowPlayingArchive/
        if ($LASTEXITCODE -ne 0) { throw "ADB push exited with $LASTEXITCODE" }
    }
}

& $adb -s $Serial shell monkey -p com.brennan.nowplayingarchive -c android.intent.category.LAUNCHER 1 | Out-Host
Write-Host 'Installed and launched Now Playing Archive.'
