#Requires -Version 5.1
<#
.SYNOPSIS
  Watch the repo and auto git add / commit / push on file changes (real-time sync to GitHub).

.PARAMETER PollSeconds
  If > 0, poll on an interval instead of using a FileSystemWatcher (more reliable behind some AV).

.PARAMETER DebounceSeconds
  Wait this long after the last change before committing (default 3).

.PARAMETER Remote
  Git remote name (default origin).
#>

param(
  [int]$PollSeconds = 0,
  [int]$DebounceSeconds = 3,
  [string]$Remote = "origin"
)

$ErrorActionPreference = "Stop"

# Resolve repo root (script lives in scripts/windows/)
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $RepoRoot

if (-not (Test-Path (Join-Path $RepoRoot ".git"))) {
  Write-Error "Not a git repository: $RepoRoot"
}

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
Write-Host "=== Nissan GTR Auto — real-time auto-sync ===" -ForegroundColor Cyan
Write-Host "Repo:    $RepoRoot"
Write-Host "Branch:  $branch"
Write-Host "Remote:  $Remote"
if ($PollSeconds -gt 0) {
  Write-Host "Mode:    poll every ${PollSeconds}s"
} else {
  Write-Host "Mode:    FileSystemWatcher (debounce ${DebounceSeconds}s)"
}
Write-Host "Press Ctrl+C to stop."
Write-Host ""

$script:pending = $false
$script:lastChange = Get-Date

function Sync-Now {
  Set-Location $RepoRoot
  $status = git status --porcelain
  if (-not $status) {
    return
  }

  $stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
  $msg = "auto-sync: $stamp"

  Write-Host "[$stamp] Changes detected — committing and pushing..." -ForegroundColor Yellow
  git add -A
  # Only commit if staged changes exist
  $staged = git diff --cached --name-only
  if (-not $staged) {
    Write-Host "[$stamp] Nothing staged; skipping." -ForegroundColor DarkGray
    return
  }

  git commit -m $msg
  $branchNow = (git rev-parse --abbrev-ref HEAD).Trim()
  git push -u $Remote $branchNow
  if ($LASTEXITCODE -eq 0) {
    Write-Host "[$stamp] Pushed to $Remote/$branchNow" -ForegroundColor Green
  } else {
    Write-Host "[$stamp] Push failed (check GitHub auth / network)." -ForegroundColor Red
  }
}

function Mark-Pending {
  $script:pending = $true
  $script:lastChange = Get-Date
}

# Exclude noisy paths from triggering (watcher still fires; Sync-Now uses git status)
$excludeDirs = @(".git", "node_modules", ".next", "dist", "build", ".turbo", "coverage")

if ($PollSeconds -gt 0) {
  while ($true) {
    try { Sync-Now } catch { Write-Host $_.Exception.Message -ForegroundColor Red }
    Start-Sleep -Seconds $PollSeconds
  }
} else {
  $watcher = New-Object System.IO.FileSystemWatcher
  $watcher.Path = $RepoRoot
  $watcher.IncludeSubdirectories = $true
  $watcher.EnableRaisingEvents = $true
  $watcher.NotifyFilter = [IO.NotifyFilters]"FileName, DirectoryName, LastWrite, Size"

  $handler = {
    $path = $Event.SourceEventArgs.FullPath
    $skip = $false
    foreach ($d in $excludeDirs) {
      if ($path -match [regex]::Escape([IO.Path]::DirectorySeparatorChar + $d + [IO.Path]::DirectorySeparatorChar) -or
          $path -match [regex]::Escape([IO.Path]::DirectorySeparatorChar + $d + "$")) {
        $skip = $true
        break
      }
    }
    if (-not $skip) { Mark-Pending }
  }

  Register-ObjectEvent $watcher "Changed" -Action $handler | Out-Null
  Register-ObjectEvent $watcher "Created" -Action $handler | Out-Null
  Register-ObjectEvent $watcher "Deleted" -Action $handler | Out-Null
  Register-ObjectEvent $watcher "Renamed" -Action $handler | Out-Null

  try {
    while ($true) {
      Start-Sleep -Milliseconds 500
      if ($script:pending -and ((Get-Date) - $script:lastChange).TotalSeconds -ge $DebounceSeconds) {
        $script:pending = $false
        try { Sync-Now } catch { Write-Host $_.Exception.Message -ForegroundColor Red }
      }
    }
  } finally {
    $watcher.EnableRaisingEvents = $false
    $watcher.Dispose()
    Get-EventSubscriber | Where-Object { $_.SourceObject -eq $watcher } | Unregister-Event
  }
}
