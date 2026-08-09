from pathlib import Path
import sqlite3
from data_pipeline.megazip.state import list_active_leases, queue_stats

db = Path("out/megazip/nissan/megazip_state.db")
print("queue", queue_stats(db))
print("leases", list_active_leases(db))
con = sqlite3.connect(f"file:{db.as_posix()}?mode=ro", uri=True)
print(
    "pending_top",
    list(
        con.execute(
            """
            select coalesce(model_slug, 'none'), count(*)
            from queue where status='PENDING'
            group by 1 order by 2 desc limit 10
            """
        )
    ),
)
print(
    "visited",
    con.execute("select count(*) from queue where status='VISITED'").fetchone()[0],
)
# main log mtime / last progress
for p in [
    Path("out/megazip/main_crawl_resume.err.log"),
    Path("out/megazip/main_crawl_resume.log"),
]:
    if p.exists():
        print(p.name, "mtime", p.stat().st_mtime, "bytes", p.stat().st_size)
