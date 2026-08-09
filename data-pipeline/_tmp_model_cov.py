import json
import sqlite3
from pathlib import Path

con = sqlite3.connect(Path("out/megazip/nissan/megazip_state.db"))
hub = con.execute(
    "select payload_json from parsed_pages where page_type='maker_hub' limit 1"
).fetchone()
models = json.loads(hub[0]).get("models") or [] if hub else []
slugs = sorted({m.get("slug") for m in models if m.get("slug")})
visited = {
    r[0]
    for r in con.execute(
        "select distinct model_slug from queue where status='VISITED' "
        "and model_slug!='' and page_type in ('section_list','diagram')"
    )
}
pending = {
    r[0]
    for r in con.execute(
        "select distinct model_slug from queue where status='PENDING' and model_slug!=''"
    )
}
variant = {
    r[0]
    for r in con.execute(
        "select distinct json_extract(payload_json, '$.model_slug') "
        "from parsed_pages where page_type='variant_list'"
    )
    if r[0]
}
print("hub", len(slugs), "deep_visited", len(visited), "pending", len(pending), "variant_lists", len(variant))
never = sorted(set(slugs) - visited - pending)
print("never_deep", len(never))
print("sample", never[:30])
