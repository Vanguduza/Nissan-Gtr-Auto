import sqlite3
from data_pipeline.megazip.state import list_active_leases, init_db
from pathlib import Path

db = Path(r"out/megazip/nissan/megazip_state.db")
init_db(db)
print("ACTIVE LEASES:")
for m, w in sorted(list_active_leases(db).items()):
    print(f"  {w:12} -> {m}")
c = sqlite3.connect(db)
print("\nPROCESSING by model:")
for r in c.execute("SELECT model_slug, COUNT(1) FROM queue WHERE status='PROCESSING' GROUP BY model_slug"):
    print(" ", r)
print("\nPENDING remaining by model:")
for r in c.execute("""
  SELECT model_slug, COUNT(1) FROM queue
  WHERE status='PENDING' AND page_type='diagram' AND model_slug!=''
  GROUP BY model_slug ORDER BY 2 DESC
"""):
    print(f"  {r[0]:40} {r[1]}")
