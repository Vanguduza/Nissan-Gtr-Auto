import sqlite3
import sys
import re
from pathlib import Path
sys.path.insert(0, ".")
from data_pipeline.megazip.parse_html import cache_key

con = sqlite3.connect(r"out/megazip/nissan/megazip_state.db")
con.row_factory = sqlite3.Row
cur = con.cursor()
hub_url = "https://www.megazip.net/parts/nissan"

r = cur.execute("SELECT cache_path FROM page_cache WHERE url = ?", (hub_url,)).fetchone()
print("stored cache_path:", r["cache_path"] if r else None)

ck = cache_key(hub_url)
print("cache_key:", ck)
cand = Path("out/megazip/nissan/cache") / f"{ck}.html"
print("candidate exists:", cand.is_file(), cand)

path = None
if r and Path(r["cache_path"]).is_file():
    path = Path(r["cache_path"])
elif cand.is_file():
    path = cand

if not path:
    # brute: find any cache file whose content references parts/nissan hub title
    print("searching cache dir...")
    for f in Path("out/megazip/nissan/cache").glob("*.html"):
        txt = f.read_text(encoding="utf-8", errors="ignore")
        if "<title>" in txt and "nissan" in txt.lower() and ("sil-card" in txt or "ItemList" in txt):
            # heuristics; check it is the hub (many model links)
            n = len(re.findall(r'href="/parts/nissan/[^"]+"', txt))
            if n > 5:
                path = f
                print("  found likely hub:", f, "model-link count:", n)
                break

if path:
    html = path.read_text(encoding="utf-8", errors="ignore")
    print("\nHTML length:", len(html))
    t = re.search(r"<title>([^<]+)</title>", html, re.I)
    print("title:", t.group(1) if t else None)
    print("sil-card:", len(re.findall(r"sil-card", html)))
    print("s-catalog__model-link:", len(re.findall(r"s-catalog__model-link", html)))
    print("ItemList:", len(re.findall(r'"@type"\s*:\s*"ItemList"', html)))
    print("data-name=:", len(re.findall(r'data-name="', html)))
    print('href="/parts/nissan/...":', len(re.findall(r'href="/parts/nissan/[^"]+"', html)))
    print('href="/zapchasti.../nissan/...":', len(re.findall(r'href="/zapchasti-dlya-avtomobilej/nissan/[^"]+"', html)))
    print("mentions Cloudflare/blocked/captcha:", bool(re.search(r"cloudflare|captcha|access denied|just a moment", html, re.I)))
    # show sample anchor hrefs containing nissan
    hrefs = re.findall(r'href="([^"]*nissan[^"]*)"', html)
    print("\nsample nissan hrefs (first 20):")
    for h in hrefs[:20]:
        print("   ", h)
    # dump a window around first model-ish anchor
    m = re.search(r'href="(/(?:parts|zapchasti-dlya-avtomobilej)/nissan/[^"]+)"', html)
    if m:
        i = m.start()
        print("\ncontext around first model href:\n", html[max(0,i-300):i+300])
    else:
        print("\nNO model href of expected shape found in hub HTML")
        # print a chunk of body to see structure
        body = re.search(r"<body.*?>(.*)</body>", html, re.S | re.I)
        snippet = (body.group(1) if body else html)[:1500]
        print("body snippet:\n", re.sub(r"\s+", " ", snippet))
else:
    print("could not locate hub cache file")
