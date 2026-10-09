# Stop Catalog APK FlareSolverr sidecar (+ optional control agent).
param(
    [switch]$Agent
)

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot

Push-Location $Root
try {
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        docker compose -f .\docker-compose.yml stop
        Write-Host "Docker FlareSolverr stopped"
    }
} finally {
    Pop-Location
}

Get-CimInstance Win32_Process -Filter "Name = 'flaresolverr.exe'" -ErrorAction SilentlyContinue |
    ForEach-Object {
        Write-Host "Stopping native flaresolverr.exe PID $($_.ProcessId)"
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }

if ($Agent) {
    Get-CimInstance Win32_Process -Filter "Name = 'python.exe' OR Name = 'pythonw.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'sidecar\\agent\\agent\.py|sidecar/agent/agent\.py' } |
        ForEach-Object {
            Write-Host "Stopping agent PID $($_.ProcessId)"
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        }
}

Write-Host "Done"
