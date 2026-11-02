#!/usr/bin/env bash
# Runs every simulation scenario against a throwaway local Supabase stack.
#   1. start the stack (supabase/sim/ci/supabase/config.toml: ports 56421 / 56422)
#   2. apply the complete active migration chain to the empty native platform
#   3. add simulation-only stock items and prices using the baseline's reference IDs
#   4. seed simulated data and run the scenarios; any FAIL fails the run
# Needs: docker, node (npx supabase), psql, python3 with psycopg2 + cryptography.
set -euo pipefail
umask 077
here="$(cd "$(dirname "$0")" && pwd)"
sim="$(dirname "$here")"
repo="$(cd "$sim/../.." && pwd)"
export PGPASSWORD=postgres
DB="postgresql://postgres:postgres@127.0.0.1:56422/postgres"
psqlq() { psql "$DB" -X -q -v ON_ERROR_STOP=1 "$@"; }
supabase() { npx -y supabase@2.120.0 "$@"; }

# `start` and `status` can print generated local keys. Keep their output private;
# only the two validated fields below enter the simulation process environment.
stack_log="$(mktemp)"
trap 'rm -f "$stack_log"' EXIT

cd "$here"
supabase stop --no-backup >/dev/null 2>&1 || true
echo "== start empty local Supabase stack (CLI 2.120.0)"
if ! supabase start >"$stack_log" 2>&1; then
  echo "Local Supabase startup failed; no migrations or scenarios were run." >&2
  exit 1
fi
stack_configuration="$(supabase status -o json 2>>"$stack_log" | python3 -c '
import json, re, sys
try:
    status = json.load(sys.stdin)
    api = status["API_URL"]
    service = status["SERVICE_ROLE_KEY"]
    if api not in ("http://127.0.0.1:56421", "http://localhost:56421"):
        raise ValueError("unexpected API origin")
    if not isinstance(service, str) or not re.fullmatch(r"[A-Za-z0-9_.-]{20,}", service):
        raise ValueError("invalid local service key")
except (ValueError, TypeError, KeyError):
    raise SystemExit("Invalid local Supabase status; refusing simulation")
print(api)
print(service)
')"
{
  IFS= read -r API_URL
  IFS= read -r SERVICE_ROLE_KEY
} <<<"$stack_configuration"
unset stack_configuration

echo "== fresh active migration chain"
# CLI migration replay is disabled in the CI config: each active file is applied
# exactly once here. Historical snapshots and archived migrations stay immutable.
shopt -s nullglob
migrations=("$repo"/supabase/migrations/*.sql)
if ((${#migrations[@]} == 0)); then
  echo "No active migrations found; refusing simulation." >&2
  exit 1
fi
for migration in "${migrations[@]}"; do
  echo "   $(basename "$migration")"
  psqlq -1 -f "$migration"
done

echo "== simulation-only catalogue fixtures"
psqlq -1 -c "SET gtr.simulation_project = 'gtr-sim-ci'" -f "$here/fixtures.sql"

echo "== seed + scenarios"
cd "$sim"
export SIM_DB_URL="$DB" SIM_API_URL="$API_URL" SIM_SERVICE_KEY="$SERVICE_ROLE_KEY"
rm -f ids.json
python3 seed.py
status=0
for s in e2e_day e2e_cod e2e_handin e2e_card e2e_backorder e2e_exceptions e2e_restock e2e_alerts; do
  echo "---- $s"
  if ! out="$(python3 "$s.py" 2>&1)"; then status=1; fi
  echo "$out"
  if grep -qE '^(FAIL|FINDINGS)|Traceback' <<<"$out"; then status=1; echo "!!!! $s failed"; fi
done
"$here/rules.sh" || status=1
exit $status
