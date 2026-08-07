import re
import httpx

UA = "GTR-Auto-CatalogBot/1.0 (+https://nissangtrauto.co.zw/bot; megazip-epc)"
hub = "https://www.megazip.net/parts/nissan"

for label, headers in [
    ("crawler-UA", {"User-Agent": UA, "Accept-Language": "en"}),
    ("browser-UA", {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
        "Accept-Language": "en-US,en;q=0.9",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    }),
]:
    try:
        r = httpx.get(hub, headers=headers, timeout=30.0, follow_redirects=True)
        html = r.text
        print(f"\n===== {label} =====")
        print("status:", r.status_code, "| final url:", str(r.url), "| length:", len(html))
        t = re.search(r"<title>([^<]+)</title>", html, re.I)
        print("title:", t.group(1) if t else None)
        print("sil-card:", len(re.findall(r"sil-card", html)))
        print("s-catalog__model-link:", len(re.findall(r"s-catalog__model-link", html)))
        print("ItemList:", len(re.findall(r'"@type"\s*:\s*"ItemList"', html)))
        print("data-name=:", len(re.findall(r'data-name="', html)))
        print('href="/parts/nissan/...":', len(re.findall(r'href="/parts/nissan/[^"]+"', html)))
        print('href="/zapchasti.../nissan/...":', len(re.findall(r'href="/zapchasti-dlya-avtomobilej/nissan/[^"]+"', html)))
        print("block markers:", bool(re.search(r"cloudflare|captcha|access denied|just a moment|are you human", html, re.I)))
        hrefs = re.findall(r'href="([^"]*/(?:parts|zapchasti-dlya-avtomobilej)/nissan/[^"]+)"', html)
        print("model-shaped hrefs found:", len(hrefs))
        for h in hrefs[:12]:
            print("   ", h)
        if not hrefs:
            # look for any anchors with model names / data attributes
            names = re.findall(r'data-name="([^"]+)"', html)[:12]
            print("data-name samples:", names)
            print("first 800 chars:\n", re.sub(r"\s+", " ", html[:800]))
    except Exception as e:
        print(f"{label} FAILED:", e)
