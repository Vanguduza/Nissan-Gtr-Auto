import json
import sqlite3
from pathlib import Path

db = Path("out/megazip/nissan/megazip_state.db")
print("state_db_gb", round(db.stat().st_size / 1e9, 2))
conn = sqlite3.connect(f"file:{db}?mode=ro", uri=True, timeout=120)
print("journal", conn.execute("PRAGMA journal_mode").fetchone())
print("queue:")
for r in conn.execute("SELECT status, COUNT(*) FROM queue GROUP BY status ORDER BY status"):
    print(" ", r)
print("visited types:")
for r in conn.execute(
    "SELECT page_type, COUNT(*) FROM queue WHERE status='VISITED' GROUP BY page_type ORDER BY COUNT(*) DESC LIMIT 6"
):
    print(" ", r)
leases = dict(conn.execute("SELECT model_slug, worker_id FROM worker_leases").fetchall())
print("leases", leases)
print("processing", conn.execute("SELECT COUNT(*) FROM queue WHERE status='PROCESSING'").fetchone()[0])
conn.close()

cache_root = Path("out/megazip/nissan/cache")
cache = sum(1 for _ in cache_root.rglob("*.html")) if cache_root.exists() else 0
print("cache_html", cache)

qpath = Path("out/megazip/worker_replacement_queue.json")
if qpath.exists():
    q = json.loads(qpath.read_text())
    queue = q.get("queue", q if isinstance(q, list) else [])
    print("replacement_queue_len", len(queue))
    print("replacement_queue_front", queue[:10])

# supabase probe
try:
    import os
    from dotenv import load_dotenv
    load_dotenv(Path(__file__).resolve().parents[1] / ".env")
    url = os.getenv("SUPABASE_URL") or os.getenv("NEXT_PUBLIC_SUPABASE_URL")
    key = os.getenv("SUPABASE_SERVICE_ROLE_KEY") or os.getenv("SUPABASE_ANON_KEY")
    if url and key:
        import httpx
        headers = {"apikey": key, "Authorization": f"Bearer {key}", "Prefer": "count=exact"}
        counts = {}
        for table, col in [
            ("external_vehicle_models", "id"),
            ("external_vehicle_variants", "id"),
            ("external_diagrams", "id"),
            ("external_part_fitments", "id"),
        ]:
            r = httpx.head(f"{url}/rest/v1/{table}?select={col}", headers=headers, timeout=20)
            cr = r.headers.get("content-range", "")
            counts[table.split("_")[-1] if "models" in table else table.replace("external_", "")] = cr.split("/")[-1] if "/" in cr else "?"
        print("supabase", counts)
except Exception as e:
    print("supabase_err", type(e).__name__, str(e)[:80])
