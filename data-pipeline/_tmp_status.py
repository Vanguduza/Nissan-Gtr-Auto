import json
import sqlite3
from pathlib import Path

db = Path("out/megazip/nissan/megazip_state.db")
print("state_db_gb", round(db.stat().st_size / 1e9, 2))
conn = sqlite3.connect(f"file:{db}?mode=ro", uri=True, timeout=60)
print("journal", conn.execute("PRAGMA journal_mode").fetchone())
print("queue:")
for r in conn.execute("SELECT status, COUNT(*) FROM url_queue GROUP BY status ORDER BY status"):
    print(" ", r)
print("visited types:")
for r in conn.execute(
    "SELECT url_type, COUNT(*) FROM url_queue WHERE status='VISITED' GROUP BY url_type ORDER BY COUNT(*) DESC LIMIT 6"
):
    print(" ", r)
try:
    leases = dict(
        conn.execute(
            "SELECT model_slug, worker_id FROM worker_leases WHERE released_at IS NULL"
        ).fetchall()
    )
except sqlite3.OperationalError:
    leases = dict(conn.execute("SELECT model_slug, worker_id FROM worker_leases").fetchall())
print("leases", leases)
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
