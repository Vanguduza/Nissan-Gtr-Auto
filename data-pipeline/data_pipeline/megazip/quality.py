"""Publish-quality gates for Megazip hierarchy bundles."""

from __future__ import annotations

from collections import Counter
from typing import Any

from data_pipeline.bundle_filter import complete_fitments, filter_complete_bundle
from data_pipeline.import_hierarchy_catalog import import_schema_issues

MIN_EXPLODED_HOTSPOTS = 5
MIN_EXPLODED_PARTS_OEM = 3
MIN_RASTER_TABLE_ROWS = 1


def _diagram_kind_by_path(bundle: dict[str, Any]) -> dict[str, str]:
    out: dict[str, str] = {}
    for d in bundle.get("catalog_diagrams") or []:
        path = d.get("storage_path") or ""
        if path:
            out[path] = str(d.get("diagram_kind") or "ambiguous")
    return out


def _companion_rows_by_path(bundle: dict[str, Any]) -> dict[str, list[dict[str, Any]]]:
    grouped: dict[str, list[dict[str, Any]]] = {}
    for row in bundle.get("catalog_diagram_parts") or []:
        path = row.get("diagram_path") or ""
        if path:
            grouped.setdefault(path, []).append(row)
    return grouped


def megazip_complete_fitments(bundle: dict[str, Any]) -> list[dict[str, Any]]:
    """Fitments passing Megazip diagram-kind gates (exploded vs raster table)."""
    kinds = _diagram_kind_by_path(bundle)
    companions = _companion_rows_by_path(bundle)
    complete: list[dict[str, Any]] = []
    for f in bundle.get("part_fitment") or []:
        if not f.get("chassis_code") or not f.get("diagram_path"):
            continue
        path = f["diagram_path"]
        kind = kinds.get(path, "ambiguous")
        oem = f.get("oem_part_number") or ""
        if kind == "parts_list_raster":
            table_rows = [
                r for r in companions.get(path, []) if r.get("oem_part_number")
            ]
            if table_rows and oem:
                complete.append(f)
            continue
        if kind == "ambiguous":
            table_rows = [
                r for r in companions.get(path, []) if r.get("oem_part_number")
            ]
            if table_rows and oem:
                complete.append(f)
            elif f.get("bbox_x") is not None and oem:
                complete.append(f)
            continue
        if f.get("bbox_x") is not None and oem:
            complete.append(f)
    return complete


def _passing_diagram_paths(bundle: dict[str, Any]) -> set[str]:
    """Diagram storage paths that pass per-kind quality gates."""
    kinds = _diagram_kind_by_path(bundle)
    companions = _companion_rows_by_path(bundle)
    fitments = bundle.get("part_fitment") or []
    by_path: dict[str, list[dict[str, Any]]] = {}
    for f in fitments:
        path = f.get("diagram_path") or ""
        if path:
            by_path.setdefault(path, []).append(f)

    diagram_meta = {
        d.get("storage_path"): d for d in (bundle.get("catalog_diagrams") or []) if d.get("storage_path")
    }
    passing: set[str] = set()
    for path, kind in kinds.items():
        parts = by_path.get(path, [])
        oem_parts = [p for p in parts if p.get("oem_part_number")]
        bbox_parts = [p for p in oem_parts if p.get("bbox_x") is not None]
        table_rows = [r for r in companions.get(path, []) if r.get("oem_part_number")]
        meta = diagram_meta.get(path, {})
        hotspots = int(meta.get("hotspot_count") or 0) or len(bbox_parts)

        if kind == "parts_list_raster":
            if len(table_rows) >= MIN_RASTER_TABLE_ROWS:
                passing.add(path)
        elif kind == "exploded_diagram":
            if (
                path
                and hotspots >= MIN_EXPLODED_HOTSPOTS
                and len(oem_parts) >= MIN_EXPLODED_PARTS_OEM
                and len(bbox_parts) >= MIN_EXPLODED_PARTS_OEM
            ):
                passing.add(path)
        else:
            # ambiguous: table required, hotspots optional
            if len(table_rows) >= MIN_RASTER_TABLE_ROWS:
                passing.add(path)
            elif (
                len(bbox_parts) >= MIN_EXPLODED_PARTS_OEM
                and oem_parts
            ):
                passing.add(path)
    return passing


