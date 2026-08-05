# Surface PartSouq catalogue crawl watchdog alerts at session start.
# Reads stdin JSON (Cursor hook payload); prints additional_context when alert exists.

$ErrorActionPreference = 'Continue'
$null = [Console]::In.ReadToEnd()

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$alertPath = Join-Path $repoRoot 'data-pipeline\out\catalogue_watchdog_alert.json'

if (-not (Test-Path -LiteralPath $alertPath)) {
  Write-Output '{}'
  exit 0
}

try {
  $alert = Get-Content -LiteralPath $alertPath -Raw -Encoding UTF8 | ConvertFrom-Json
} catch {
  Write-Output '{}'
  exit 0
}

$status = [string]$alert.status
if ($status -eq 'completed') {
  # Informational only — do not force agent work on clean completion.
  $ctx = "Catalogue crawl completed. Alert file: data-pipeline/out/catalogue_watchdog_alert.json"
  $payload = @{ additional_context = $ctx } | ConvertTo-Json -Compress
  Write-Output $payload
  exit 0
}

$kind = [string]$alert.kind
$reason = [string]$alert.reason
$prompt = [string]$alert.agent_prompt
if (-not $prompt) {
  $prompt = "PartSouq catalogue crawl alert ($kind): $reason. Read data-pipeline/out/catalogue_watchdog_alert.json and diagnose/fix if safe."
}

$ctx = @"
CATALOGUE CRAWL WATCHDOG ALERT ($status / $kind)
$reason
$prompt
Alert JSON: data-pipeline/out/catalogue_watchdog_alert.json
Logs: data-pipeline/out/partsouq_full_catalogue.err.log
"@

$out = @{
  additional_context = $ctx.Trim()
  agent_message = "Catalogue crawl watchdog alert is active ($kind). Diagnose and fix/restart if safe."
} | ConvertTo-Json -Compress

Write-Output $out
exit 0
