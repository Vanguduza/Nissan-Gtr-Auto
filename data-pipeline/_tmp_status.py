import sqlite3
from pathlib import Path
from data_pipeline.megazip.state import list_active_leases, init_db

db = Path(r"out/megazip/nissan/megazip_state.db")
init_db(db)
c = sqlite3.connect(db)
print("status:", [tuple(r) for r in c.execute("SELECT status, COUNT(1) FROM queue GROUP BY status ORDER BY 2 DESC")])
d_v = c.execute("SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='VISITED'").fetchone()[0]
d_p = c.execute("SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='PENDING'").fetchone()[0]
print(f"diagrams visited={d_v} pending={d_p} pct={100.0*d_v/(d_v+d_p) if d_v+d_p else 0:.1f}%")
print("leases:", list_active_leases(db))
print("PROCESSING:")
for r in c.execute("SELECT model_slug, substr(url,-50) FROM queue WHERE status='PROCESSING'"):
    print(" ", r)
print("PENDING by model:")
for r in c.execute("""
  SELECT model_slug, COUNT(1) FROM queue
  WHERE status='PENDING' AND page_type='diagram' AND model_slug!=''
  GROUP BY model_slug ORDER BY 2 DESC
"""):
    print(f"  {r[0]:40} {r[1]}")
print("VISITED diagrams by model (top 10):")
for r in c.execute("""
  SELECT model_slug, COUNT(1) FROM queue
  WHERE status='VISITED' AND page_type='diagram' AND model_slug!=''
  GROUP BY model_slug ORDER BY 2 DESC LIMIT 10
"""):
    print(" ", r)
print("errors:", [tuple(r) for r in c.execute(
    "SELECT last_error, COUNT(1) FROM queue WHERE last_error IS NOT NULL AND last_error!='' GROUP BY last_error LIMIT 5"
)])
