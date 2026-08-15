#!/usr/bin/env python3
"""Build partsouq site_catalog from data-pipeline chassis_catalogs + makers list."""
from __future__ import annotations

import json
import re
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "site_catalogs" / "partsouq.json"
MAKERS = ROOT / "app" / "src" / "main" / "python" / "config" / "partsouq_makers.json"
CHASSIS = REPO / "data-pipeline" / "config" / "chassis_catalogs.json"
BASE = "https://partsouq.com"


def slugify(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def main() -> None:
    makers_list = json.loads(MAKERS.read_text(encoding="utf-8")).get("makers") or []
    catalogs = json.loads(CHASSIS.read_text(encoding="utf-8")).get("makers") or {}
    out_makers = []
    for name in makers_list:
        key = slugify(name)
        # chassis_catalogs keys are lowercase maker ids
        cat = catalogs.get(key) or catalogs.get(name.lower()) or {}
        chassis_map = cat.get("chassis") or {}
        # group chassis by model_variant family when possible
        models: dict[str, dict] = {}
        if chassis_map:
            for code, meta in chassis_map.items():
                variant = (meta or {}).get("model_variant") or code
                # group by first two words of variant or code family
                mslug = slugify(re.sub(r"\b" + re.escape(code) + r"\b", "", variant, flags=re.I).strip() or variant)
                if not mslug:
                    mslug = slugify(variant)
                rec = models.setdefault(
                    mslug,
                    {
                        "display_name": re.sub(r"\s+" + re.escape(code) + r"\s*$", "", variant, flags=re.I).strip()
                        or variant,
                        "slug": mslug,
                        "source_url": f"{BASE}/en/catalog/genuine/unit?c={name}&q={code}",
                        "chassis": [],
                    },
                )
                years = (meta or {}).get("year_range") or []
                year_label = f"{years[0]}-{years[-1]}" if len(years) >= 2 else ""
                engines = (meta or {}).get("engines") or []
                rec["chassis"].append(
                    {
                        "code": str(code).upper(),
                        "variant_slug": str(code).lower(),
                        "frame": variant,
                        "year_label": year_label,
                        "engine_code": ",".join(engines[:3]),
                        "source_url": f"{BASE}/en/catalog/genuine/unit?c={name}&q={code}",
                    }
                )
        if not models:
            # curated maker with no chassis map yet — one placeholder model so UI works
            models["genuine"] = {
                "display_name": "Genuine catalog",
                "slug": "genuine",
                "source_url": f"{BASE}/en/catalog/genuine/locate?c={name}",
                "chassis": [
                    {
                        "code": key[:12].upper(),
                        "variant_slug": key,
                        "frame": name,
                        "year_label": "",
                        "engine_code": "",
                        "source_url": f"{BASE}/en/catalog/genuine/locate?c={name}",
                    }
                ],
            }
        out_makers.append(
            {
                "name": name,
                "slug": key,
                "source_url": f"{BASE}/en/catalog/genuine/locate?c={name}",
                "models": sorted(models.values(), key=lambda x: x["display_name"].lower()),
            }
        )
    payload = {"id": "partsouq", "harvested_at": time.strftime("%Y-%m-%d"), "makers": out_makers}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    n_models = sum(len(m["models"]) for m in out_makers)
    n_ch = sum(len(mo["chassis"]) for m in out_makers for mo in m["models"])
    print(f"wrote {OUT.name}: makers={len(out_makers)} models={n_models} chassis={n_ch}")


if __name__ == "__main__":
    main()
