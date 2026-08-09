"""Merge small fixtures and live-import flat catalog + EPC maker/model/variant roots.

Uses repo-root .env last so hosted SoR wins. Does not print credentials.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[2]
PIPELINE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PIPELINE))

from data_pipeline.import_catalog import (  # noqa: E402
    import_supabase,
    load_env_files,
    resolve_supabase_credentials,
)
from data_pipeline.import_hierarchy_catalog import (  # noqa: E402
    _HIERARCHY_TABLES,
    import_hierarchy_supabase,
)
from data_pipeline.validate import validate_bundle  # noqa: E402


def _merge_list(paths: list[Path], key_fn) -> list[dict]:
    seen: set = set()
    out: list[dict] = []
    for path in paths:
        rows = json.loads(path.read_text(encoding="utf-8"))
        for row in rows:
            k = key_fn(row)
            if k in seen:
                continue
            seen.add(k)
            out.append(row)
    return out


def main() -> int:
    load_env_files(PIPELINE / ".env", ROOT / ".env", override=True)
    url, key = resolve_supabase_credentials()
    host = urlparse(url or "").hostname or ""
    print(f"target host={host}")
    if not url or not key:
        print("ERROR: missing Supabase URL or privileged key in .env")
        return 2
    if host in {"127.0.0.1", "localhost"}:
        print("ERROR: refusing local Docker target — set hosted URL in repo-root .env")
        return 2

    fixtures = [
        PIPELINE / "fixtures" / "navara_d40_yd25",
        PIPELINE / "fixtures" / "xtrail_t31_mr20",
    ]
    vehicles = _merge_list(
        [f / "vehicle_master.json" for f in fixtures],
        lambda r: (
            r.get("vin_prefix") or "",
            r.get("chassis_code"),
            r.get("engine_code") or "",
            r.get("production_year") or 0,
            r.get("model_variant"),
        ),
    )
    pncs = _merge_list(
        [f / "pnc_categories.json" for f in fixtures],
        lambda r: r.get("pnc_code"),
    )
    fits = _merge_list(
        [f / "part_fitment.json" for f in fixtures],
        lambda r: (
            r.get("oem_part_number"),
            r.get("chassis_code") or "",
            r.get("engine_code") or "",
            r.get("pnc_code") or "",
        ),
    )
    diagrams: list[dict] = []
    for f in fixtures:
        p = f / "diagram_assets.json"
        if p.is_file():
            diagrams.extend(json.loads(p.read_text(encoding="utf-8")))

    bundle = {
        "vehicle_master": vehicles,
        "pnc_categories": pncs,
        "part_fitment": fits,
        "diagram_assets": diagrams,
    }
    validate_bundle(bundle)
    print(
        f"flat pack vehicles={len(vehicles)} pncs={len(pncs)} "
        f"fitments={len(fits)} diagrams={len(diagrams)}"
    )

    result = import_supabase(
        bundle,
        url=url,
        key=key,
        ensure_stock_items=True,
        prune_stale=False,
    )
    for name, stats in sorted(result.stats.items()):
        print(
            f"  {name}: +{getattr(stats, 'inserted', stats)} "
            f"~{getattr(stats, 'updated', 0)} ={getattr(stats, 'unchanged', 0)}"
        )
    for note in result.notes:
        print(f"  # {note}")

    # Hierarchy roots only (skip 88k sections / 1.8M parts — too large for smoke reload).
    megazip = PIPELINE / "out" / "megazip" / "nissan" / "bundle"
    if megazip.is_dir():
        hier: dict = {t: [] for t in _HIERARCHY_TABLES}
        for name in ("catalog_makers", "catalog_models", "catalog_variants"):
            path = megazip / f"{name}.json"
            if path.is_file():
                hier[name] = json.loads(path.read_text(encoding="utf-8"))
        print(
            f"hierarchy roots makers={len(hier['catalog_makers'])} "
            f"models={len(hier['catalog_models'])} "
            f"variants={len(hier['catalog_variants'])}"
        )
        hres = import_hierarchy_supabase(
            hier,
            url=url,
            key=key,
            ensure_stock_items=False,
            prune_stale=False,
        )
        for name, stats in sorted(hres.stats.items()):
            print(
                f"  {name}: +{getattr(stats, 'inserted', stats)} "
                f"~{getattr(stats, 'updated', 0)}"
            )
        for note in hres.notes:
            print(f"  # {note}")
    else:
        print("megazip bundle missing — skipped hierarchy roots")

    print("DONE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
