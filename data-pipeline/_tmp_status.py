import sqlite3
c = sqlite3.connect(r"out/megazip/nissan/megazip_state.db")
print("status:", [tuple(r) for r in c.execute("SELECT status, COUNT(1) FROM queue GROUP BY status ORDER BY 2 DESC")])
print("page_type x status:")
for r in c.execute("SELECT page_type, status, COUNT(1) FROM queue GROUP BY page_type, status ORDER BY page_type, status"):
    print(" ", tuple(r))
models = [r[0] for r in c.execute(
    "SELECT DISTINCT model_slug FROM queue WHERE model_slug IS NOT NULL AND model_slug != '' ORDER BY 1"
)]
print(f"distinct models in queue: {len(models)}")
print("models:", models[:40], ("..." if len(models) > 40 else ""))
print("chassis:", [r[0] for r in c.execute(
    "SELECT DISTINCT chassis_code FROM queue WHERE chassis_code IS NOT NULL AND chassis_code != '' ORDER BY 1"
)])
print("page_cache:", c.execute("SELECT COUNT(1) FROM page_cache").fetchone()[0])
print("VISITED by model (top 15):")
for r in c.execute(
    "SELECT model_slug, COUNT(1) FROM queue WHERE status='VISITED' AND model_slug != '' GROUP BY model_slug ORDER BY 2 DESC LIMIT 15"
):
    print(" ", r)
print("PENDING by page_type:")
for r in c.execute(
    "SELECT page_type, COUNT(1) FROM queue WHERE status='PENDING' GROUP BY page_type ORDER BY 2 DESC"
):
    print(" ", r)
print("recent VISITED:")
for r in c.execute(
    "SELECT page_type, url, updated_at FROM queue WHERE status='VISITED' ORDER BY updated_at DESC LIMIT 5"
):
    print(" ", tuple(r))
print("PROCESSING:")
for r in c.execute("SELECT page_type, url, updated_at FROM queue WHERE status='PROCESSING'"):
    print(" ", tuple(r))
# hub payload model count
import json
row = c.execute("SELECT payload_json FROM parsed_pages WHERE url=?", ("https://www.megazip.net/parts/nissan",)).fetchone()
if row:
    models_hub = (json.loads(row[0]) or {}).get("models") or []
    print(f"hub parsed models: {len(models_hub)}")
else:
    print("hub parsed models: (no row)")
