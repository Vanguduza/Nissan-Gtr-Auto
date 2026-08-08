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
print("leases cleared")
print("status", list(c.execute("SELECT status, COUNT(1) FROM queue GROUP BY status")))
c.close()
