import json
import sqlite3
from pathlib import Path

con = sqlite3.connect(Path("out/megazip/nissan/megazip_state.db"))
for s in ["350z-2133", "cube-2138", "frontier-2140", "x-trail-2064", "navara-2141"]:
    n = con.execute(
        "select count(*) from parsed_pages where page_type=? and payload_json like ?",
        ("variant_list", f'%"{s}"%'),
    ).fetchone()[0]
    print(s, "variant_like", n)
    row = con.execute(
        "select payload_json from parsed_pages where page_type='variant_list' and payload_json like ? limit 1",
        (f'%"{s}"%',),
    ).fetchone()
    if row:
        payload = json.loads(row[0])
        vs = payload.get("variants") or []
        print(
            "  model_slug",
            payload.get("model_slug"),
            "variants",
            len(vs),
            "chassis",
            [v.get("chassis_code") for v in vs[:4]],
        )
