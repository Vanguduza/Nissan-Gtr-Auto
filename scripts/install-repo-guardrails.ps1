$ErrorActionPreference = 'Stop'
$root = (git rev-parse --show-toplevel).Trim()
Set-Location $root
git config core.hooksPath .githooks
Write-Host 'Nissan GTR repository guardrails enabled via core.hooksPath=.githooks'
