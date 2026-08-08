import sqlite3
from datetime import datetime, timedelta

c = sqlite3.connect(r"out/megazip/nissan/megazip_state.db")

# Overall diagram backlog
d_v = c.execute("SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='VISITED'").fetchone()[0]
d_p = c.execute("SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='PENDING'").fetchone()[0]
total = d_v + d_p

# Windows: last 10 / 30 / 60 minutes (SQLite datetime('now') is UTC usually)
for label, minutes in [("10m", 10), ("30m", 30), ("60m", 60)]:
    n = c.execute(
        """
        SELECT COUNT(1) FROM queue
        WHERE page_type='diagram' AND status='VISITED'
          AND updated_at >= datetime('now', ?)
        """,
        (f"-{minutes} minutes",),
    ).fetchone()[0]
    rate = n / (minutes / 60.0)
    print(f"last {label}: {n} diagrams -> {rate:.0f}/hour")

# Since fleet restart ~10:04 UTC (12:04 local UTC+2) = 2026-08-08 10:04:00 UTC
# SQLite updated_at from earlier logs looked like local or UTC without timezone.
# Check max updated_at format
print("max updated_at:", c.execute("SELECT MAX(updated_at) FROM queue").fetchone()[0])
print("now sqlite:", c.execute("SELECT datetime('now')").fetchone()[0])
print("now local:", c.execute("SELECT datetime('now','localtime')").fetchone()[0])

# Rate by model last 15 minutes
print("\nPer-model last 15m:")
for r in c.execute(
    """
    SELECT model_slug, COUNT(1) FROM queue
    WHERE page_type='diagram' AND status='VISITED'
      AND updated_at >= datetime('now', '-15 minutes')
      AND model_slug != ''
    GROUP BY model_slug ORDER BY 2 DESC
    """
):
    print(f"  {r[0]:40} {r[1]}  ({r[1]*4}/hr)")

# Also try localtime window if clock skew
print("\nUsing localtime clock for windows:")
for label, minutes in [("10m", 10), ("30m", 30)]:
    n = c.execute(
        """
        SELECT COUNT(1) FROM queue
        WHERE page_type='diagram' AND status='VISITED'
          AND updated_at >= datetime('now', 'localtime', ?)
        """,
        (f"-{minutes} minutes",),
    ).fetchone()[0]
    print(f"  last {label} (localtime cmp): {n} -> {n/(minutes/60):.0f}/hr")

# If updated_at is stored as UTC-naive matching datetime('now')
n15 = c.execute(
    """
    SELECT COUNT(1) FROM queue WHERE page_type='diagram' AND status='VISITED'
      AND updated_at >= datetime('now', '-15 minutes')
    """
).fetchone()[0]
rate_hr = n15 / 0.25
eta_h = d_p / rate_hr if rate_hr > 0 else float("inf")
print(f"\ndiagrams visited={d_v} pending={d_p} total={total} pct={100*d_v/total:.1f}%")
print(f"fleet rate (15m window): {rate_hr:.0f} diagrams/hour")
print(f"ETA at current rate: {eta_h:.1f} hours ({eta_h/24:.1f} days)")

# Pending by model ETAs
print("\nPer-model ETA (from 15m rate):")
for r in c.execute(
    """
    SELECT q.model_slug,
      SUM(CASE WHEN q.status='PENDING' THEN 1 ELSE 0 END) AS pend,
      SUM(CASE WHEN q.status='VISITED' AND q.updated_at >= datetime('now','-15 minutes') THEN 1 ELSE 0 END) AS recent
    FROM queue q
    WHERE q.page_type='diagram' AND q.model_slug!=''
    GROUP BY q.model_slug
    HAVING pend > 0
    ORDER BY pend DESC
    """
):
    model, pend, recent = r
    rate = (recent or 0) / 0.25
    eta = pend / rate if rate > 0 else None
    eta_s = f"{eta:.1f}h" if eta is not None else "n/a (0 recent)"
    print(f"  {model:40} pend={pend:6} rate={rate:6.0f}/h  ETA={eta_s}")
