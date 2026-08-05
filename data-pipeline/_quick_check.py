import json
import sqlite3
from pathlib import Path

meta_path = Path(r"c:\Users\j\Desktop\nissan gtr\data-pipeline\out\partsouq_bundle\parse_bundle_meta.json")
print("=== META ===")
print("path:", meta_path, "exists:", meta_path.exists())
if meta_path.exists():
    d = json.loads(meta_path.read_text(encoding="utf-8"))
    print(json.dumps(d, indent=2)[:4000])

# also list bundle dir files
bundle = Path(r"c:\Users\j\Desktop\nissan gtr\data-pipeline\out\partsouq_bundle")
print("\n=== BUNDLE FILES ===")
for p in sorted(bundle.iterdir()):
    if p.is_file():
        print(f"{p.name}\t{p.stat().st_size}\t{p.stat().st_mtime}")

db_path = Path(r"c:\Users\j\Desktop\nissan gtr\data-pipeline\out\cache_parse_state.db")
print("\n=== DB ===", db_path, "size", db_path.stat().st_size if db_path.exists() else None)
c = sqlite3.connect(str(db_path))
cur = c.cursor()
tables = [r[0] for r in cur.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY 1").fetchall()]
print("TABLES:", tables)

for t in tables:
    try:
        n = cur.execute(f"SELECT COUNT(*) FROM [{t}]").fetchone()[0]
        print(f"COUNT {t}: {n}")
    except Exception as e:
        print(f"COUNT {t}: ERR {e}")

# part_fitment sample
for name in ("part_fitment", "fitment", "parts"):
    if name in tables:
        cols = [d[1] for d in cur.execute(f"PRAGMA table_info([{name}])").fetchall()]
        print(f"\n--- {name} COLS ---", cols)
        rows = cur.execute(f"SELECT * FROM [{name}] LIMIT 3").fetchall()
        for r in rows:
            d = dict(zip(cols, r))
            interesting = {
                k: d[k]
                for k in d
                if k.startswith("bbox")
                or "diagram" in k.lower()
                or k
                in (
                    "part_number",
                    "oem_number",
                    "sku",
                    "vehicle_id",
                    "chassis_code",
                    "vin_prefix",
                )
            }
            print("sample keys:", interesting if interesting else {k: d[k] for k in list(d)[:12]})
        break

for name in ("vehicle_master", "vehicles"):
    if name in tables:
        cols = [d[1] for d in cur.execute(f"PRAGMA table_info([{name}])").fetchall()]
        print(f"\n--- {name} COLS ---", cols)
        rows = cur.execute(f"SELECT * FROM [{name}] LIMIT 3").fetchall()
        for r in rows:
            d = dict(zip(cols, r))
            want = [
                k
                for k in d
                if any(
                    x in k.lower()
                    for x in ("chassis", "vin", "variant", "model", "make", "year", "code")
                )
            ]
            print({k: d[k] for k in want} or dict(list(d.items())[:15]))
        break

if "vehicle_identity" in tables:
    cols = [d[1] for d in cur.execute("PRAGMA table_info(vehicle_identity)").fetchall()]
    print("\n--- vehicle_identity COLS ---", cols)
    total = cur.execute("SELECT COUNT(*) FROM vehicle_identity").fetchone()[0]
    vin_cols = [x for x in cols if "vin" in x.lower()]
    print("vin_cols:", vin_cols)
    if vin_cols:
        vc = vin_cols[0]
        with_vin = cur.execute(
            f"SELECT COUNT(*) FROM vehicle_identity WHERE {vc} IS NOT NULL AND trim(CAST({vc} AS TEXT))<>''"
        ).fetchone()[0]
        print(f"vehicle_identity total={total} with_{vc}={with_vin}")
    else:
        print(f"vehicle_identity total={total}")
    for r in cur.execute("SELECT * FROM vehicle_identity LIMIT 2").fetchall():
        print(dict(zip(cols, r)))

# also check jsonl/parquet outputs for part_fitment
print("\n=== LOOKING FOR FITMENT ARTIFACTS ===")
for pat in ("*fitment*", "*vehicle*", "*hotspot*", "*diagram*"):
    for p in Path(r"c:\Users\j\Desktop\nissan gtr\data-pipeline\out").rglob(pat):
        if p.is_file() and p.stat().st_size < 50_000_000:
            print(p, p.stat().st_size)
