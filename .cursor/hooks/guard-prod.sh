#!/usr/bin/env bash
# Block dangerous production commands.
# Cursor beforeShellExecution: JSON in on stdin, JSON out on stdout.

set -euo pipefail

input=$(cat || true)
COMMAND=$(printf '%s' "$input" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n1)

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
  if [ -n "$COMMAND" ] && echo "$COMMAND" | grep -qiE "$pattern"; then
    printf '{"permission":"deny","user_message":"BLOCKED: Command matches production guard pattern: %s","agent_message":"BLOCKED: Command matches production guard pattern: %s"}' "$pattern" "$pattern"
    exit 0
  fi
done

if [ -n "${SUPABASE_URL:-}" ]; then
  if ! echo "$SUPABASE_URL" | grep -qE "localhost|127\.0\.0\.1"; then
    if [ -n "$COMMAND" ] && echo "$COMMAND" | grep -qiE "supabase db (reset|push|migrate)"; then
      printf '{"permission":"deny","user_message":"BLOCKED: Supabase command against non-local URL","agent_message":"BLOCKED: Supabase command against non-local URL: %s"}' "$SUPABASE_URL"
      exit 0
    fi
  fi
fi

echo '{ "permission": "allow" }'
exit 0
