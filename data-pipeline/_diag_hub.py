import sqlite3
import json
import sys
sys.path.insert(0, ".")
from data_pipeline.megazip.parse_html import classify_megazip_url

con = sqlite3.connect(r"out/megazip/nissan/megazip_state.db")
con.row_factory = sqlite3.Row
cur = con.cursor()

hub_url = "https://www.megazip.net/parts/nissan"
r = cur.execute("SELECT payload_json FROM parsed_pages WHERE url = ?", (hub_url,)).fetchone()
payload = json.loads(r["payload_json"]) if r else {}
models = payload.get("models") or []
print("HUB models stored:", len(models))
for m in models[:30]:
    su = m.get("source_url", "")
    print(f"  {m.get('slug'):30} classify={classify_megazip_url(su):15} {su}")

# How many of those model URLs got enqueued / visited?
print("\nqueue model_catalog rows:")
for r in cur.execute("SELECT status, COUNT(1) FROM queue WHERE page_type='model_catalog' GROUP BY status"):
    print("  ", tuple(r))

# distinct parsed page types
print("\nparsed_pages page_type counts:")
for r in cur.execute("SELECT page_type, COUNT(1) FROM parsed_pages GROUP BY page_type ORDER BY 2 DESC"):
    print("  ", tuple(r))

# check what parse produced for the x-trail model page (variant_list?)
xt = cur.execute("SELECT url, page_type, payload_json FROM parsed_pages WHERE url LIKE '%/parts/nissan/x-trail%' OR url LIKE '%x-trail-2064' LIMIT 3").fetchall()
print("\nx-trail model page(s):")
for row in xt:
    pl = json.loads(row["payload_json"])
    print("  ", row["url"], "| type:", row["page_type"], "| variants:", len(pl.get("variants") or []), "| models:", len(pl.get("models") or []))
