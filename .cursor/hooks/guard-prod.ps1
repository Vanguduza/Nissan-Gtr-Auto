# Block dangerous production commands.
# Cursor beforeShellExecution: JSON in on stdin, JSON out on stdout.
$ErrorActionPreference = 'Stop'

$inputText = [Console]::In.ReadToEnd()
$command = ''

if ($inputText) {
  try {
    $payload = $inputText | ConvertFrom-Json
    if ($null -ne $payload.command) { $command = [string]$payload.command }
  } catch {
    $command = $inputText
  }
}

$blockedPatterns = @(
  'supabase db push.*--linked',
  'supabase functions deploy.*--project-ref',
  'DROP DATABASE',
  'DROP SCHEMA',
  'TRUNCATE.*journal_entries',
  'DELETE FROM journal_entries',
  'UPDATE journal_entries',
  'service_role',
  'rm -rf /',
  'rm -rf \*',
  '--force.*production',
  'deploy.*prod'
)

foreach ($pattern in $blockedPatterns) {
  if ($command -and ($command -match "(?i)$pattern")) {
    $msg = "BLOCKED: Command matches production guard pattern: $pattern"
    @{
      permission = 'deny'
      user_message = $msg
      agent_message = "$msg`nCommand was: $command"
    } | ConvertTo-Json -Compress
    exit 0
  }
}

$supabaseUrl = $env:SUPABASE_URL
if ($supabaseUrl -and ($supabaseUrl -notmatch 'localhost|127\.0\.0\.1')) {
  if ($command -and ($command -match '(?i)supabase db (reset|push|migrate)')) {
    $msg = "BLOCKED: Supabase command against non-local URL: $supabaseUrl"
    @{
      permission = 'deny'
      user_message = $msg
      agent_message = $msg
    } | ConvertTo-Json -Compress
    exit 0
  }
}

'{ "permission": "allow" }'
exit 0
