import sqlite3
import time

db = r"out/megazip/nissan/megazip_state.db"
time.sleep(5)
c = sqlite3.connect(db)
rows = list(c.execute(
    "SELECT url, updated_at FROM queue WHERE status = 'PROCESSING' ORDER BY updated_at"
))
print("PROCESSING after wait:", len(rows))
for r in rows:
    print(r)

if len(rows) >= 2:
    # Keep newest (likely live worker); reset older orphan(s)
    for url, updated_at in rows[:-1]:
        c.execute(
            "UPDATE queue SET status = 'PENDING', updated_at = datetime('now') WHERE url = ? AND status = 'PROCESSING'",
            (url,),
        )
        print("requeued orphan:", updated_at, url[:80])
    c.commit()
elif len(rows) == 1:
    print("single PROCESSING — assume live worker owns it; no reset")
else:
    print("no PROCESSING rows")

print("status now:", [tuple(r) for r in c.execute("SELECT status, COUNT(1) FROM queue GROUP BY status")])
c.close()
