#!/usr/bin/env python3
"""Harvest one Catalog APK site catalog. Usage: python harvest_one.py japan_parts"""

from __future__ import annotations

import json
import sys
from pathlib import Path

# Reuse helpers from harvest_site_catalogs
sys.path.insert(0, str(Path(__file__).resolve().parent))
import harvest_site_catalogs as H  # noqa: E402


def japan_parts_fixed() -> None:
    print("=== japan_parts (homepage years) ===", flush=True)
    base = "https://japan-parts.eu"
    st, html = H.fetch(base + "/")
    print(f"  home {st} bytes={len(html)}", flush=True)
    out = []
    for slug, name in [("toyota", "Toyota"), ("lexus", "Lexus")]:
        # Prefer maker page (Lexus years are /lexus//YYYY/ without region on home)
        st_m, page = H.fetch(f"{base}/{slug}")
        blob = page + "\n" + html
        models = {}
        for m in H.re.finditer(
            rf'href="(/{slug}/(?:([a-z]{{2}})/)?(\d{{4}})/?)"',
            blob,
            H.re.I,
        ):
            href, region, year = m.group(1), (m.group(2) or "eu").lower(), m.group(3)
            # normalize broken /lexus//2014/ -> /lexus/eu/2014/
            if f"/{slug}//" in href or href.count("/") < 4:
                href = f"/{slug}/{region}/{year}/"
            mslug = f"{region}-{year}"
            models.setdefault(
                mslug,
                {
                    "display_name": f"{region.upper()} {year}",
                    "slug": mslug,
                    "source_url": H._join(base, href),
                    "chassis": [],
                },
            )
        # also region hubs may list years
        for region in ("eu", "us", "jp", "gr"):
            H.time.sleep(H.DELAY)
            st_r, rhtml = H.fetch(f"{base}/{slug}/{region}")
            for m in H.re.finditer(
                rf'href="(/{slug}/{region}/(\d{{4}})/?)"',
                rhtml,
                H.re.I,
            ):
                href, year = m.group(1), m.group(2)
                mslug = f"{region}-{year}"
                models.setdefault(
                    mslug,
                    {
                        "display_name": f"{region.upper()} {year}",
                        "slug": mslug,
                        "source_url": H._join(base, href),
                        "chassis": [],
                    },
                )
        print(f"  {slug} years={len(models)}", flush=True)
        for mslug, model in list(models.items()):
            H.time.sleep(H.DELAY)
            st2, mhtml = H.fetch(model["source_url"])
            chassis = {}
            for cm in H.re.finditer(
                rf'href="(/{slug}/([a-z]{{2}})/(\d{{4}})/([a-z0-9-]+)/?)"',
                mhtml,
                H.re.I,
            ):
                href, region, year, nslug = cm.groups()
                tokens = list(H.re.finditer(r"([a-z]{1,3}\d{1,3}[a-z0-9]*)", nslug, H.re.I))
                code = tokens[-1].group(1).upper() if tokens else nslug[:12].upper()
                chassis[nslug] = {
                    "code": code,
                    "variant_slug": nslug,
                    "frame": nslug.replace("-", " "),
                    "year_label": year,
                    "engine_code": "",
                    "source_url": H._join(base, href),
                }
            model["chassis"] = list(chassis.values()) or [
                {
                    "code": mslug.upper(),
                    "variant_slug": mslug,
                    "frame": model["display_name"],
                    "year_label": mslug,
                    "engine_code": "",
                    "source_url": model["source_url"],
                }
            ]
        out.append(
            {
                "name": name,
                "slug": slug,
                "source_url": f"{base}/{slug}",
                "models": sorted(models.values(), key=lambda x: x["slug"], reverse=True),
            }
        )
        H.dump("japan_parts", out)
    H.dump("japan_parts", out)


SITES = {
    "japan_parts": japan_parts_fixed,
    "japancats": H.harvest_japancats,
    "catcar": H.harvest_catcar,
    "7zap": H.harvest_7zap,
    "megazip": H.harvest_megazip,
    "partsouq": H.harvest_partsouq,
}


def main() -> int:
    name = sys.argv[1] if len(sys.argv) > 1 else ""
    if name not in SITES:
        print("usage: harvest_one.py [" + "|".join(SITES) + "]", flush=True)
        return 2
    SITES[name]()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
