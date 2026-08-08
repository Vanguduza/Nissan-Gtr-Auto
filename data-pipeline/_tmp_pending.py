import sqlite3
c = sqlite3.connect(r"out/megazip/nissan/megazip_state.db")
print("PENDING diagrams by model (top 25):")
for r in c.execute("""
  SELECT model_slug, COUNT(1) AS c
  FROM queue
  WHERE status='PENDING' AND page_type='diagram' AND model_slug != ''
  GROUP BY model_slug
  ORDER BY c DESC
  LIMIT 25
"""):
    print(f"  {r[0]:40} {r[1]}")
print("\nPROCESSING:")
for r in c.execute("SELECT model_slug, url FROM queue WHERE status='PROCESSING'"):
    print(" ", r[0], r[1][-80:])
print("\nVISITED diagrams by model (top 10):")
for r in c.execute("""
  SELECT model_slug, COUNT(1) FROM queue
  WHERE status='VISITED' AND page_type='diagram' AND model_slug != ''
  GROUP BY model_slug ORDER BY 2 DESC LIMIT 10
"""):
    print(" ", r)
