import sqlite3

c = sqlite3.connect("crawler_state.db")
print("CRAWL:", dict(c.execute("SELECT status, COUNT(*) FROM queue GROUP BY status")))
v = c.execute(
    """
    SELECT
      SUM(status='VISITED'),
      SUM(status='PENDING'),
      SUM(status='BLOCKED_CF'),
      SUM(status='PROCESSING')
    FROM queue WHERE url LIKE '%/vehicle%'
    """
).fetchone()
print("vehicle visited/pending/blocked/processing:", v)
print(
    "last VISITED:",
    c.execute(
        "SELECT updated_at FROM queue WHERE status='VISITED' ORDER BY updated_at DESC LIMIT 1"
    ).fetchone()[0],
)
print("scraped_data:", c.execute("SELECT COUNT(*) FROM scraped_data").fetchone()[0])
p = sqlite3.connect("out/cache_parse_state.db")
print("PARSE:", dict(p.execute("SELECT status, COUNT(*) FROM parse_queue GROUP BY status")))
