#!/usr/bin/env python3
"""Enrich partsouq catalog: keep chassis_catalogs rows; fill gaps from 7zap OEM trees."""
from __future__ import annotations

import json
import re
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CAT = ROOT / "app" / "src" / "main" / "assets" / "site_catalogs"
BASE = "https://partsouq.com"


def slugify(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def main() -> None:
    partsouq = json.loads((CAT / "partsouq.json").read_text(encoding="utf-8"))
    seven = json.loads((CAT / "7zap.json").read_text(encoding="utf-8"))
    by7 = {slugify(m["name"]): m for m in seven["makers"]}
    by7.update({m["slug"]: m for m in seven["makers"]})
    # alias map
    aliases = {
        "mercedes-benz": "mercedes",
        "land-rover": "land-rover",
        "alfa-romeo": "alfa-romeo",
        "volkswagen": "volkswagen",
        "mini": "mini",
    }
    out = []
    for maker in partsouq["makers"]:
        slug = maker["slug"]
        # already has real chassis beyond placeholder?
        real = sum(len(m.get("chassis") or []) for m in maker.get("models") or [])
        src = by7.get(slug) or by7.get(aliases.get(slug, slug))
        src_ch = 0
        if src:
            src_ch = sum(len(mo.get("chassis") or []) for mo in src.get("models") or [])
        if src and src_ch > real:
            models = []
            for mo in src.get("models") or []:
                chassis = []
                for ch in mo.get("chassis") or []:
                    code = (ch.get("code") or mo.get("slug") or "").upper()
                    chassis.append(
                        {
                            "code": code,
                            "variant_slug": ch.get("variant_slug") or code.lower(),
                            "frame": ch.get("frame") or mo.get("display_name") or code,
                            "year_label": ch.get("year_label") or "",
                            "engine_code": ch.get("engine_code") or "",
                            "source_url": f"{BASE}/en/catalog/genuine/unit?c={maker['name']}&q={code}",
                        }
                    )
                if not chassis:
                    continue
                models.append(
                    {
                        "display_name": mo.get("display_name") or mo.get("slug"),
                        "slug": mo.get("slug") or slugify(mo.get("display_name") or "model"),
                        "source_url": f"{BASE}/en/catalog/genuine/locate?c={maker['name']}",
                        "chassis": chassis,
                    }
                )
            if models:
                maker = {
                    **maker,
                    "models": models,
                }
        out.append(maker)
    payload = {"id": "partsouq", "harvested_at": time.strftime("%Y-%m-%d"), "makers": out}
    (CAT / "partsouq.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    n_models = sum(len(m["models"]) for m in out)
    n_ch = sum(len(mo["chassis"]) for m in out for mo in m["models"])
    print(f"partsouq enriched: makers={len(out)} models={n_models} chassis={n_ch}")


if __name__ == "__main__":
    main()
