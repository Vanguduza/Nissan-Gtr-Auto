import sqlite3
c = sqlite3.connect(r"out/megazip/nissan/megazip_state.db")
print("status:", [tuple(r) for r in c.execute("SELECT status, COUNT(1) FROM queue GROUP BY status")])
rows = list(c.execute("SELECT url, updated_at FROM queue WHERE status = 'PROCESSING'"))
print("PROCESSING count:", len(rows))
for r in rows:
    print(r)
