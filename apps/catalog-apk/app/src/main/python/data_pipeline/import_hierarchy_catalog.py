"""Import EPC hierarchy bundle + legacy fitment tables into Supabase."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from data_pipeline.bundle_filter import filter_complete_bundle
from data_pipeline.import_catalog import (
    ImportResult,
    import_catalog,
    import_supabase,
    load_env_files,
    resolve_supabase_credentials,
    _chunks,
    _project,
)
from data_pipeline.validate import validate_bundle

_HIERARCHY_TABLES = (
    "catalog_makers",
    "catalog_models",
    "catalog_variants",
    "catalog_sections",
    "catalog_diagrams",
    "catalog_diagram_parts",
)

_MAKER_COLS = ("slug", "name", "sort_order", "source")
_MODEL_COLS = (
    "maker_slug",
    "slug",
    "display_name",
    "body_type",
    "sort_key",
    "year_start",
    "year_end",
    "source_url",
)
_VARIANT_COLS = (
    "maker_slug",
    "model_slug",
    "slug",
    "chassis_code",
    "frame",
    "grade",
    "sales_region",
    "year_start",
    "year_end",
    "year_label",
    "engine_code",
    "megazip_data_id",
    "source_url",
)
_SECTION_COLS = (
    "maker_slug",
    "model_slug",
    "variant_slug",
    "slug",
    "name",
    "thumbnail_url",
    "sort_order",
    "assembly_group_id",
    "source_url",
)
_DIAGRAM_COLS = (
    "maker_slug",
    "model_slug",
    "variant_slug",
    "section_slug",
    "slug",
    "title",
    "image_url",
    "image_width",
    "image_height",
    "diagram_kind",
    "hotspot_count",
    "storage_path",
    "source_url",
)
_DIAGRAM_PART_COLS = (
    "maker_slug",
    "model_slug",
    "variant_slug",
    "section_slug",
    "diagram_slug",
    "diagram_path",
    "itemslist_id",
    "callout_ref",
    "oem_part_number",
    "description",
    "quantity",
    "megazip_item_id",
    "bbox_x",
    "bbox_y",
    "bbox_width",
    "bbox_height",
)


def _key_maker(row: dict[str, Any]) -> tuple[Any, ...]:
    return (row.get("slug"),)


def _key_model(row: dict[str, Any]) -> tuple[Any, ...]:
    return (row.get("maker_slug"), row.get("slug"))


def _key_variant(row: dict[str, Any]) -> tuple[Any, ...]:
    return (row.get("maker_slug"), row.get("model_slug"), row.get("slug"))


def _key_section(row: dict[str, Any]) -> tuple[Any, ...]:
    return (
        row.get("maker_slug"),
        row.get("model_slug"),
        row.get("variant_slug"),
        row.get("slug"),
    )


def _key_diagram(row: dict[str, Any]) -> tuple[Any, ...]:
    return (
        row.get("maker_slug"),
        row.get("model_slug"),
        row.get("variant_slug"),
        row.get("section_slug"),
        row.get("slug"),
    )


def _key_diagram_part(row: dict[str, Any]) -> tuple[Any, ...]:
    return (
        row.get("maker_slug"),
        row.get("model_slug"),
        row.get("variant_slug"),
        row.get("section_slug"),
        row.get("itemslist_id"),
    )


def load_hierarchy_bundle(bundle_dir: Path) -> dict[str, Any]:
    bundle: dict[str, Any] = {}
    for name in (
        *_HIERARCHY_TABLES,
        "vehicle_master",
        "pnc_categories",
        "part_fitment",
        "diagram_assets",
    ):
        path = bundle_dir / f"{name}.json"
        if path.is_file():
            bundle[name] = json.loads(path.read_text(encoding="utf-8"))
    oem_path = bundle_dir / "oem_display_names.json"
    if oem_path.is_file():
        bundle["_oem_display_names"] = json.loads(oem_path.read_text(encoding="utf-8"))
    return bundle


def _batch_upsert_slug_table(
    client: Any,
    table: str,
    rows: list[dict[str, Any]],
    cols: tuple[str, ...],
    key_fn,
    *,
    conflict_cols: str,
) -> int:
    if not rows:
        return 0
    projected = [_project(r, cols) for r in rows]
    count = 0
    for chunk in _chunks(projected):
        client.table(table).upsert(chunk, on_conflict=conflict_cols).execute()
        count += len(chunk)
    return count


def import_hierarchy_supabase(
    bundle: dict[str, Any],
    *,
    url: str,
    key: str,
    ensure_stock_items: bool = True,
    prune_stale: bool = False,
) -> ImportResult:
    try:
        from supabase import create_client
    except ImportError as exc:
        raise RuntimeError("pip install -e '.[supabase]'") from exc

    client = create_client(url, key)
    validate_bundle(
        {
            k: v
            for k, v in bundle.items()
            if k in ("vehicle_master", "pnc_categories", "part_fitment", "diagram_assets")
        }
    )

    notes: list[str] = []
    _batch_upsert_slug_table(
        client,
        "catalog_makers",
        bundle.get("catalog_makers") or [],
        _MAKER_COLS,
        _key_maker,
        conflict_cols="slug",
    )
    _batch_upsert_slug_table(
        client,
        "catalog_models",
        bundle.get("catalog_models") or [],
        _MODEL_COLS,
        _key_model,
        conflict_cols="maker_slug,slug",
    )
    _batch_upsert_slug_table(
        client,
        "catalog_variants",
        bundle.get("catalog_variants") or [],
        _VARIANT_COLS,
        _key_variant,
        conflict_cols="maker_slug,model_slug,slug",
    )
    _batch_upsert_slug_table(
        client,
        "catalog_sections",
        bundle.get("catalog_sections") or [],
        _SECTION_COLS,
        _key_section,
        conflict_cols="maker_slug,model_slug,variant_slug,slug",
    )
    _batch_upsert_slug_table(
        client,
        "catalog_diagrams",
        bundle.get("catalog_diagrams") or [],
        _DIAGRAM_COLS,
        _key_diagram,
        conflict_cols="maker_slug,model_slug,variant_slug,section_slug,slug",
    )
    _batch_upsert_slug_table(
        client,
        "catalog_diagram_parts",
        bundle.get("catalog_diagram_parts") or [],
        _DIAGRAM_PART_COLS,
        _key_diagram_part,
        conflict_cols="maker_slug,model_slug,variant_slug,section_slug,itemslist_id",
    )
    notes.append("hierarchy tables upserted")

    legacy = {
        "vehicle_master": bundle.get("vehicle_master") or [],
        "pnc_categories": bundle.get("pnc_categories") or [],
        "part_fitment": bundle.get("part_fitment") or [],
        "diagram_assets": bundle.get("diagram_assets") or [],
        "_oem_display_names": bundle.get("_oem_display_names") or {},
    }
    result = import_supabase(
        legacy,
        url=url,
        key=key,
        ensure_stock_items=ensure_stock_items,
        prune_stale=prune_stale,
    )
    result.notes.extend(notes)
    return result


def import_hierarchy_bundle_dir(
    bundle_dir: Path,
    *,
    live: bool = False,
    complete_only: bool = True,
    prune_stale: bool | None = None,
    ensure_stock_items: bool = True,
) -> ImportResult:
    bundle = load_hierarchy_bundle(bundle_dir)
    if complete_only:
        filtered, meta = filter_complete_bundle(bundle, completed_only=True)
        bundle = {**bundle, **filtered}
        variant_count = meta.get("variants_with_passing_exploded")
        if variant_count is not None:
            notes_hint = f"variant-level complete-only: {variant_count} variants with passing exploded"
        else:
            notes_hint = "complete-only filter applied"
    else:
        notes_hint = "full bundle (no complete-only filter)"
    if live:
        root = Path(__file__).resolve().parent.parent
        load_env_files(root.parent / ".env", root / ".env", override=True)
        url, key = resolve_supabase_credentials()
        if not url or not key:
            raise RuntimeError("SUPABASE_URL and service role key required for live import")
        ps = prune_stale if prune_stale is not None else complete_only
        result = import_hierarchy_supabase(
            bundle,
            url=url,
            key=key,
            ensure_stock_items=ensure_stock_items,
            prune_stale=ps,
        )
        result.notes.insert(0, notes_hint)
        return result
    legacy = {
        k: bundle.get(k) or []
        for k in ("vehicle_master", "pnc_categories", "part_fitment", "diagram_assets")
    }
    result = import_catalog(legacy, ensure_stock_items=ensure_stock_items)
    result.notes.insert(0, notes_hint)
    return result
