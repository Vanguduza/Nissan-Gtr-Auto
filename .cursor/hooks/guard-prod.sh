#!/usr/bin/env bash
# Block dangerous production commands.
# Called by Cursor beforeShellExecution hook with failClosed: true.

set -euo pipefail

COMMAND="${1:-}"

BLOCKED_PATTERNS=(
  "supabase db push.*--linked"
  "supabase functions deploy.*--project-ref"
  "DROP DATABASE"
  "DROP SCHEMA"
  "TRUNCATE.*journal_entries"
  "DELETE FROM journal_entries"
  "UPDATE journal_entries"
  "service_role"
  "rm -rf /"
  "rm -rf \*"
  "--force.*production"
  "deploy.*prod"
)

for pattern in "${BLOCKED_PATTERNS[@]}"; do
  if echo "$COMMAND" | grep -qiE "$pattern"; then
    echo "BLOCKED: Command matches production guard pattern: $pattern"
    echo "Command was: $COMMAND"
    exit 1
  fi
done

# Block if SUPABASE_URL points to production (not localhost)
if [ -n "${SUPABASE_URL:-}" ]; then
  if ! echo "$SUPABASE_URL" | grep -qE "localhost|127\.0\.0\.1"; then
    if echo "$COMMAND" | grep -qiE "supabase db (reset|push|migrate)"; then
      echo "BLOCKED: Supabase command against non-local URL: $SUPABASE_URL"
      exit 1
    fi
  fi
fi

exit 0
