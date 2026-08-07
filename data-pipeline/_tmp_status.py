import sqlite3
c = sqlite3.connect(r"out/megazip/nissan/megazip_state.db")
print("status:", [tuple(r) for r in c.execute("SELECT status, COUNT(1) FROM queue GROUP BY status ORDER BY 2 DESC")])
print("page_type x status:")
for r in c.execute("SELECT page_type, status, COUNT(1) FROM queue GROUP BY page_type, status ORDER BY page_type, status"):
    print(" ", tuple(r))
print("models:", [r[0] for r in c.execute("SELECT DISTINCT model_slug FROM queue WHERE model_slug NOT IN ('') ORDER BY 1")])
print("chassis:", [r[0] for r in c.execute("SELECT DISTINCT chassis_code FROM queue WHERE chassis_code NOT IN ('') ORDER BY 1")])
print("page_cache:", c.execute("SELECT COUNT(1) FROM page_cache").fetchone()[0])
print("parsed_pages:", c.execute("SELECT COUNT(1) FROM parsed_pages").fetchone()[0])
d = c.execute("SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='VISITED'").fetchone()[0]
p = c.execute("SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='PENDING'").fetchone()[0]
print(f"diagrams visited={d} pending={p} total={d+p} pct={100.0*d/(d+p) if d+p else 0:.1f}%")
print("PROCESSING:")
for r in c.execute("SELECT url, updated_at FROM queue WHERE status='PROCESSING'"):
    print(" ", tuple(r))
print("recent VISITED:")
for r in c.execute("SELECT url, updated_at FROM queue WHERE status='VISITED' ORDER BY updated_at DESC LIMIT 4"):
    print(" ", tuple(r))
print("errors:")
for r in c.execute("SELECT status, last_error, COUNT(1) FROM queue WHERE last_error IS NOT NULL AND last_error != '' GROUP BY status, last_error LIMIT 10"):
    print(" ", tuple(r))
