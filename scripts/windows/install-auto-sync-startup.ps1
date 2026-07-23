#Requires -Version 5.1
<#
.SYNOPSIS
  Install a per-user Startup shortcut so auto-sync starts at login.
#>

$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$batPath = Join-Path $PSScriptRoot "start-auto-sync.bat"
$startup = [Environment]::GetFolderPath("Startup")
$shortcutPath = Join-Path $startup "Nissan GTR Auto-Sync.lnk"

$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $batPath
$shortcut.WorkingDirectory = "$RepoRoot"
$shortcut.WindowStyle = 7
$shortcut.Description = "Nissan GTR Auto real-time Git sync to GitHub"
$shortcut.Save()

Write-Host "Startup shortcut installed:" -ForegroundColor Green
Write-Host "  $shortcutPath"
Write-Host ""
Write-Host "Auto-sync will start minimized at Windows login."
Write-Host "To remove later, delete that shortcut from the Startup folder."
