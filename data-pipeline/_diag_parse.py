import re
import httpx
import sys
sys.path.insert(0, ".")
from data_pipeline.megazip.parse_html import (
    parse_maker_hub, _MAKER_HUB_MODEL, _is_maker_model_catalog_href, _clean,
)

UA = "GTR-Auto-CatalogBot/1.0 (+https://nissangtrauto.co.zw/bot; megazip-epc)"
hub = "https://www.megazip.net/parts/nissan"
html = httpx.get(hub, headers={"User-Agent": UA}, timeout=30.0, follow_redirects=True).text

# Run the actual parser
parsed = parse_maker_hub(html, hub, "nissan")
print("parse_maker_hub models:", len(parsed.payload.get("models") or []))

# Loop 1: _MAKER_HUB_MODEL
m1 = _MAKER_HUB_MODEL.findall(html)
print("\n[loop1] _MAKER_HUB_MODEL raw matches:", len(m1))
passed1 = [(h, _clean(l)) for h, l in m1 if _is_maker_model_catalog_href(h, "nissan")]
print("[loop1] passing _is_maker_model_catalog_href:", len(passed1))
for h, l in m1[:6]:
    print("   href=", h[:70], "| is_model:", _is_maker_model_catalog_href(h, "nissan"), "| label=", repr(_clean(l)[:40]))

# Loop 2: sil-card anchors
loop2 = re.findall(r'<a\s+([^>]*?\shref="(/(?:parts|zapchasti-dlya-avtomobilej)/[^"]+)"[^>]*)>', html, re.I | re.S)
print("\n[loop2] anchor regex matches:", len(loop2))
sil_ok = [(a, h) for a, h in loop2 if "nissan" in h.lower() and ("sil-card" in a or "s-catalog__model-link" in a)]
print("[loop2] with sil-card/model-link in anchor attrs:", len(sil_ok))

# Where does sil-card actually live? show one card
i = html.find("sil-card")
print("\ncontext around first 'sil-card':\n", re.sub(r"\s+"," ", html[max(0,i-200):i+400]))

# Is the model href wrapped: find an x-trail anchor and show surrounding tag
j = html.find("x-trail-2064")
print("\ncontext around x-trail anchor:\n", re.sub(r"\s+"," ", html[max(0,j-400):j+200]))

# ItemList JSON-LD
for block in re.finditer(r'<script type="application/ld\+json">(.*?)</script>', html, re.I|re.S):
    import json
    try:
        data = json.loads(block.group(1))
    except Exception as e:
        print("ld json parse err:", e); continue
    if isinstance(data, dict) and data.get("@type")=="ItemList":
        els = data.get("itemListElement") or []
        print("\nItemList elements:", len(els))
        for it in els[:4]:
            print("   ", it if not isinstance(it, dict) else {k: it.get(k) for k in ("url","name")})
