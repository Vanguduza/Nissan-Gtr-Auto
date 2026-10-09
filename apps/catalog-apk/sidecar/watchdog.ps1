# Restart FlareSolverr if health fails (run while crawling overnight).
param(
    [int]$IntervalSec = 30,
    [int]$Port = 8191
)

$ErrorActionPreference = "Continue"
$Root = $PSScriptRoot
Write-Host "Watchdog every ${IntervalSec}s for http://127.0.0.1:$Port/"

while ($true) {
    $ok = $false
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/" -TimeoutSec 5 -UseBasicParsing
        $ok = $r.StatusCode -ge 200 -and $r.StatusCode -lt 300
    } catch {
        $ok = $false
    }
    if ($ok) {
        Write-Host "$(Get-Date -Format o) healthy"
    } else {
        Write-Warning "$(Get-Date -Format o) unhealthy — restarting sidecar"
        & (Join-Path $Root "start.ps1")
    }
    Start-Sleep -Seconds $IntervalSec
}
