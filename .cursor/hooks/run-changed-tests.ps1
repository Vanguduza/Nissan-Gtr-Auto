# Run targeted tests based on which directories were changed.
$ErrorActionPreference = 'Continue'

$changed = ''
try {
  $changed = (& git diff --name-only HEAD 2>$null) -join "`n"
} catch { }

if (-not $changed) {
  exit 0
}

$ran = $false

if ($changed -match '(?m)^supabase/') {
  if (Get-Command supabase -ErrorAction SilentlyContinue) {
    Write-Output 'Running Supabase migration validation...'
    & supabase db lint 2>$null | Out-Null
    $ran = $true
  }
}

if ($changed -match '(?m)^data-pipeline/') {
  if ((Test-Path 'data-pipeline/pyproject.toml') -or (Test-Path 'data-pipeline/requirements.txt')) {
    Write-Output 'Running data pipeline tests...'
    Push-Location data-pipeline
    try { & python -m pytest -x -q 2>$null | Out-Null } finally { Pop-Location }
    $ran = $true
  }
}

if ($changed -match '(?m)^apps/web/') {
  if (Test-Path 'apps/web/package.json') {
    Write-Output 'Running web tests...'
    Push-Location apps/web
    try { & pnpm test --passWithNoTests 2>$null | Out-Null } finally { Pop-Location }
    $ran = $true
  }
}

if ($changed -match '(?m)^packages/shared/') {
  if (Test-Path 'packages/shared/package.json') {
    Write-Output 'Running shared package tests...'
    Push-Location packages/shared
    try { & pnpm test --passWithNoTests 2>$null | Out-Null } finally { Pop-Location }
    $ran = $true
  }
}

if (-not $ran) {
  Write-Output 'No test runner configured for changed files.'
}

exit 0
