"""Probe Megazip HTML for Engine attributes on variant listings."""
from __future__ import annotations

import re
import sqlite3
from pathlib import Path

import httpx

from data_pipeline.megazip.parse_html import _parse_attrs, cache_key, parse_variant_list

UA = {"User-Agent": "Mozilla/5.0"}


def main() -> None:
    url = "https://www.megazip.net/zapchasti-dlya-avtomobilej/nissan/x-trail-2064"
    r = httpx.get(url, headers=UA, follow_redirects=True, timeout=60)
    print("model_status", r.status_code, "Engine_hits", len(re.findall("Engine", r.text, re.I)))
    attrs = re.findall(
        r"attrs-term[^>]*>\s*([^<]+)\s*</dt>\s*<dd[^>]*>\s*([^<]+)",
        r.text,
    )
    print("unique_terms", sorted({a[0].strip() for a in attrs})[:40])
    parsed = parse_variant_list(r.text, url, "nissan", "x-trail-2064")
    vs = parsed.payload.get("variants") or []
    print("variants", len(vs), "sample", vs[:2] if vs else None)

    # Prefer a cached variant_list HTML if present
    db = Path("out/megazip/nissan/megazip_state.db")
    con = sqlite3.connect(db)
    row = con.execute(
        "select url from parsed_pages where page_type='variant_list' limit 1"
    ).fetchone()
    if row:
        u = row[0]
        cache = Path("out/megazip/nissan/cache") / f"{cache_key(u)}.html"
        print("cache_exists", cache.exists(), u)
        if cache.exists():
            html = cache.read_text(encoding="utf-8", errors="ignore")
            print("cache Engine_hits", len(re.findall("Engine", html, re.I)))
            print(
                "cache terms",
                sorted(
                    {
                        a[0].strip()
                        for a in re.findall(
                            r"attrs-term[^>]*>\s*([^<]+)\s*</dt>\s*<dd[^>]*>\s*([^<]+)",
                            html,
                        )
                    }
                ),
            )
            # show first block attrs via parser helper
            from data_pipeline.megazip import parse_html as ph

            for data_id, block in ph._VARIANT_ITEM.findall(html)[:3]:
                print("block attrs", data_id, _parse_attrs(block))


if __name__ == "__main__":
    main()
