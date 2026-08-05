import json
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "out"

paths_crawl = [ROOT / "crawler_state.db", OUT / "crawler_state.db"]
for db in paths_crawl:
    if not db.is_file():
        continue
    c = sqlite3.connect(str(db))
    tables = [t[0] for t in c.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()]
    print("CRAWLER_DB:", db)
    print("  tables:", tables)
    for sql in (
        "SELECT COUNT(DISTINCT vid) FROM crawl_queue WHERE vid IS NOT NULL",
        "SELECT COUNT(DISTINCT vid) FROM crawl_queue",
        "SELECT COUNT(*) FROM crawl_queue",
    ):
        try:
            print(f"  {sql}:", c.execute(sql).fetchone()[0])
        except Exception as e:
            print(f"  {sql}: ERR", e)
    for t in tables:
        cols = [x[1] for x in c.execute(f"PRAGMA table_info({t})").fetchall()]
        if "vid" in cols:
            try:
                print(f"  DISTINCT vid in {t}:", c.execute(f"SELECT COUNT(DISTINCT vid) FROM {t}").fetchone()[0])
            except Exception:
                pass
        if "url" in cols:
            try:
                n = c.execute(f"SELECT COUNT(*) FROM {t} WHERE url LIKE '%/vehicle%'").fetchone()[0]
                print(f"  URLs /vehicle in {t}:", n)
            except Exception:
                pass
    c.close()
    break
else:
    print("CRAWLER_DB: not found")

parse_db = OUT / "cache_parse_state.db"
if parse_db.is_file():
    c = sqlite3.connect(str(parse_db))
    print("PARSE_DB:", parse_db)
    for sql in (
        "SELECT COUNT(*) FROM vehicle_identity",
        "SELECT COUNT(DISTINCT vid) FROM vehicle_identity",
    ):
        try:
            print(f"  {sql}:", c.execute(sql).fetchone()[0])
        except Exception as e:
            print(f"  {sql}: ERR", e)
    c.close()
else:
    print("PARSE_DB: missing")

bundle = OUT / "partsouq_bundle"
if bundle.is_dir():
    vm = bundle / "vehicle_master.json"
    if vm.is_file():
        data = json.loads(vm.read_text(encoding="utf-8"))
        n = len(data) if isinstance(data, list) else len(data)
        print("vehicle_master.json:", n)
    else:
        print("vehicle_master.json: missing; files:", [x.name for x in bundle.iterdir()])
else:
    print("partsouq_bundle: missing")
