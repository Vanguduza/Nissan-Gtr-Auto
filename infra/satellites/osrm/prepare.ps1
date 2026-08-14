# Windows helper for H6 OSRM prepare (calls Git Bash script).
# Requires: Docker Desktop running, Git for Windows.
$ErrorActionPreference = "Stop"
$dockerBin = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin"
if (Test-Path $dockerBin) { $env:Path = "$dockerBin;$env:Path" }
$bash = @(
  "${env:ProgramFiles}\Git\bin\bash.exe",
  "${env:ProgramFiles(x86)}\Git\bin\bash.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $bash) { throw "Git Bash not found. Install Git for Windows or run prepare.sh from WSL." }
$root = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
# Script lives at infra/satellites/osrm/prepare.ps1 → repo root is three levels up... wait
# Actually: infra/satellites/osrm/prepare.ps1 → parent=osrm, satellites, infra → need repo root = Join-Path $PSScriptRoot ..\..\..
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
Set-Location $repoRoot
& $bash (Join-Path $PSScriptRoot "prepare.sh")
