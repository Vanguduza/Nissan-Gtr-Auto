#!/usr/bin/env bash
# Runs every simulation scenario against a throwaway local Supabase stack.
#   1. start the stack (supabase/sim/ci/supabase/config.toml: ports 56421 / 56422)
#   2. load the schema snapshot, storage policies and reference data
#   3. apply migrations newer than the snapshot
#   4. seed simulated data and run the scenarios; any FAIL fails the run
# Needs: docker, node (npx supabase), psql, python3 with psycopg2 + cryptography.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
sim="$(dirname "$here")"
repo="$(cd "$sim/../.." && pwd)"
export PGPASSWORD=postgres
DB="postgresql://postgres:postgres@127.0.0.1:56422/postgres"
psqlq() { psql "$DB" -q -v ON_ERROR_STOP=1 "$@"; }

cd "$here"
npx -y supabase@latest stop --no-backup >/dev/null 2>&1 || true
npx -y supabase@latest start
eval "$(npx -y supabase@latest status -o env | grep -E '^(API_URL|SERVICE_ROLE_KEY)=')"

echo "== schema snapshot"
psqlq -c "create extension if not exists pg_cron; create extension if not exists pg_net schema extensions;"
# A fresh stack grants every new table/function to anon + authenticated automatically; hosted's grants
# are explicit and in the snapshot. Without this the CI database would be more open than production.
psqlq <<'SQL'
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON TABLES FROM anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM anon, authenticated, service_role;
SQL
gunzip -c "$sim/snapshot/schema.sql.gz" | psqlq
psqlq -f "$sim/snapshot/managed_schemas.sql"
psqlq -f "$sim/snapshot/reference.sql"

echo "== migrations after $(cat "$sim/snapshot/VERSION")"
since="$(cat "$sim/snapshot/VERSION")"
for f in $(ls "$repo/supabase/migrations"/*.sql | sort); do
  v="$(basename "$f" | cut -c1-14)"
  if [[ "$v" > "$since" ]]; then echo "   $v"; psqlq -1 -f "$f"; fi
done

echo "== seed + scenarios"
cd "$sim"
export SIM_DB_URL="$DB" SIM_API_URL="$API_URL" SIM_SERVICE_KEY="$SERVICE_ROLE_KEY"
rm -f ids.json
python3 seed.py
status=0
for s in e2e_day e2e_cod e2e_handin e2e_card e2e_backorder e2e_exceptions e2e_restock; do
  echo "---- $s"
  if ! out="$(python3 "$s.py" 2>&1)"; then status=1; fi
  echo "$out"
  if grep -qE '^(FAIL|FINDINGS)|Traceback' <<<"$out"; then status=1; echo "!!!! $s failed"; fi
done
"$here/rules.sh" || status=1
exit $status
