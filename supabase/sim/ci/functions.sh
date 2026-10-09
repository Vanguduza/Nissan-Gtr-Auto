#!/usr/bin/env bash
# Serves every Edge Function in supabase/functions against the local simulation stack (gtr-sim-ci,
# started by ci/run.sh), at http://127.0.0.1:56421/functions/v1/<name>.
#   bash supabase/sim/ci/functions.sh            # serve until Ctrl-C
# Workers that send messages run in their local stub mode (WORKER_ALLOW_UNVERIFIED_LOCAL=1 with no
# WORKER_SHARED_SECRET): process-sms-outbox marks messages sent without calling a gateway. Provider
# secrets (SMS, WhatsApp, Paynow, ContiPay, R2) are never needed here; put real ones only in the
# hosted project's function secrets. Needs Docker and network access to jsr.io / npm for imports.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
env_file="$(mktemp)"
trap 'rm -f "$env_file"' EXIT
printf 'WORKER_ALLOW_UNVERIFIED_LOCAL=1\n' > "$env_file"
cd "$here"
npx -y supabase@latest functions serve --env-file "$env_file"