def variants_with_passing_exploded(bundle: dict[str, Any]) -> set[tuple[str, str]]:
    """Variant keys (model_slug, variant_slug) with ≥1 passing exploded_diagram."""
    passing = _passing_diagram_paths(bundle)
    out: set[tuple[str, str]] = set()
    for d in bundle.get("catalog_diagrams") or []:
        path = d.get("storage_path") or ""
        if path not in passing:
            continue
        if str(d.get("diagram_kind") or "") != "exploded_diagram":
            continue
        model_slug = d.get("model_slug") or ""
        variant_slug = d.get("variant_slug") or ""
        if model_slug and variant_slug:
            out.add((model_slug, variant_slug))
    return out


def publishable_variant_keys(bundle: dict[str, Any]) -> set[tuple[str, str]]:
    """Variant keys eligible for --complete-only import (≥1 passing exploded_diagram)."""
    return variants_with_passing_exploded(bundle)


def variant_quality_breakdown(bundle: dict[str, Any]) -> list[dict[str, Any]]:
    """Per-variant diagram counts, companion rows, and publishability."""
    passing = _passing_diagram_paths(bundle)
    exploded_variants = variants_with_passing_exploded(bundle)
    companions = _companion_rows_by_path(bundle)

    by_variant: dict[tuple[str, str], dict[str, Any]] = {}
    for v in bundle.get("catalog_variants") or []:
        key = (v.get("model_slug") or "", v.get("slug") or "")
        by_variant[key] = {
            "model_slug": key[0],
            "variant_slug": key[1],
            "chassis_code": v.get("chassis_code") or "",
            "frame": v.get("frame") or "",
            "exploded_count": 0,
            "exploded_passing_count": 0,
            "raster_count": 0,
            "raster_passing_count": 0,
            "ambiguous_count": 0,
            "ambiguous_passing_count": 0,
            "companion_parts_rows": 0,
            "complete": False,
            "publishable": False,
        }

    for d in bundle.get("catalog_diagrams") or []:
        key = (d.get("model_slug") or "", d.get("variant_slug") or "")
        if key not in by_variant:
            continue
        rec = by_variant[key]
        path = d.get("storage_path") or ""
        kind = str(d.get("diagram_kind") or "ambiguous")
        is_passing = path in passing
        if kind == "exploded_diagram":
            rec["exploded_count"] += 1
            if is_passing:
                rec["exploded_passing_count"] += 1
        elif kind == "parts_list_raster":
            rec["raster_count"] += 1
            if is_passing:
                rec["raster_passing_count"] += 1
        else:
            rec["ambiguous_count"] += 1
            if is_passing:
                rec["ambiguous_passing_count"] += 1
        rec["companion_parts_rows"] += len(companions.get(path, []))

    for key, rec in by_variant.items():
        has_exploded = key in exploded_variants
        rec["complete"] = has_exploded and rec["exploded_passing_count"] >= 1
        rec["publishable"] = rec["complete"]

    return sorted(by_variant.values(), key=lambda r: (r["chassis_code"], r["variant_slug"]))


def _diagram_gate_stats(bundle: dict[str, Any]) -> dict[str, Any]:
    kinds = _diagram_kind_by_path(bundle)
    companions = _companion_rows_by_path(bundle)
    fitments = bundle.get("part_fitment") or []
    by_path: dict[str, list[dict[str, Any]]] = {}
    for f in fitments:
        path = f.get("diagram_path") or ""
        if path:
            by_path.setdefault(path, []).append(f)

    diagram_meta = {
        d.get("storage_path"): d for d in (bundle.get("catalog_diagrams") or []) if d.get("storage_path")
    }

    exploded_ok = 0
    raster_ok = 0
    ambiguous_ok = 0
    failed: list[str] = []
    kind_counts = Counter(kinds.values())
    passing = _passing_diagram_paths(bundle)

    for path, kind in kinds.items():
        if path in passing:
            if kind == "parts_list_raster":
                raster_ok += 1
            elif kind == "exploded_diagram":
                exploded_ok += 1
            else:
                ambiguous_ok += 1
        else:
            failed.append(path)

    return {
        "diagram_kind_counts": dict(kind_counts),
        "exploded_diagrams_passing": exploded_ok,
        "parts_list_raster_passing": raster_ok,
        "ambiguous_diagrams_passing": ambiguous_ok,
        "diagrams_failing_gate": failed,
        "diagrams_failing_gate_count": len(failed),
    }


