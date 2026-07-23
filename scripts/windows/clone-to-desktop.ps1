#Requires -Version 5.1
<#
.SYNOPSIS
  Clone or update Nissan-Gtr-Auto into C:\Users\j\Desktop\nissan gtr
#>

$ErrorActionPreference = "Stop"

$Dest = "C:\Users\j\Desktop\nissan gtr"
$RepoUrl = "https://github.com/Vanguduza/Nissan-Gtr-Auto.git"
$Branch = "cursor/erp-cursor-setup-ad25"

Write-Host "=== Nissan GTR Auto — clone to Desktop ===" -ForegroundColor Cyan
Write-Host "Destination: $Dest"
Write-Host "Branch:      $Branch"
Write-Host ""

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
  Write-Error "git is not installed or not on PATH. Install Git for Windows first: https://git-scm.com/download/win"
}

if (Test-Path (Join-Path $Dest ".git")) {
  Write-Host "Git repo already present. Fetching and updating..." -ForegroundColor Yellow
  Set-Location $Dest
  git fetch origin
  git checkout $Branch
  git pull origin $Branch
  Write-Host "Updated." -ForegroundColor Green
} elseif (Test-Path $Dest) {
  $items = Get-ChildItem $Dest -Force -ErrorAction SilentlyContinue
  if ($items) {
    Write-Host "Folder exists and is not empty / not a git repo." -ForegroundColor Yellow
    Write-Host "Cloning into temporary folder then copying files..."
    $tmp = Join-Path $env:TEMP "nissan-gtr-auto-clone-$(Get-Random)"
    git clone -b $Branch $RepoUrl $tmp
    Copy-Item -Path (Join-Path $tmp "*") -Destination $Dest -Recurse -Force
    # Also copy hidden .git etc.
    Get-ChildItem $tmp -Force | ForEach-Object {
      Copy-Item $_.FullName -Destination $Dest -Recurse -Force
    }
    Remove-Item $tmp -Recurse -Force
    Set-Location $Dest
    Write-Host "Copied into existing folder." -ForegroundColor Green
  } else {
    git clone -b $Branch $RepoUrl $Dest
    Set-Location $Dest
    Write-Host "Cloned into empty folder." -ForegroundColor Green
  }
} else {
  New-Item -ItemType Directory -Path (Split-Path $Dest -Parent) -Force | Out-Null
  git clone -b $Branch $RepoUrl $Dest
  Set-Location $Dest
  Write-Host "Cloned." -ForegroundColor Green
}

Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Open Cursor Desktop → File → Open Folder → $Dest"
Write-Host "  2. Start real-time sync:"
Write-Host "       cd `"$Dest`""
Write-Host "       .\scripts\windows\auto-sync.ps1"
Write-Host ""
