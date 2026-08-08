import sqlite3, json
c = sqlite3.connect(r"out/megazip/nissan/megazip_state.db")
print("status:", [tuple(r) for r in c.execute("SELECT status, COUNT(1) FROM queue GROUP BY status ORDER BY 2 DESC")])
print("page_type x status:")
for r in c.execute("SELECT page_type, status, COUNT(1) FROM queue GROUP BY page_type, status ORDER BY page_type, status"):
    print(" ", tuple(r))
print("page_cache:", c.execute("SELECT COUNT(1) FROM page_cache").fetchone()[0])
models_visited = c.execute(
    "SELECT COUNT(DISTINCT model_slug) FROM queue WHERE status='VISITED' AND model_slug != ''"
).fetchone()[0]
print("distinct models with VISITED pages:", models_visited)
print("chassis:", [r[0] for r in c.execute(
    "SELECT DISTINCT chassis_code FROM queue WHERE chassis_code != '' ORDER BY 1")])
print("VISITED by model (top 20):")
for r in c.execute(
    "SELECT model_slug, COUNT(1) FROM queue WHERE status='VISITED' AND model_slug != '' GROUP BY model_slug ORDER BY 2 DESC LIMIT 20"
):
    print(" ", r)
d_v = c.execute("SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='VISITED'").fetchone()[0]
d_p = c.execute("SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='PENDING'").fetchone()[0]
print(f"diagrams visited={d_v} pending={d_p} pct={100.0*d_v/(d_v+d_p) if d_v+d_p else 0:.1f}%")
print("recent VISITED:")
for r in c.execute("SELECT page_type, url, updated_at FROM queue WHERE status='VISITED' ORDER BY updated_at DESC LIMIT 4"):
    print(" ", tuple(r))
print("errors:")
for r in c.execute("SELECT status, last_error, COUNT(1) FROM queue WHERE last_error IS NOT NULL AND last_error != '' GROUP BY status, last_error LIMIT 8"):
    print(" ", tuple(r))
