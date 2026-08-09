"""Print megazip queue + lease status."""
from __future__ import annotations

import sqlite3
from pathlib import Path

db = Path("out/megazip/nissan/megazip_state.db")
con = sqlite3.connect(f"file:{db.as_posix()}?mode=ro", uri=True)
print("tables", [r[0] for r in con.execute("select name from sqlite_master where type='table' order by 1")])
print("queue_by_status", list(con.execute("select status, count(*) from queue group by status order by 1")))
print(
    "pending_by_model_top15",
    list(
        con.execute(
            """
            select coalesce(model_slug,'(none)'), count(*)
            from queue where status='PENDING'
            group by 1 order by 2 desc limit 15
            """
        )
    ),
)
print(
    "processing",
    list(con.execute("select model_slug, count(*) from queue where status='PROCESSING' group by 1")),
)
try:
    print("leases", list(con.execute("select model_slug, worker_id, heartbeat_at from worker_leases")))
except Exception as exc:
    print("leases_err", exc)
print(
    "visited",
    con.execute("select count(*) from queue where status='VISITED'").fetchone()[0],
)
