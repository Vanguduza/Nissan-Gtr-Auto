"""Build EPC hierarchy bundle from parsed Megazip pages."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from data_pipeline.megazip.config import MakerPaths
from data_pipeline.megazip.state import load_all_parsed

# Variants must be indexed before diagrams so chassis/engine cascade rows attach
# to the published generation (catalog_variants.chassis_code), not a later Frame.
_PAGE_TYPE_ORDER = {
    "maker_hub": 0,
    "variant_list": 1,
    "model_catalog": 1,
    "section_list": 2,
    "diagram": 3,
}


def _diagram_storage_path(prefix: str, image_url: str, slug: str) -> str:
    if not image_url:
        return f"{prefix}/diagrams/{slug}.png"
    name = urlparse(image_url).path.rsplit("/", 1)[-1]
    return f"{prefix}/{name}"


def _public_catalog_url(url: str | None) -> str | None:
    """Drop vendor crawl URLs so Supabase catalog never stores megazip hosts."""
    if not url:
        return None
    if "megazip" in str(url).lower():
        return None
    return url


def _model_variant_label(
    maker_name: str,
    model_slug: str,
    models: dict[str, dict[str, Any]],
) -> str:
    """Stable cascade label: ``{Maker} {display_name}`` (must match clients exactly)."""
    display = (models.get(model_slug) or {}).get("display_name") or ""
    if not display:
        display = model_slug.replace("-", " ").upper() if model_slug else "UNKNOWN"
    return f"{maker_name} {display}"


def _upsert_vehicle(
    vehicles: dict[tuple[Any, ...], dict[str, Any]],
    *,
    chassis: str,
    engine: str | None,
    model_variant: str,
) -> None:
    if not chassis or not model_variant:
        return
    eng = (engine or "").strip() or None
    key = (None, chassis, eng, None, model_variant)
    row: dict[str, Any] = {
        "chassis_code": chassis,
        "model_variant": model_variant,
    }
    if eng:
        row["engine_code"] = eng
    vehicles[key] = row


def build_hierarchy_bundle(
    paths: MakerPaths,
    *,
    storage_prefix: str,
) -> dict[str, Any]:
    parsed = load_all_parsed(paths.state_db, maker_slug=paths.slug)
    parsed = sorted(
        parsed,
        key=lambda r: (
            _PAGE_TYPE_ORDER.get(str(r.get("page_type") or ""), 99),
            str(r.get("url") or ""),
        ),
    )
    maker_slug = paths.slug
    maker_name = paths.maker

    models: dict[str, dict[str, Any]] = {}
    variants: dict[str, dict[str, Any]] = {}
    sections: dict[str, dict[str, Any]] = {}
    diagrams: dict[str, dict[str, Any]] = {}
    diagram_parts: list[dict[str, Any]] = []
    pncs: dict[str, dict[str, Any]] = {}
    fitments: list[dict[str, Any]] = []
    vehicles: dict[tuple[Any, ...], dict[str, Any]] = {}
    diagram_assets: dict[str, dict[str, Any]] = {}
    oem_names: dict[str, str] = {}

    for row in parsed:
        ptype = row["page_type"]
        payload = row["payload"]
        url = row["url"]

        if ptype == "maker_hub":
            for m in payload.get("models") or []:
                slug = m["slug"]
                models[slug] = {
                    "maker_slug": maker_slug,
                    "slug": slug,
                    "display_name": m["display_name"],
                    "sort_key": m.get("sort_key") or slug.upper(),
                    "source_url": m.get("source_url") or url,
                }

        elif ptype == "variant_list":
            model_slug = payload.get("model_slug") or ""
            if model_slug and model_slug not in models:
                models[model_slug] = {
                    "maker_slug": maker_slug,
                    "slug": model_slug,
                    "display_name": model_slug.replace("-", " ").upper(),
                    "sort_key": model_slug.upper(),
                    "source_url": _public_catalog_url(url),
                }
            for v in payload.get("variants") or []:
                vslug = v["slug"]
                key = (model_slug, vslug)
                engine = (v.get("engine_code") or "").strip() or None
                variants[key] = {
                    "maker_slug": maker_slug,
                    "model_slug": model_slug,
                    "slug": vslug,
                    "chassis_code": v.get("chassis_code") or "",
                    "frame": v.get("frame") or "",
                    "grade": v.get("grade") or "",
                    "sales_region": v.get("sales_region") or "",
                    "year_label": v.get("year_label") or "",
                    "engine_code": engine or "",
                    "external_data_id": v.get("external_data_id")
                    or v.get("megazip_data_id")
                    or "",
                    "source_url": _public_catalog_url(v.get("source_url") or url),
                }
                chassis = v.get("chassis_code") or ""
                if chassis:
                    _upsert_vehicle(
                        vehicles,
                        chassis=chassis,
                        engine=engine,
                        model_variant=_model_variant_label(maker_name, model_slug, models),
                    )

        elif ptype == "section_list":
            model_slug = payload.get("model_slug") or ""
            variant_slug = payload.get("variant_slug") or ""
            for i, s in enumerate(payload.get("sections") or []):
                sslug = s["slug"]
                key = (model_slug, variant_slug, sslug)
                sections[key] = {
                    "maker_slug": maker_slug,
                    "model_slug": model_slug,
                    "variant_slug": variant_slug,
                    "slug": sslug,
                    "name": s.get("name") or sslug,
                    "thumbnail_url": s.get("thumbnail_url"),
                    "sort_order": i,
                    "assembly_group_id": s.get("assembly_group_id") or "",
                    "source_url": _public_catalog_url(s.get("source_url") or url),
                }

        elif ptype == "diagram":
            model_slug = payload.get("model_slug") or ""
            variant_slug = payload.get("variant_slug") or ""
            section_slug = payload.get("section_slug") or ""
            dslug = re.sub(r"[^\w-]", "-", f"{section_slug}-diagram").strip("-")
            image_url = payload.get("image_url") or ""
            storage_path = _diagram_storage_path(storage_prefix, image_url, dslug)
            dkey = (model_slug, variant_slug, section_slug)
            diagrams[dkey] = {
                "maker_slug": maker_slug,
                "model_slug": model_slug,
                "variant_slug": variant_slug,
                "section_slug": section_slug,
                "slug": dslug,
                "title": payload.get("title") or section_slug,
                "image_url": _public_catalog_url(image_url),
                "image_width": payload.get("image_width"),
                "image_height": payload.get("image_height"),
                "diagram_kind": payload.get("diagram_kind") or "ambiguous",
                "hotspot_count": payload.get("hotspot_count") or 0,
                "publish_diagram": (payload.get("diagram_kind") or "ambiguous")
                != "parts_list_raster",
                "storage_path": storage_path,
                "source_url": _public_catalog_url(url),
            }
            if storage_path:
                asset: dict[str, Any] = {
                    "storage_path": storage_path,
                    "content_type": "image/png",
                    "provenance": "scraped-reference",
                    "chassis_code": variants.get((model_slug, variant_slug), {}).get(
                        "chassis_code"
                    )
                    or "",
                }
                pub_img = _public_catalog_url(image_url)
                if pub_img:
                    asset["source_url"] = pub_img
                diagram_assets[storage_path] = asset

            variant_meta = variants.get((model_slug, variant_slug), {})
            variant_chassis = variant_meta.get("chassis_code", "")
            if not variant_chassis:
                for part in payload.get("parts") or []:
                    variant_chassis = (part.get("chassis_code") or "").strip()
                    if variant_chassis:
                        break
            variant_engine = (variant_meta.get("engine_code") or "").strip() or None
            diagram_engine = (payload.get("engine_code") or "").strip() or variant_engine
            if storage_path and variant_chassis:
                diagram_assets[storage_path]["chassis_code"] = variant_chassis
            if storage_path and diagram_engine:
                diagram_assets[storage_path]["engine_code"] = diagram_engine
            if variant_chassis and diagram_engine:
                _upsert_vehicle(
                    vehicles,
                    chassis=variant_chassis,
                    engine=diagram_engine,
                    model_variant=_model_variant_label(maker_name, model_slug, models),
                )
            category_name = payload.get("title") or section_slug.replace("-", " ").title()

            for row in payload.get("parts_table") or []:
                part_row: dict[str, Any] = {
                    "maker_slug": maker_slug,
                    "model_slug": model_slug,
                    "variant_slug": variant_slug,
                    "section_slug": section_slug,
                    "diagram_slug": dslug,
                    "diagram_path": storage_path,
                    "itemslist_id": row.get("itemslist_id") or "",
                    "callout_ref": row.get("callout_ref"),
                    "oem_part_number": row.get("oem_part_number") or "",
                    "description": row.get("description"),
                    "quantity": row.get("quantity"),
                    "external_item_id": row.get("external_item_id")
                    or row.get("megazip_item_id"),
                }
                for k in ("bbox_x", "bbox_y", "bbox_width", "bbox_height"):
                    if row.get(k) is not None:
                        part_row[k] = row[k]
                diagram_parts.append(part_row)

            for part in payload.get("parts") or []:
                oem = part.get("oem_part_number") or ""
                pnc = part.get("pnc_code") or ""
                if pnc:
                    pncs[pnc] = {
                        "pnc_code": pnc,
                        "category_name": category_name,
                        "subcategory_name": sections.get(
                            (model_slug, variant_slug, section_slug), {}
                        ).get("name"),
                        "assembly_group_id": sections.get(
                            (model_slug, variant_slug, section_slug), {}
                        ).get("assembly_group_id"),
                        "catalog_section_path": f"{category_name}",
                    }
                    if (
                        storage_path
                        and pnc
                        and "pnc_code" not in diagram_assets.get(storage_path, {})
                    ):
                        diagram_assets[storage_path]["pnc_code"] = pnc
                fit: dict[str, Any] = {
                    "oem_part_number": oem,
                    "chassis_code": part.get("chassis_code") or variant_chassis,
                    "diagram_path": storage_path,
                }
                if pnc:
                    fit["pnc_code"] = pnc
                engine = (part.get("engine_code") or diagram_engine or "").strip() or None
                if engine:
                    fit["engine_code"] = engine
                for k in ("bbox_x", "bbox_y", "bbox_width", "bbox_height"):
                    if part.get(k) is not None:
                        fit[k] = part[k]
                fitments.append(fit)
                if oem:
                    oem_names[oem] = category_name

    bundle: dict[str, Any] = {
        "catalog_makers": [
            {"slug": maker_slug, "name": maker_name, "sort_order": 0, "source": "epc"}
        ],
        "catalog_models": sorted(models.values(), key=lambda m: m.get("sort_key") or ""),
        "catalog_variants": list(variants.values()),
        "catalog_sections": list(sections.values()),
        "catalog_diagrams": list(diagrams.values()),
        "catalog_diagram_parts": diagram_parts,
        "vehicle_master": list(vehicles.values()),
        "pnc_categories": list(pncs.values()),
        "part_fitment": fitments,
        "diagram_assets": list(diagram_assets.values()),
        "_oem_display_names": oem_names,
    }
    return bundle


def write_bundle(bundle: dict[str, Any], bundle_dir: Path) -> None:
    bundle_dir.mkdir(parents=True, exist_ok=True)
    table_names = (
        "catalog_makers",
        "catalog_models",
        "catalog_variants",
        "catalog_sections",
        "catalog_diagrams",
        "catalog_diagram_parts",
        "vehicle_master",
        "pnc_categories",
        "part_fitment",
        "diagram_assets",
    )
    for name in table_names:
        rows = bundle.get(name) or []
        (bundle_dir / f"{name}.json").write_text(
            json.dumps(rows, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
    oem = bundle.get("_oem_display_names") or {}
    if oem:
        (bundle_dir / "oem_display_names.json").write_text(
            json.dumps(oem, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
    meta = {
        "models": len(bundle.get("catalog_models") or []),
        "variants": len(bundle.get("catalog_variants") or []),
        "sections": len(bundle.get("catalog_sections") or []),
        "diagrams": len(bundle.get("catalog_diagrams") or []),
        "fitments": len(bundle.get("part_fitment") or []),
        "pncs": len(bundle.get("pnc_categories") or []),
        "vehicles": len(bundle.get("vehicle_master") or []),
        "vehicles_with_engine": sum(
            1 for r in (bundle.get("vehicle_master") or []) if r.get("engine_code")
        ),
    }
    (bundle_dir / "browse_tree_meta.json").write_text(
        json.dumps(meta, indent=2) + "\n",
        encoding="utf-8",
    )


def transform_maker(paths: MakerPaths, *, storage_prefix: str) -> dict[str, Any]:
    bundle = build_hierarchy_bundle(paths, storage_prefix=storage_prefix)
    write_bundle(bundle, paths.bundle_dir)
    return bundle
