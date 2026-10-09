#!/usr/bin/env bash
# Single-role rule tests (supabase/tests/sim_role_rules_smoke.sql) against the seeded CI stack.
set -euo pipefail
repo="$(cd "$(dirname "$0")/../../.." && pwd)"
PGPASSWORD=postgres psql "${SIM_DB_URL:-postgresql://postgres:postgres@127.0.0.1:56422/postgres}" -v ON_ERROR_STOP=1 \
  -f "$repo/supabase/tests/sim_role_rules_smoke.sql"
