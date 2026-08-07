import sqlite3
import json
import sys
from pathlib import Path

sys.path.insert(0, ".")
from data_pipeline.megazip.parse_html import (
    parse_maker_hub,
    classify_megazip_url,
    parse_html_page,
)

db = r"out/megazip/nissan/megazip_state.db"
con = sqlite3.connect(db)
con.row_factory = sqlite3.Row
cur = con.cursor()

hub_url = "https://www.megazip.net/parts/nissan"
row = cur.execute("SELECT url, cache_path FROM page_cache WHERE url = ?", (hub_url,)).fetchone()
print("hub cache row:", None if not row else row["cache_path"])
if not row:
    # try LIKE
    for r in cur.execute("SELECT url, cache_path FROM page_cache WHERE url LIKE '%/parts/nissan%'"):
        print("  candidate:", tuple(r))

# what parsed_pages recorded for the hub
pr = cur.execute("SELECT url, page_type FROM parsed_pages WHERE url = ?", (hub_url,)).fetchone()
print("parsed_pages hub page_type:", None if not pr else pr["page_type"])

if row:
    html = Path(row["cache_path"]).read_text(encoding="utf-8", errors="ignore")
    print("hub html length:", len(html))
    print("classify(hub_url):", classify_megazip_url(hub_url))
    parsed = parse_maker_hub(html, hub_url, "nissan")
    models = parsed.payload.get("models") or []
    print("parse_maker_hub MODELS FOUND:", len(models))
    for m in models[:15]:
        print("   ", m["slug"], "->", m["source_url"], "| classify:", classify_megazip_url(m["source_url"]))
    # also run through the real dispatch parse_html_page (as crawl does)
    dispatched = parse_html_page(html, hub_url, maker_slug="nissan")
    print("parse_html_page dispatch page_type:", dispatched.page_type,
          "models:", len(dispatched.payload.get("models") or []),
          "variants:", len(dispatched.payload.get("variants") or []))

# raw signal: count sil-card / model-link / ld+json ItemList in hub html
if row:
    import re
    print("sil-card count:", len(re.findall(r"sil-card", html)))
    print("s-catalog__model-link count:", len(re.findall(r"s-catalog__model-link", html)))
    print("ld+json ItemList count:", len(re.findall(r'"@type"\s*:\s*"ItemList"', html)))
    print("href /parts/nissan/ occurrences:", len(re.findall(r'href="(/parts/nissan/[^"]+)"', html)))
    print("href /zapchasti.../nissan/ occurrences:", len(re.findall(r'href="(/zapchasti-dlya-avtomobilej/nissan/[^"]+)"', html)))
    # sample hrefs
    sample = re.findall(r'href="(/(?:parts|zapchasti-dlya-avtomobilej)/nissan/[^"]+)"', html)[:12]
    for s in sample:
        print("   href sample:", s, "| classify:", classify_megazip_url("https://www.megazip.net"+s))
