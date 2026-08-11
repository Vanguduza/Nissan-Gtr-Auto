"""One-off: list Megazip car makers from homepage + /parts hub status."""
from __future__ import annotations

import re
import sys
from pathlib import Path

try:
    import httpx
except ImportError:
    print("httpx required", file=sys.stderr)
    sys.exit(1)

BASE = "https://www.megazip.net"
# Powersports / non-car brands often listed on homepage
EXCLUDE = {
    "yamaha",
    "kawasaki",
    "suzuki-moto",
    "honda-moto",
    "brp",
    "cfmoto",
    "polaris",
    "arctic-cat",
    "sea-doo",
    "ski-doo",
    "can-am",
    "outboard",
}


def main() -> int:
    r = httpx.get(BASE, timeout=60.0, follow_redirects=True)
    r.raise_for_status()
    html = r.text

    order: list[str] = []
    seen: set[str] = set()
    for m in re.finditer(r'href="(/parts/([a-z0-9-]+))"', html, re.I):
        slug = m.group(2).lower()
        if slug in seen or slug in EXCLUDE:
            continue
        seen.add(slug)
        order.append(slug)

    print(f"homepage unique /parts/ slugs (excl powersports filter): {len(order)}")
    for i, slug in enumerate(order, 1):
        print(f"  {i:2d}. {slug}")

    # Probe HTTP status for each
    ok: list[str] = []
    bad: list[tuple[str, int]] = []
    with httpx.Client(timeout=30.0, follow_redirects=True) as client:
        for slug in order:
            resp = client.get(f"{BASE}/parts/{slug}")
            if resp.status_code == 200:
                ok.append(slug)
            else:
                bad.append((slug, resp.status_code))

    print(f"\nHTTP 200 car hubs: {len(ok)}")
    print(", ".join(ok))
    if bad:
        print(f"non-200: {bad}")

    # Compare to config
    cfg_path = Path(__file__).resolve().parents[1] / "config" / "megazip_makers.json"
    import json

    cfg = json.loads(cfg_path.read_text(encoding="utf-8"))
    cfg_slugs = [cfg["maker_slugs"][m] for m in cfg["makers"]]
    missing = [s for s in ok if s not in cfg_slugs]
    extra = [s for s in cfg_slugs if s not in ok]
    print(f"\nconfig makers: {cfg['makers']}")
    print(f"missing from config (in 200 hubs): {missing or 'none'}")
    print(f"in config but not 200: {extra or 'none'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
