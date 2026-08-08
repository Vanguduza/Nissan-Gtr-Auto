import sqlite3
from pathlib import Path
from data_pipeline.megazip.state import list_active_leases, init_db

db = Path(r"out/megazip/nissan/megazip_state.db")
init_db(db)
c = sqlite3.connect(db)
print("status:", [tuple(r) for r in c.execute("SELECT status, COUNT(1) FROM queue GROUP BY status ORDER BY 2 DESC")])
d_v = c.execute("SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='VISITED'").fetchone()[0]
d_p = c.execute("SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='PENDING'").fetchone()[0]
total = d_v + d_p
print(f"diagrams visited={d_v} pending={d_p} pct={100.0*d_v/total if total else 0:.1f}%")
print("leases:", list_active_leases(db))
print("PROCESSING:")
for r in c.execute("SELECT model_slug, substr(url,-55) FROM queue WHERE status='PROCESSING'"):
    print(" ", r)
n10 = c.execute("""
  SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='VISITED'
    AND updated_at >= datetime('now', '-10 minutes')
""").fetchone()[0]
print(f"last 10m: {n10} -> {n10*6:.0f}/hour")
print("PENDING / rate / ETA:")
for r in c.execute("""
  SELECT q.model_slug,
    SUM(CASE WHEN q.status='PENDING' THEN 1 ELSE 0 END) AS pend,
    SUM(CASE WHEN q.status='VISITED' AND q.updated_at >= datetime('now','-15 minutes') THEN 1 ELSE 0 END) AS recent
  FROM queue q
  WHERE q.page_type='diagram' AND q.model_slug!=''
  GROUP BY q.model_slug
  HAVING pend > 0
  ORDER BY pend DESC
"""):
    model, pend, recent = r
    rate = (recent or 0) / 0.25
    eta = f"{pend/rate:.1f}h" if rate > 0 else "n/a"
    print(f"  {model:40} pend={pend:6} rate={rate:5.0f}/h  ETA={eta}")
print("done models (0 pending diagrams):")
for r in c.execute("""
  SELECT model_slug,
    SUM(CASE WHEN status='VISITED' AND page_type='diagram' THEN 1 ELSE 0 END) AS v,
    SUM(CASE WHEN status='PENDING' AND page_type='diagram' THEN 1 ELSE 0 END) AS p
  FROM queue WHERE model_slug IN ('serena-2090','versa-2151','murano-2120','tiida-tiida-latio-2092','pathfinder-2142','frontier-2140','altima-2135','x-trail-2064')
  GROUP BY model_slug ORDER BY 1
"""):
    print(f"  {r[0]:40} visited={r[1]:5} pending={r[2]}")
print("errors:", [tuple(r) for r in c.execute(
    "SELECT last_error, COUNT(1) FROM queue WHERE last_error IS NOT NULL AND last_error!='' GROUP BY last_error LIMIT 5"
)])
