import sqlite3
c = sqlite3.connect(r"out/megazip/nissan/megazip_state.db")
print("PENDING by model:")
for r in c.execute("""
  SELECT model_slug, COUNT(1) FROM queue
  WHERE status='PENDING' AND page_type='diagram' AND model_slug!=''
  GROUP BY model_slug ORDER BY 2 DESC
"""):
    print(f"  {r[0]:40} {r[1]}")
print("chassis with PENDING:")
for r in c.execute("""
  SELECT chassis_code, model_slug, COUNT(1) FROM queue
  WHERE status='PENDING' AND chassis_code!=''
  GROUP BY chassis_code, model_slug ORDER BY 3 DESC LIMIT 40
"""):
    print(f"  {r[0]:8} {r[1]:35} {r[2]}")
