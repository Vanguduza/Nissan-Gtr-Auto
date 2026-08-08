from pathlib import Path
import sqlite3
from data_pipeline.megazip.state import init_db

db = Path(r"out/megazip/nissan/megazip_state.db")
init_db(db)
c = sqlite3.connect(db)
n = c.execute(
    "UPDATE queue SET status='PENDING', updated_at=datetime('now') WHERE status='PROCESSING'"
).rowcount
c.execute("DELETE FROM worker_leases")
c.commit()
print("requeued PROCESSING", n)
print("pending by model:")
for r in c.execute("""
  SELECT model_slug, COUNT(1) FROM queue
  WHERE status='PENDING' AND page_type='diagram' AND model_slug!=''
  GROUP BY model_slug ORDER BY 2 DESC
"""):
    print(f"  {r[0]:40} {r[1]}")
c.close()
