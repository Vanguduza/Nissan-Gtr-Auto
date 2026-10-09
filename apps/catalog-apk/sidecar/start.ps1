# Start Catalog APK FlareSolverr sidecar (Docker preferred, native Windows fallback).
param(
    [switch]$Native,
    [switch]$Agent,
    [string]$Bind = "127.0.0.1",
    [string]$Token = "catalog-apk-dev",
    [int]$AgentPort = 8192,
    [int]$Port = 8191
)

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$env:FLARESOLVERR_BIND = $Bind
$env:SIDECAR_TOKEN = $Token
$env:SIDECAR_BIND = $Bind
$env:SIDECAR_PORT = "$AgentPort"
$env:FLARESOLVERR_URL = "http://127.0.0.1:$Port"

function Test-FlareHealthy {
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/" -TimeoutSec 5 -UseBasicParsing
        return $r.StatusCode -ge 200 -and $r.StatusCode -lt 300
    } catch {
        return $false
    }
}

if (-not $Native) {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker) {
        Write-Host "Starting FlareSolverr via Docker Compose (bind ${Bind}:${Port})..."
        Push-Location $Root
        try {
            docker compose -f .\docker-compose.yml up -d --remove-orphans
        } finally {
            Pop-Location
        }
        $deadline = (Get-Date).AddSeconds(90)
        while ((Get-Date) -lt $deadline) {
            if (Test-FlareHealthy) {
                Write-Host "FlareSolverr healthy at http://127.0.0.1:$Port/"
                break
            }
            Start-Sleep -Seconds 2
        }
        if (-not (Test-FlareHealthy)) {
            Write-Warning "Compose started but health check timed out"
        }
    } else {
        Write-Warning "docker not found - falling back to native Windows binary"
        $Native = $true
    }
}

if ($Native) {
    $repoRoot = (Resolve-Path (Join-Path $Root "..\..\..")).Path
    $nativeScript = Join-Path $repoRoot "data-pipeline\scripts\start-flaresolverr-native.ps1"
    if (-not (Test-Path $nativeScript)) { throw "Native starter not found: $nativeScript" }
    & $nativeScript -Port $Port
}

if ($Agent) {
    $agentPy = Join-Path $Root "agent\agent.py"
    Write-Host "Starting control agent on http://${Bind}:${AgentPort}/ (token=$Token)"
    Start-Process -FilePath "python" -ArgumentList @("`"$agentPy`"") -WorkingDirectory (Join-Path $Root "agent") -WindowStyle Minimized
    Start-Sleep -Seconds 1
    try {
        $h = Invoke-WebRequest -Uri "http://127.0.0.1:$AgentPort/health" -TimeoutSec 5 -UseBasicParsing
        Write-Host "Agent OK: $($h.Content)"
    } catch {
        Write-Warning "Agent not responding yet: $($_.Exception.Message)"
    }
}

Write-Host ""
Write-Host "Phone / emulator tips:"
Write-Host "  Emulator: FlareSolverr at http://10.0.2.2:8191/v1"
Write-Host "  USB device: run .\adb-reverse.ps1 (phone uses http://127.0.0.1:8191/v1)"
Write-Host "  Or: .\setup-host-ops.ps1 -InstallApk"
