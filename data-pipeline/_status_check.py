import sqlite3
from datetime import datetime, timezone

con = sqlite3.connect(r"out/megazip/nissan/megazip_state.db")
cur = con.cursor()

print("=== status ===")
for r in cur.execute("SELECT status, COUNT(1) AS c FROM queue GROUP BY status ORDER BY c DESC"):
    print(r)

print("=== page_type x status ===")
for r in cur.execute(
    "SELECT page_type, status, COUNT(1) FROM queue GROUP BY page_type, status ORDER BY page_type, status"
):
    print(r)

print("models:", [r[0] for r in cur.execute(
    "SELECT DISTINCT model_slug FROM queue WHERE model_slug IS NOT NULL AND model_slug != '' ORDER BY 1"
)])
print("chassis:", [r[0] for r in cur.execute(
    "SELECT DISTINCT chassis_code FROM queue WHERE chassis_code IS NOT NULL AND chassis_code != '' ORDER BY 1"
)])
print("variants:", cur.execute(
    "SELECT COUNT(DISTINCT variant_slug) FROM queue WHERE variant_slug IS NOT NULL AND variant_slug != ''"
).fetchone()[0])
print("page_cache:", cur.execute("SELECT COUNT(1) FROM page_cache").fetchone()[0])
print("parsed_pages:", cur.execute("SELECT COUNT(1) FROM parsed_pages").fetchone()[0])

print("VISITED by model:")
for r in cur.execute(
    "SELECT model_slug, COUNT(1) FROM queue WHERE status='VISITED' GROUP BY model_slug ORDER BY 2 DESC LIMIT 15"
):
    print(" ", r)

print("PENDING diagrams by model:")
for r in cur.execute(
    "SELECT model_slug, COUNT(1) FROM queue WHERE status='PENDING' AND page_type='diagram' GROUP BY model_slug ORDER BY 2 DESC LIMIT 15"
):
    print(" ", r)

print("recent VISITED:")
for r in cur.execute(
    "SELECT page_type, url, updated_at FROM queue WHERE status='VISITED' ORDER BY updated_at DESC LIMIT 5"
):
    print(" ", r)

print("PROCESSING:")
for r in cur.execute(
    "SELECT page_type, url, updated_at FROM queue WHERE status='PROCESSING' LIMIT 5"
):
    print(" ", r)

print("errors:")
for r in cur.execute(
    "SELECT status, last_error, COUNT(1) FROM queue WHERE last_error IS NOT NULL AND last_error != '' GROUP BY status, last_error LIMIT 10"
):
    print(" ", r)

d = cur.execute("SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='VISITED'").fetchone()[0]
p = cur.execute("SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='PENDING'").fetchone()[0]
print(f"diagrams visited={d} pending={p} total={d+p} pct={100.0*d/(d+p) if d+p else 0:.1f}%")
