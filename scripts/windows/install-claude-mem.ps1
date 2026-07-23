#Requires -Version 5.1
<#
.SYNOPSIS
  Clone/build claude-mem and launch Cursor setup wizard (user-level preferred).
#>

$ErrorActionPreference = "Stop"

$env:Path = "C:\Program Files\nodejs;$env:USERPROFILE\.bun\bin;" +
  [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
  [System.Environment]::GetEnvironmentVariable("Path", "User")

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
  Write-Error "Node.js required. Install LTS from https://nodejs.org then re-run."
}

if (-not (Get-Command bun -ErrorAction SilentlyContinue)) {
  Write-Host "Installing Bun..." -ForegroundColor Cyan
  powershell -NoProfile -ExecutionPolicy Bypass -Command "irm bun.sh/install.ps1 | iex"
  $env:Path = "$env:USERPROFILE\.bun\bin;$env:Path"
}

if (-not (Get-Command bun -ErrorAction SilentlyContinue)) {
  Write-Error "Bun not found after install. Open a new PowerShell and re-run this script."
}

$dest = Join-Path $env:USERPROFILE "src\claude-mem"
if (-not (Test-Path $dest)) {
  New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
  git clone https://github.com/thedotmack/claude-mem.git $dest
}

Set-Location $dest
Write-Host "Installing dependencies..." -ForegroundColor Cyan
bun install
bun run build

Write-Host ""
Write-Host "Starting Cursor setup wizard..." -ForegroundColor Cyan
Write-Host "Prefer USER-level hooks so this ERP repo keeps its own guard-prod hooks." -ForegroundColor Yellow
Write-Host "Recommended provider for Cursor-only: Gemini free tier." -ForegroundColor Yellow
Write-Host ""

bun run cursor:setup

Write-Host ""
Write-Host "Next: restart Cursor, then confirm worker status from $dest" -ForegroundColor Green
Write-Host "  bun run worker:status"
Write-Host "Docs: docs/TOOLING_SETUP.md in the Nissan GTR repo"
