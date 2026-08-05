"""One-off: pending queue breakdown by URL pattern."""
import sqlite3
from collections import Counter

c = sqlite3.connect("crawler_state.db")
total = c.execute("SELECT COUNT(*) FROM queue WHERE status='PENDING'").fetchone()[0]
print("total PENDING:", total)

patterns = [
    ("/vehicle", "vehicle"),
    ("/parts", "parts"),
    ("/catalog/genuine/group", "group"),
    ("/catalog/genuine/unit", "unit"),
    ("locate", "locate"),
    ("filter", "filter"),
    ("model", "model"),
]
for pat, label in patterns:
    n = c.execute(
        "SELECT COUNT(*) FROM queue WHERE status='PENDING' AND url LIKE ?",
        (f"%{pat}%",),
    ).fetchone()[0]
    print(f"  {label} ({pat}): {n}")

rows = c.execute("SELECT url FROM queue WHERE status='PENDING'").fetchall()

def bucket(u: str) -> str:
    if "/vehicle" in u:
        return "vehicle"
    if "/parts" in u:
        return "parts"
    if "group" in u or "unit" in u:
        return "groups_units"
    if "locate" in u or "filter" in u or "model" in u:
        return "nav"
    return "other"

b = Counter(bucket(u[0]) for u in rows)
print("exclusive buckets:", dict(b))

# hierarchy_level if column exists
try:
    hl = c.execute(
        "SELECT hierarchy_level, COUNT(*) FROM queue WHERE status='PENDING' GROUP BY hierarchy_level ORDER BY COUNT(*) DESC"
    ).fetchall()
    print("hierarchy_level:", hl)
except sqlite3.OperationalError as e:
    print("no hierarchy_level column:", e)

# sample vehicle URLs
print("\nsample vehicle URLs:")
for (u,) in c.execute(
    "SELECT url FROM queue WHERE status='PENDING' AND url LIKE '%/vehicle%' LIMIT 5"
):
    print(" ", u[:120])
