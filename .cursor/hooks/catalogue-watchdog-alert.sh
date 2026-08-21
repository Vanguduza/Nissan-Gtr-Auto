#!/usr/bin/env bash
# Surface PartSouq catalogue crawl watchdog alerts at session start.
# Reads stdin JSON (Cursor hook payload); prints additional_context when alert exists.

set -euo pipefail
cat >/dev/null || true

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ALERT_PATH="$REPO_ROOT/data-pipeline/out/catalogue_watchdog_alert.json"

if [ ! -f "$ALERT_PATH" ]; then
  echo '{}'
  exit 0
fi

python3 - "$ALERT_PATH" <<'PY' || echo '{}'
import json, sys
path = sys.argv[1]
try:
    with open(path, encoding="utf-8") as f:
        alert = json.load(f)
except Exception:
    print("{}")
    raise SystemExit(0)

status = str(alert.get("status") or "")
if status == "completed":
    ctx = "Catalogue crawl completed. Alert file: data-pipeline/out/catalogue_watchdog_alert.json"
    print(json.dumps({"additional_context": ctx}, separators=(",", ":")))
    raise SystemExit(0)

kind = str(alert.get("kind") or "")
reason = str(alert.get("reason") or "")
prompt = str(alert.get("agent_prompt") or "")
if not prompt:
    prompt = (
        f"PartSouq catalogue crawl alert ({kind}): {reason}. "
        "Read data-pipeline/out/catalogue_watchdog_alert.json and diagnose/fix if safe."
    )
ctx = (
    f"CATALOGUE CRAWL WATCHDOG ALERT ({status} / {kind})\n"
    f"{reason}\n{prompt}\n"
    "Alert JSON: data-pipeline/out/catalogue_watchdog_alert.json\n"
    "Logs: data-pipeline/out/partsouq_full_catalogue.err.log"
)
print(
    json.dumps(
        {
            "additional_context": ctx.strip(),
            "agent_message": f"Catalogue crawl watchdog alert is active ({kind}). Diagnose and fix/restart if safe.",
        },
        separators=(",", ":"),
    )
)
PY
exit 0