def bundle_quality_report(bundle: dict[str, Any]) -> dict[str, Any]:
    fitments = bundle.get("part_fitment") or []
    complete = megazip_complete_fitments(bundle)
    legacy_complete = complete_fitments(bundle)
    pncs = bundle.get("pnc_categories") or []
    uncat = sum(1 for p in pncs if str(p.get("category_name") or "").lower() == "uncategorized")
    variants = bundle.get("catalog_variants") or []
    chassis_with_parts = {f.get("chassis_code") for f in complete if f.get("chassis_code")}
    variants_with_parts = [
        v
        for v in variants
        if v.get("chassis_code") in chassis_with_parts
        or any(f.get("catalog_variant_slug") == v.get("slug") for f in complete)
    ]
    _, filter_meta = filter_complete_bundle(bundle, completed_only=True)
    diagram_stats = _diagram_gate_stats(bundle)
    variant_rows = variant_quality_breakdown(bundle)
    variants_publishable = sum(1 for v in variant_rows if v.get("publishable"))
    companion_count = len(bundle.get("catalog_diagram_parts") or [])

    vehicles = bundle.get("vehicle_master") or []
    vehicles_with_engine = [r for r in vehicles if r.get("engine_code")]
    chassis_all = {r.get("chassis_code") for r in vehicles if r.get("chassis_code")}
    chassis_with_engine = {
        r.get("chassis_code") for r in vehicles_with_engine if r.get("chassis_code")
    }
    variant_chassis = {v.get("chassis_code") for v in variants if v.get("chassis_code")}
    chassis_missing_engine = sorted(
        ch for ch in variant_chassis if ch and ch not in chassis_with_engine
    )
    engine_coverage = {
        "vehicle_master_rows": len(vehicles),
        "vehicle_master_with_engine": len(vehicles_with_engine),
        "distinct_chassis": len(chassis_all),
        "chassis_with_engine": len(chassis_with_engine),
        "variant_chassis_missing_engine": chassis_missing_engine,
        "variant_chassis_missing_engine_count": len(chassis_missing_engine),
    }

    kinds = _diagram_kind_by_path(bundle)
    non_ambiguous_failing = [
        p for p in diagram_stats["diagrams_failing_gate"] if kinds.get(p) != "ambiguous"
    ]
    maker_publishable = (
        len(complete) > 0
        and uncat == 0
        and len(non_ambiguous_failing) == 0
        and diagram_stats["diagrams_failing_gate_count"] == 0
        and variants_publishable > 0
        and all(v.get("publishable") for v in variant_rows if v.get("exploded_count") or v.get("raster_count"))
    )

    # Variant-level import gate: publishable variants with clean PNCs
    publishable = variants_publishable > 0 and uncat == 0

    return {
        "models": len(bundle.get("catalog_models") or []),
        "variants": len(variants),
        "variants_with_complete_fitments": len(variants_with_parts),
        "variants_publishable": variants_publishable,
        "sections": len(bundle.get("catalog_sections") or []),
        "diagrams": len(bundle.get("catalog_diagrams") or []),
        "companion_parts_rows": companion_count,
        "fitments_total": len(fitments),
        "fitments_complete": len(complete),
        "fitments_complete_legacy_bbox": len(legacy_complete),
        "uncategorized_pncs": uncat,
        "pcdb_mapped": sum(1 for p in pncs if p.get("pcdb_part_type_id")),
        **filter_meta,
        **diagram_stats,
        **engine_coverage,
        "maker_publishable": maker_publishable,
        "publishable": publishable,
        "variant_quality": variant_rows,
    }


def assert_publishable(meta: dict[str, Any], *, strict: bool = True) -> None:
    """Strict gate uses variant-level publishable (import-ready), not maker_publishable."""
    if not strict:
        return
    if not meta.get("publishable"):
        raise RuntimeError(
            "Bundle failed variant import gate: "
            f"variants_publishable={meta.get('variants_publishable')} "
            f"uncategorized_pncs={meta.get('uncategorized_pncs')}"
        )
