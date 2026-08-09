import sqlite3

c = sqlite3.connect(r"out/megazip/nissan/megazip_state.db")
print("pending", c.execute("SELECT COUNT(1) FROM queue WHERE status='PENDING'").fetchone()[0])
print("processing", c.execute("SELECT COUNT(1) FROM queue WHERE status='PROCESSING'").fetchone()[0])
print(
    "visited_diagrams",
    c.execute(
        "SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='VISITED'"
    ).fetchone()[0],
)
print("leases", c.execute("SELECT model_slug, worker_id FROM worker_leases").fetchall())
