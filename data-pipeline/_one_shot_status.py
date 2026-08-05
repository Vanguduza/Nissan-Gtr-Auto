"""One-shot cache parse / catalog pipeline status (fast, no rglob)."""
import json
import sqlite3
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "out"
BUNDLE = OUT / "partsouq_bundle"
DB = OUT / "cache_parse_state.db"
META = BUNDLE / "parse_bundle_meta.json"


def worker_running() -> str:
    try:
        ps = (
            "Get-CimInstance Win32_Process | "
            "Where-Object { $_.CommandLine -match 'cache_parse_worker' } | "
            "Select-Object -ExpandProperty ProcessId"
        )
        r = subprocess.run(
            ["powershell", "-NoProfile", "-Command", ps],
            capture_output=True,
            text=True,
            timeout=15,
        )
        pids = [x.strip() for x in r.stdout.splitlines() if x.strip().isdigit()]
        return "YES pids=" + ",".join(pids) if pids else "NO"
    except Exception as exc:
        return f"UNKNOWN ({exc})"


def load_json_array(path: Path) -> list:
    if not path.is_file():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    return data if isinstance(data, list) else []


def main() -> None:
    print("cache_parse_worker running?", worker_running())

    print("parse_bundle_meta.json:")
    if META.is_file():
        print(json.dumps(json.loads(META.read_text(encoding="utf-8")), indent=2))
    else:
        alt = OUT / "parse_bundle_meta.json"
        if alt.is_file():
            print(json.dumps(json.loads(alt.read_text(encoding="utf-8")), indent=2))
        else:
            print("NOT FOUND")

    pf = load_json_array(BUNDLE / "part_fitment.json")
    vm = load_json_array(BUNDLE / "vehicle_master.json")
    bbox = sum(1 for x in pf if x.get("bbox_x") is not None)
    diag = sum(1 for x in pf if x.get("diagram_path"))
    vin_vm = sum(1 for x in vm if x.get("vin_prefix"))
    print(
        f"part_fitment: total={len(pf)} with_bbox_x={bbox} with_diagram_path={diag}"
    )
    print(f"vehicle_master: total={len(vm)} with_vin_prefix={vin_vm}")

    vi_total = vi_vin = 0
    if DB.is_file():
        conn = sqlite3.connect(str(DB))
        try:
            vi_total = conn.execute("SELECT COUNT(*) FROM vehicle_identity").fetchone()[0]
            vi_vin = conn.execute(
                "SELECT COUNT(*) FROM vehicle_identity "
                "WHERE vin_prefix IS NOT NULL AND length(trim(vin_prefix)) > 0"
            ).fetchone()[0]
        finally:
            conn.close()
    print(f"vehicle_identity: total={vi_total} with_vin_prefix={vi_vin}")

    hot = bbox > 0 or diag > 0
    vin_ok = vi_vin > 0 or vin_vm > 0
    part_ok = len(pf) > 0
    if hot and vin_ok and part_ok:
        verdict = "WORKING"
    elif hot or vin_ok or part_ok:
        verdict = "PARTIAL"
    else:
        verdict = "NOT WORKING"
    print(f"VERDICT: {verdict} for hotspots+VIN/model/part catalog")


if __name__ == "__main__":
    main()
