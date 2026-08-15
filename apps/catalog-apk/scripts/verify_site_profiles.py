#!/usr/bin/env python3
"""Live smoke-test for Catalog APK hardcoded site profiles.

Verifies:
  1) each preset JSON loads
  2) hub + sample maker URLs return HTTP 200
  3) maker/model link patterns appear in HTML (engine-specific)

Usage:
  python apps/catalog-apk/scripts/verify_site_profiles.py
"""

from __future__ import annotations

import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROFILES = ROOT / "app" / "src" / "main" / "assets" / "site_profiles"
UA = "GTR-CatalogApk-ProfileVerify/1.0 (+research)"

CHECKS = {
    "megazip.json": {
        "hubs": ["https://www.megazip.net/", "https://www.megazip.net/parts/nissan"],
        "maker_re": re.compile(r'href="(/parts/[a-z0-9-]+)"', re.I),
        "min_makers": 8,
    },
    "7zap.json": {
        "hubs": [
            "https://7zap.com/en/catalog/cars/",
            "https://7zap.com/en/catalog/cars/nissan/europe/",
        ],
        "maker_re": re.compile(
            r'"url"\s*:\s*"(https://([a-z0-9-]+)\.7zap\.com/en/[^"]+)"\s*,\s*"name"\s*:\s*"([^"]+)"',
            re.I,
        ),
        "model_re": re.compile(
            r"/en/catalog/cars/nissan/europe/[a-z0-9%-]+-parts-catalog/",
            re.I,
        ),
        "min_makers": 20,
        "min_models": 10,
    },
    "catcar.json": {
        "hubs": [
            "https://www.catcar.info/",
            "https://www.catcar.info/nissan/?lang=en",
        ],
        "maker_re": re.compile(r'href="[^"]*/(nissan|toyota|honda|mazda)/', re.I),
        "min_makers": 4,
    },
    "japancats.json": {
        "hubs": [
            "https://japancats.ru/",
            "https://www.japancats.ru/Nissan/",
        ],
        "maker_re": re.compile(r'href="/(Nissan|Toyota|Honda|Mazda|Subaru)/"', re.I),
        "min_makers": 5,
    },
    "japan_parts.json": {
        "hubs": [
            "https://japan-parts.eu/",
            "https://japan-parts.eu/toyota/eu/2010/",
        ],
        "maker_re": re.compile(r'href="/(toyota|lexus)/?"', re.I),
        "model_re": re.compile(r'href="/toyota/eu/2010/[a-z0-9-]+"', re.I),
        "min_makers": 1,
        "min_models": 5,
        "notes": "japan-parts.ru is a domain-for-sale page; preset targets japan-parts.eu",
    },
}


def fetch(url: str) -> tuple[int, str]:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": UA,
            "Accept": "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            return int(resp.status), resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", "replace") if exc.fp else ""
        return int(exc.code), body


def main() -> int:
    failures = 0
    # Confirm japan-parts.ru is NOT a catalog (document, do not ship as hub).
    code, body = fetch("http://japan-parts.ru/")
    if code == 200 and ("продается" in body.lower() or "domain" in body.lower() or "прода" in body):
        print("NOTE japan-parts.ru -> for-sale parking page (as expected); using japan-parts.eu")
    else:
        print(f"WARN japan-parts.ru probe code={code} (expected for-sale page)")

    for name, spec in CHECKS.items():
        path = PROFILES / name
        print(f"\n=== {name} ===")
        if not path.is_file():
            print(f"FAIL missing {path}")
            failures += 1
            continue
        try:
            profile = json.loads(path.read_text(encoding="utf-8"))
            assert profile.get("id") and profile.get("engine") and profile.get("base_url")
            assert "paths" in profile and profile["paths"].get("parts_hub") is not None
            print(f"OK json id={profile['id']} engine={profile['engine']} base={profile['base_url']}")
        except Exception as exc:  # noqa: BLE001
            print(f"FAIL json: {exc}")
            failures += 1
            continue

        if spec.get("notes"):
            print(f"NOTE {spec['notes']}")

        hub_html = ""
        for hub in spec["hubs"]:
            code, html = fetch(hub)
            ok = code == 200 and len(html) > 500
            print(f"{'OK' if ok else 'FAIL'} HTTP {code} bytes={len(html)} {hub}")
            if not ok:
                failures += 1
            else:
                hub_html = html if not hub_html else hub_html
                # Prefer maker-list pages for maker_re when second hub is maker page.
                if "parts/nissan" in hub or "catalog/cars/" in hub and hub.rstrip("/").endswith("cars"):
                    hub_html = html
                if hub.rstrip("/").endswith("japancats.ru") or hub.rstrip("/").endswith("catcar.info"):
                    hub_html = html
                if hub.rstrip("/").endswith("japan-parts.eu"):
                    hub_html = html

        makers = {m.group(0) for m in spec["maker_re"].finditer(hub_html)}
        # For 7zap use brand capture group count
        if name == "7zap.json":
            makers = {m.group(2).lower() for m in spec["maker_re"].finditer(hub_html)}
        print(f"makers_found={len(makers)} (min {spec['min_makers']})")
        if len(makers) < int(spec["min_makers"]):
            print("FAIL maker pattern count")
            failures += 1
        else:
            print("OK makers")

        model_re = spec.get("model_re")
        if model_re is not None:
            # fetch last hub for models
            code, html = fetch(spec["hubs"][-1])
            models = list(model_re.finditer(html))
            print(f"models_found={len(models)} (min {spec.get('min_models', 1)}) HTTP {code}")
            if code != 200 or len(models) < int(spec.get("min_models", 1)):
                print("FAIL model pattern count")
                failures += 1
            else:
                print("OK models")

    # partsouq optional — still shipped
    ps = PROFILES / "partsouq.json"
    if ps.is_file():
        json.loads(ps.read_text(encoding="utf-8"))
        print("\nOK partsouq.json present (not re-probed in this pass)")

    print(f"\nRESULT {'PASS' if failures == 0 else f'FAIL ({failures})'}")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
