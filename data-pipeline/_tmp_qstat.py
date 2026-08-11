import sqlite3
from pathlib import Path

c = sqlite3.connect("out/megazip/nissan/megazip_state.db")
print("PENDING by type:")
for r in c.execute(
    "SELECT page_type, COUNT(1) FROM queue WHERE status='PENDING' GROUP BY page_type ORDER BY 2 DESC"
):
    print(r)
print("VISITED by type:")
for r in c.execute(
    "SELECT page_type, COUNT(1) FROM queue WHERE status='VISITED' GROUP BY page_type ORDER BY 2 DESC"
):
    print(r)
cache = Path("out/megazip/nissan/cache")
print("cache_html", len(list(cache.glob("*.html"))) if cache.exists() else 0)
print(
    "leases",
    c.execute("SELECT COUNT(1) FROM worker_leases").fetchone()[0],
)
