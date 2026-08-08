"""Filter catalog bundles to vehicles with complete fitment data."""

from __future__ import annotations

from collections import Counter
from typing import Any


def _vehicle_key(row: dict[str, Any]) -> tuple[Any, ...]:
    return (
        row.get("vin_prefix"),
        row.get("chassis_code"),
        row.get("engine_code"),
        row.get("production_year"),
        row.get("model_variant"),
    )


def complete_fitments(bundle: dict[str, Any]) -> list[dict[str, Any]]:
    """Fitment rows with chassis, diagram path, and diagram-kind appropriate completeness."""
    if bundle.get("catalog_diagrams"):
        from data_pipeline.megazip.quality import megazip_complete_fitments

        return megazip_complete_fitments(bundle)
    return [
        f
        for f in (bundle.get("part_fitment") or [])
        if f.get("chassis_code")
        and f.get("bbox_x") is not None
        and f.get("diagram_path")
    ]


_VEHICLE_MASTER_KEYS = (
    "vin_prefix",
    "chassis_code",
    "engine_code",
    "production_year",
    "model_variant",
)


def _sanitize_vehicle_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Project to ``vehicle_master`` schema: drop extras/nulls and invalid years."""
    out: list[dict[str, Any]] = []
    for row in rows:
        cleaned: dict[str, Any] = {}
        for key in _VEHICLE_MASTER_KEYS:
            if key not in row:
                continue
            value = row.get(key)
            if value is None:
                continue
            if key == "production_year" and isinstance(value, int) and value < 1980:
                continue
            cleaned[key] = value
        out.append(cleaned)
    return out


def filter_complete_bundle(
    bundle: dict[str, Any],
    *,
    completed_only: bool = True,
) -> tuple[dict[str, Any], dict[str, Any]]:
    """Return a storefront-ready bundle and summary metadata.

    ``completed_only=True`` drops identity-only ``vehicle_master`` rows (chassis with
    no complete fitments). Fitments always require chassis + bbox + diagram_path.
    When ``catalog_diagrams`` is present, each kept variant must have ≥1 passing
    ``exploded_diagram``; ``parts_list_raster`` diagrams are kept as table companions
    with ``publish_diagram: false``.
    """
    fitments = complete_fitments(bundle)
    has_hierarchy = bool(bundle.get("catalog_diagrams"))

    complete_variants: set[tuple[str, str]] | None = None
    if completed_only and has_hierarchy:
        from data_pipeline.megazip.quality import variants_with_passing_exploded

        complete_variants = variants_with_passing_exploded(bundle)
        diagram_by_path = {
            d.get("storage_path"): d
            for d in (bundle.get("catalog_diagrams") or [])
            if d.get("storage_path")
        }
        fitments = [
            f
            for f in fitments
            if (
                (diag := diagram_by_path.get(f.get("diagram_path") or ""))
                and (diag.get("model_slug"), diag.get("variant_slug")) in complete_variants
            )
        ]

    chassis_with_parts = {f["chassis_code"] for f in fitments}

    vehicles = bundle.get("vehicle_master") or []
    all_vehicle_chassis = sorted({v.get("chassis_code") for v in vehicles if v.get("chassis_code")})
    if completed_only:
        kept: list[dict[str, Any]] = []
        seen: set[tuple[Any, ...]] = set()
        for v in vehicles:
            if v.get("chassis_code") not in chassis_with_parts:
                continue
            key = _vehicle_key(v)
            if key in seen:
                continue
            seen.add(key)
            kept.append(v)
        vehicles = kept

    pnc_codes = {f.get("pnc_code") for f in fitments if f.get("pnc_code")}
    pncs = [p for p in (bundle.get("pnc_categories") or []) if p.get("pnc_code") in pnc_codes]

    diagram_paths = {f.get("diagram_path") for f in fitments if f.get("diagram_path")}
    kept_diagram_paths = diagram_paths
    if completed_only and has_hierarchy and complete_variants is not None:
        catalog_diagrams = [
            d
            for d in (bundle.get("catalog_diagrams") or [])
            if (d.get("model_slug"), d.get("variant_slug")) in complete_variants
        ]
        kept_diagram_paths = {d.get("storage_path") for d in catalog_diagrams if d.get("storage_path")}
        fitments = [f for f in fitments if f.get("diagram_path") in kept_diagram_paths]
        diagrams = [
            d
            for d in (bundle.get("diagram_assets") or [])
            if d.get("storage_path") in kept_diagram_paths
        ]
        pnc_codes = {f.get("pnc_code") for f in fitments if f.get("pnc_code")}
        pncs = [p for p in (bundle.get("pnc_categories") or []) if p.get("pnc_code") in pnc_codes]
    else:
        diagrams = [
            d
            for d in (bundle.get("diagram_assets") or [])
            if d.get("storage_path") in diagram_paths
        ]
        catalog_diagrams = [
            d
            for d in (bundle.get("catalog_diagrams") or [])
            if d.get("storage_path") in kept_diagram_paths
        ]

    for d in catalog_diagrams:
        kind = str(d.get("diagram_kind") or "")
        if kind == "parts_list_raster":
            d["publish_diagram"] = False
        elif kind == "ambiguous":
            d["publish_diagram"] = False
            d["needs_review"] = True
        elif "publish_diagram" not in d:
            d["publish_diagram"] = True

    catalog_diagram_parts = [
        p
        for p in (bundle.get("catalog_diagram_parts") or [])
        if p.get("diagram_path") in kept_diagram_paths
    ]

    filtered: dict[str, Any] = {
        "vehicle_master": _sanitize_vehicle_rows(vehicles),
        "pnc_categories": pncs,
        "part_fitment": fitments,
        "diagram_assets": diagrams,
    }
    if catalog_diagrams:
        filtered["catalog_diagrams"] = catalog_diagrams
    if catalog_diagram_parts:
        filtered["catalog_diagram_parts"] = catalog_diagram_parts

    hierarchy_keys = ("catalog_makers", "catalog_models", "catalog_variants", "catalog_sections")
    kept_variants = complete_variants
    for key in hierarchy_keys:
        rows = bundle.get(key)
        if not rows:
            continue
        if completed_only and has_hierarchy and kept_variants is not None:
            if key == "catalog_variants":
                rows = [
                    v
                    for v in rows
                    if (v.get("model_slug"), v.get("slug")) in kept_variants
                ]
            elif key == "catalog_sections":
                rows = [
                    s
                    for s in rows
                    if (s.get("model_slug"), s.get("variant_slug")) in kept_variants
                ]
            elif key == "catalog_models":
                kept_model_slugs = {v.get("model_slug") for v in (bundle.get("catalog_variants") or []) if (v.get("model_slug"), v.get("slug")) in kept_variants}
                rows = [m for m in rows if m.get("slug") in kept_model_slugs]
        filtered[key] = rows
    oem_names = bundle.get("_oem_display_names") or {}
    if oem_names:
        fit_oems = {f.get("oem_part_number") for f in fitments if f.get("oem_part_number")}
        filtered["_oem_display_names"] = {
            oem: name for oem, name in oem_names.items() if oem in fit_oems
        }

    meta = {
        "parts_complete_chassis": sorted(chassis_with_parts),
        "excluded_identity_only_chassis": sorted(set(all_vehicle_chassis) - chassis_with_parts),
        "chassis_fitment_counts": dict(Counter(f["chassis_code"] for f in fitments).most_common()),
        "vehicles_in": len(bundle.get("vehicle_master") or []),
        "vehicles_out": len(filtered["vehicle_master"]),
        "fitments_out": len(fitments),
        "pncs_out": len(pncs),
        "diagrams_out": len(diagrams),
        "completed_only": completed_only,
        "variants_with_passing_exploded": len(complete_variants) if complete_variants is not None else None,
        "criteria": (
            "vehicle_master: chassis with complete fitments only; "
            "variant: ≥1 passing exploded_diagram; "
            "fitment: chassis + diagram_path + (bbox for exploded, table OEM for raster/ambiguous); "
            "parts_list_raster/ambiguous: publish_diagram=false; ambiguous: needs_review=true"
            if completed_only and has_hierarchy
            else (
                "vehicle_master: chassis with complete fitments only; "
                "fitment: chassis + bbox_x + diagram_path"
                if completed_only
                else "vehicle_master: all rows; fitment: chassis + bbox_x + diagram_path"
            )
        ),
    }
    return filtered, meta
