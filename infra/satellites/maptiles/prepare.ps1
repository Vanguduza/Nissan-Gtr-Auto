# Windows helper for MapLibre tiles prepare.
# Smoke (no Git Bash required):  $env:MAPTILES_SMOKE=1; powershell -File infra/satellites/maptiles/prepare.ps1
# Full Zimbabwe (Git Bash + Docker): powershell -File infra/satellites/maptiles/prepare.ps1
$ErrorActionPreference = "Stop"
$dockerBin = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin"
if (Test-Path $dockerBin) { $env:Path = "$dockerBin;$env:Path" }

$dataDir = Join-Path $PSScriptRoot "data"
$outName = if ($env:MAPTILES_MBTILES) { $env:MAPTILES_MBTILES } else { "basemap.mbtiles" }
$outFile = Join-Path $dataDir $outName
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null

$smoke = $env:MAPTILES_SMOKE
$force = $env:MAPTILES_FORCE
if ((Test-Path $outFile) -and $force -ne "1") {
  Write-Host "Using existing $outFile (set MAPTILES_FORCE=1 to rebuild)"
  exit 0
}

if ($smoke -eq "1" -or $smoke -eq "true") {
  $url = if ($env:MAPTILES_SMOKE_URL) {
    $env:MAPTILES_SMOKE_URL
  } else {
    "https://github.com/maptiler/tileserver-gl/releases/download/v1.3.0/zurich_switzerland.mbtiles"
  }
  Write-Host "Smoke: downloading sample MBTiles → $outName"
  Write-Host "URL: $url"
  Invoke-WebRequest -Uri $url -OutFile $outFile -UseBasicParsing
  Write-Host "Done. MBTiles: $outFile"
  Write-Host "Start: docker compose -f docker-compose.satellites.yml --profile maptiles up -d"
  Write-Host "Style: http://127.0.0.1:8081/styles/basic-preview/style.json"
  exit 0
}

$bash = @(
  "${env:ProgramFiles}\Git\bin\bash.exe",
  "${env:ProgramFiles(x86)}\Git\bin\bash.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $bash) { throw "Git Bash not found. Install Git for Windows or run prepare.sh from WSL." }
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
Set-Location $repoRoot
& $bash (Join-Path $PSScriptRoot "prepare.sh")
