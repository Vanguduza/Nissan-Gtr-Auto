#Requires -Version 5.1
<#
.SYNOPSIS
  Watch the repo and auto git add / commit / push on file changes (real-time sync to GitHub).

.PARAMETER PollSeconds
  Poll interval in seconds. Default 5 (reliable on Windows). Set 0 to use FileSystemWatcher.

.PARAMETER DebounceSeconds
  Wait this long after the last change before committing when using FileSystemWatcher (default 3).

.PARAMETER Remote
  Git remote name (default origin).
#>

param(
  [int]$PollSeconds = 5,
  [int]$DebounceSeconds = 3,
  [string]$Remote = "origin"
)

$ErrorActionPreference = "Stop"

# Ensure Git for Windows is on PATH for this process
$gitCandidates = @(
  "$env:ProgramFiles\Git\cmd",
  "${env:ProgramFiles(x86)}\Git\cmd",
  "$env:LOCALAPPDATA\Programs\Git\cmd"
)
foreach ($dir in $gitCandidates) {
  if ((Test-Path (Join-Path $dir "git.exe")) -and ($env:Path -notlike "*$dir*")) {
    $env:Path = "$dir;$env:Path"
  }
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
  Write-Error "git not found. Install Git for Windows: https://git-scm.com/download/win"
}

# Resolve repo root (script lives in scripts/windows/)
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $RepoRoot

if (-not (Test-Path (Join-Path $RepoRoot ".git"))) {
  Write-Error "Not a git repository: $RepoRoot"
}

# Commit identity for this process only (does not write git config)
function Ensure-GitIdentity {
  $name = (git config --get user.name 2>$null)
  $email = (git config --get user.email 2>$null)
  if (-not $name) {
    $env:GIT_AUTHOR_NAME = "Vanguduza"
    $env:GIT_COMMITTER_NAME = "Vanguduza"
  }
  if (-not $email) {
    $env:GIT_AUTHOR_EMAIL = "Vanguduza@users.noreply.github.com"
    $env:GIT_COMMITTER_EMAIL = "Vanguduza@users.noreply.github.com"
  }
}

Ensure-GitIdentity

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
# Keep log outside the repo so writes do not trigger endless sync loops
$logFile = Join-Path $env:TEMP "nissan-gtr-auto-sync.log"

function Write-SyncLog {
  param([string]$Message, [string]$Color = "White")
  $stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
  $line = "[$stamp] $Message"
  Write-Host $line -ForegroundColor $Color
  Add-Content -Path $logFile -Value $line -ErrorAction SilentlyContinue
}

Write-SyncLog "=== Nissan GTR Auto real-time auto-sync ===" "Cyan"
Write-SyncLog "Repo:    $RepoRoot"
Write-SyncLog "Branch:  $branch"
Write-SyncLog "Remote:  $Remote"
if ($PollSeconds -gt 0) {
  Write-SyncLog "Mode:    poll every ${PollSeconds}s"
} else {
  Write-SyncLog "Mode:    FileSystemWatcher (debounce ${DebounceSeconds}s)"
}
Write-SyncLog "Log:     $logFile"
Write-SyncLog "Press Ctrl+C to stop."
Write-Host ""

$script:syncLock = $false

function Sync-Now {
  if ($script:syncLock) { return }
  $script:syncLock = $true
  try {
    Set-Location $RepoRoot
    Ensure-GitIdentity

    $status = git status --porcelain
    if (-not $status) {
      return
    }

    $stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $msg = "auto-sync: $stamp"

    Write-SyncLog "Changes detected - committing and pushing..." "Yellow"
    git add -A
    $staged = git diff --cached --name-only
    if (-not $staged) {
      Write-SyncLog "Nothing staged; skipping." "Gray"
      return
    }

    git commit -m $msg
    if ($LASTEXITCODE -ne 0) {
      Write-SyncLog "Commit failed." "Red"
      return
    }

    $branchNow = (git rev-parse --abbrev-ref HEAD).Trim()
    git push -u $Remote $branchNow
    if ($LASTEXITCODE -eq 0) {
      Write-SyncLog "Pushed to $Remote/$branchNow" "Green"
    } else {
      Write-SyncLog "Push failed (check GitHub auth / network)." "Red"
    }
  } finally {
    $script:syncLock = $false
  }
}

$excludeDirs = @(".git", "node_modules", ".next", "dist", "build", ".turbo", "coverage")

# Initial sync so pending local work is pushed immediately
try { Sync-Now } catch { Write-SyncLog $_.Exception.Message "Red" }

if ($PollSeconds -gt 0) {
  while ($true) {
    try { Sync-Now } catch { Write-SyncLog $_.Exception.Message "Red" }
    Start-Sleep -Seconds $PollSeconds
  }
} else {
  $watcher = New-Object System.IO.FileSystemWatcher
  $watcher.Path = $RepoRoot
  $watcher.IncludeSubdirectories = $true
  $watcher.EnableRaisingEvents = $true
  $watcher.NotifyFilter = [IO.NotifyFilters]"FileName, DirectoryName, LastWrite, Size"

  # Shared flag file so event runspace can signal the main loop reliably
  $flagFile = Join-Path $env:TEMP "nissan-gtr-auto-sync.pending"
  $handler = {
    $path = $Event.SourceEventArgs.FullPath
    $skip = $false
    foreach ($d in $using:excludeDirs) {
      if ($path -match [regex]::Escape([IO.Path]::DirectorySeparatorChar + $d + [IO.Path]::DirectorySeparatorChar) -or
          $path -match [regex]::Escape([IO.Path]::DirectorySeparatorChar + $d + "$")) {
        $skip = $true
        break
      }
    }
    if (-not $skip) {
      Set-Content -Path $using:flagFile -Value (Get-Date).ToString("o") -Force
    }
  }

  Register-ObjectEvent $watcher "Changed" -Action $handler | Out-Null
  Register-ObjectEvent $watcher "Created" -Action $handler | Out-Null
  Register-ObjectEvent $watcher "Deleted" -Action $handler | Out-Null
  Register-ObjectEvent $watcher "Renamed" -Action $handler | Out-Null

  try {
    while ($true) {
      Start-Sleep -Milliseconds 500
      if (Test-Path $flagFile) {
        $flagTime = Get-Item $flagFile | Select-Object -ExpandProperty LastWriteTime
        if (((Get-Date) - $flagTime).TotalSeconds -ge $DebounceSeconds) {
          Remove-Item $flagFile -Force -ErrorAction SilentlyContinue
          try { Sync-Now } catch { Write-SyncLog $_.Exception.Message "Red" }
        }
      }
    }
  } finally {
    $watcher.EnableRaisingEvents = $false
    $watcher.Dispose()
    Get-EventSubscriber | Where-Object { $_.SourceObject -eq $watcher } | Unregister-Event
    Remove-Item $flagFile -Force -ErrorAction SilentlyContinue
  }
}
