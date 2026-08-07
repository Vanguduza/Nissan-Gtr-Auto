import sqlite3
con = sqlite3.connect(r"out/megazip/nissan/megazip_state.db")
con.row_factory = sqlite3.Row
cur = con.cursor()
hub = "https://www.megazip.net/parts/nissan"

print("queue hub row:")
for r in cur.execute("SELECT url, status, page_type, attempts, updated_at, last_error FROM queue WHERE url=?", (hub,)):
    print("  ", dict(r))

print("\npage_cache hub row exists:", cur.execute("SELECT COUNT(1) FROM page_cache WHERE url=?", (hub,)).fetchone()[0])

print("\nqueue updated_at range (VISITED):")
for r in cur.execute("SELECT MIN(updated_at), MAX(updated_at) FROM queue WHERE status='VISITED'"):
    print("  min/max:", tuple(r))

print("\nhub vs first diagram timestamps:")
for r in cur.execute("SELECT page_type, MIN(updated_at) mn, MAX(updated_at) mx, COUNT(1) c FROM queue GROUP BY page_type ORDER BY mn"):
    print("  ", dict(r))

# earliest visited rows overall
print("\nearliest 5 visited rows:")
for r in cur.execute("SELECT page_type, url, updated_at FROM queue WHERE status='VISITED' ORDER BY updated_at ASC LIMIT 5"):
    print("  ", dict(r))

# page_cache count vs cache files
import os
n = len([f for f in os.listdir("out/megazip/nissan/cache") if f.endswith(".html")])
print("\ncache .html files on disk:", n)
print("page_cache rows:", cur.execute("SELECT COUNT(1) FROM page_cache").fetchone()[0])
