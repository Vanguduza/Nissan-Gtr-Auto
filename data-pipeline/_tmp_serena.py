import sqlite3
import json
from pathlib import Path

db = Path(r"out/megazip/nissan/megazip_state.db")
c = sqlite3.connect(db)
model = "serena-2090"

print("=== queue Serena ===")
for r in c.execute(
    "SELECT page_type, status, COUNT(1) FROM queue WHERE model_slug=? GROUP BY page_type, status ORDER BY 1,2",
    (model,),
):
    print(" ", r)

print("\n=== parsed_pages Serena ===")
n = c.execute(
    "SELECT COUNT(1) FROM parsed_pages WHERE url LIKE ?",
    (f"%/{model}/%",),
).fetchone()[0]
print("  parsed rows with model in url:", n)
# sample diagram payload completeness
rows = c.execute(
    """
    SELECT page_type, payload_json FROM parsed_pages
    WHERE url LIKE ? AND page_type='diagram'
    LIMIT 3
    """,
    (f"%/{model}/%",),
).fetchall()
for ptype, payload in rows:
    p = json.loads(payload)
    parts = p.get("parts") or p.get("parts_table") or []
    print(
        f"  sample diagram: kind={p.get('diagram_kind')} parts={len(parts)} "
        f"img={bool(p.get('image_url'))} w={p.get('image_width')} h={p.get('image_height')} "
        f"title={str(p.get('title') or '')[:50]}"
    )

print("\n=== page_cache Serena ===")
print(
    "  cache rows:",
    c.execute("SELECT COUNT(1) FROM page_cache WHERE url LIKE ?", (f"%/{model}/%",)).fetchone()[0],
)

# chassis codes for serena
print("\nchassis:", [r[0] for r in c.execute(
    "SELECT DISTINCT chassis_code FROM queue WHERE model_slug=? AND chassis_code!=''",
    (model,),
)])
