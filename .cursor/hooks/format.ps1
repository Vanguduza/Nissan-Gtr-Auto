# Auto-format edited files based on extension.
param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$HookArgs
)

$File = if ($HookArgs -and $HookArgs.Count -gt 0) { $HookArgs[0] } else { $null }
if (-not $File) {
  $stdin = [Console]::In.ReadToEnd()
  if ($stdin) {
    try {
      $payload = $stdin | ConvertFrom-Json
      if ($payload.file) { $File = [string]$payload.file }
      elseif ($payload.path) { $File = [string]$payload.path }
    } catch { }
  }
}

if (-not $File -or -not (Test-Path -LiteralPath $File -PathType Leaf)) {
  exit 0
}

$ext = [System.IO.Path]::GetExtension($File).ToLowerInvariant()
switch ($ext) {
  { $_ -in '.ts', '.tsx', '.js', '.jsx' } {
    if (Get-Command prettier -ErrorAction SilentlyContinue) {
      & prettier --write $File 2>$null | Out-Null
    }
  }
  '.py' {
    if (Get-Command ruff -ErrorAction SilentlyContinue) {
      & ruff format $File 2>$null | Out-Null
    }
  }
  '.swift' {
    if (Get-Command swiftformat -ErrorAction SilentlyContinue) {
      & swiftformat $File 2>$null | Out-Null
    }
  }
  '.sql' {
    if (Get-Command sqlfluff -ErrorAction SilentlyContinue) {
      & sqlfluff fix $File 2>$null | Out-Null
    }
  }
}

exit 0
