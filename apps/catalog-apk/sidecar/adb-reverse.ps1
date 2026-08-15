# Map phone localhost:8191/8192 -> host FlareSolverr + control agent (USB debugging).
param(
    [int]$FlarePort = 8191,
    [int]$AgentPort = 8192
)

$ErrorActionPreference = "Stop"

function Find-Adb {
    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }
    $candidates = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { return $c }
    }
    throw "adb not found. Install Android platform-tools or add adb to PATH."
}

$adb = Find-Adb
& $adb reverse "tcp:$FlarePort" "tcp:$FlarePort"
& $adb reverse "tcp:$AgentPort" "tcp:$AgentPort"
Write-Host "adb reverse ok:"
& $adb reverse --list
Write-Host "On device use: http://127.0.0.1:$FlarePort/v1 and agent http://127.0.0.1:$AgentPort"
