# Run FlareSolverr natively on Windows (no Docker) — same port as compose satellite.
# Official binary: https://github.com/FlareSolverr/FlareSolverr/releases
param(
    [string]$InstallRoot = (Join-Path (Split-Path $PSScriptRoot -Parent | Split-Path -Parent) "tools\flaresolverr"),
    [string]$Version = "v3.5.0",
    [int]$Port = 8191
)

$ErrorActionPreference = "Stop"
$zipName = "flaresolverr_windows_x64.zip"
$zipPath = Join-Path $InstallRoot $zipName
$exe = Get-ChildItem -Path $InstallRoot -Recurse -Filter "flaresolverr.exe" -ErrorAction SilentlyContinue | Select-Object -First 1

New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null

if (-not $exe) {
    $url = "https://github.com/FlareSolverr/FlareSolverr/releases/download/$Version/$zipName"
    Write-Host "Downloading FlareSolverr $Version (~311 MB)..."
    Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing
    Write-Host "Extracting to $InstallRoot ..."
    Expand-Archive -Path $zipPath -DestinationPath $InstallRoot -Force
    $exe = Get-ChildItem -Path $InstallRoot -Recurse -Filter "flaresolverr.exe" | Select-Object -First 1
    if (-not $exe) { throw "flaresolverr.exe not found after extract" }
}

$existing = Get-CimInstance Win32_Process -Filter "Name = 'flaresolverr.exe'" -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "FlareSolverr already running PID(s): $($existing.ProcessId -join ', ')"
    exit 0
}

$env:LOG_LEVEL = "info"
$env:HOST = "127.0.0.1"
$env:PORT = "$Port"
Write-Host "Starting $($exe.FullName) on http://${env:HOST}:${env:PORT}/"
Start-Process -FilePath $exe.FullName -WorkingDirectory $exe.DirectoryName -WindowStyle Hidden
Start-Sleep -Seconds 5
try {
    $r = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/" -TimeoutSec 10 -UseBasicParsing
    Write-Host "FlareSolverr OK: HTTP $($r.StatusCode)"
} catch {
    Write-Warning "FlareSolverr not responding yet: $($_.Exception.Message)"
}
