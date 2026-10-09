# One-shot host ops: start FlareSolverr sidecar, wait for authorized adb device,
# set port reverses, optionally install the debug APK.
param(
    [switch]$InstallApk,
    [switch]$SkipSidecar,
    [int]$WaitSeconds = 120,
    [string]$ApkPath = ""
)

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot

function Find-Adb {
    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }
    $candidates = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { return $c }
    }
    throw "adb not found"
}

$adb = Find-Adb
Write-Host "adb: $adb"

if (-not $SkipSidecar) {
    Write-Host "Starting sidecar (FlareSolverr + agent)..."
    & (Join-Path $Root "start.ps1") -Agent
}

Write-Host "Waiting up to ${WaitSeconds}s for an authorized device..."
Write-Host ">>> Unlock the phone and tap Allow USB debugging if prompted <<<"
$deadline = (Get-Date).AddSeconds($WaitSeconds)
$ready = $false
while ((Get-Date) -lt $deadline) {
    $lines = & $adb devices | Select-Object -Skip 1
    foreach ($line in $lines) {
        if ($line -match '^\s*$') { continue }
        if ($line -match '\tdevice\s*$' -or $line -match '\tdevice\s') {
            $ready = $true
            break
        }
        if ($line -match 'unauthorized') {
            Write-Host "  still unauthorized - accept the dialog on the phone..."
        }
    }
    if ($ready) { break }
    Start-Sleep -Seconds 2
}

if (-not $ready) {
    throw "No authorized adb device within ${WaitSeconds}s. Accept USB debugging and re-run."
}

Write-Host "Device authorized. Setting adb reverse..."
& (Join-Path $Root "adb-reverse.ps1")

if ($InstallApk) {
    if (-not $ApkPath) {
        $ApkPath = Join-Path $Root "..\app\build\outputs\apk\debug\app-debug.apk"
    }
    $ApkPath = (Resolve-Path $ApkPath).Path
    if (-not (Test-Path $ApkPath)) {
        throw "APK not found: $ApkPath (run assembleDebug first)"
    }
    Write-Host "Installing $ApkPath ..."
    & $adb install -r $ApkPath
}

Write-Host ""
Write-Host "Host ops ready."
Write-Host "  FlareSolverr: http://127.0.0.1:8191/ (phone via adb reverse)"
Write-Host "  Agent:        http://127.0.0.1:8192/"
Write-Host "  App uses these automatically when Cloudflare challenges."
