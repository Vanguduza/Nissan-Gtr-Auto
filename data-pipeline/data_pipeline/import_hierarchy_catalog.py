"""Import EPC hierarchy bundle + legacy fitment tables into Supabase."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from data_pipeline.bundle_filter import _sanitize_vehicle_rows, filter_complete_bundle
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

_PNC_SCHEMA_KEYS = (
    "pnc_code",
    "category_name",
    "subcategory_name",
    "assembly_group_id",
    "catalog_section_path",
    "pcdb_part_type_id",
)
_FITMENT_SCHEMA_KEYS = (
    "oem_part_number",
    "pnc_code",
    "chassis_code",
    "engine_code",
    "superseded_by",
    "bbox_x",
    "bbox_y",
    "bbox_width",
    "bbox_height",
    "diagram_path",
)


def _drop_nulls(row: dict[str, Any], keys: tuple[str, ...]) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for key in keys:
        if key not in row:
            continue
        value = row.get(key)
        if value is None:
            continue
        out[key] = value
    return out


def sanitize_legacy_bundle_for_import(bundle: dict[str, Any]) -> dict[str, Any]:
    """Project Megazip/legacy tables to JSON-schema shapes used by validate_bundle."""
    out = dict(bundle)
    out["vehicle_master"] = _sanitize_vehicle_rows(bundle.get("vehicle_master") or [])
    out["pnc_categories"] = [
        _drop_nulls(row, _PNC_SCHEMA_KEYS) for row in (bundle.get("pnc_categories") or [])
    ]
    out["part_fitment"] = [
        _drop_nulls(row, _FITMENT_SCHEMA_KEYS) for row in (bundle.get("part_fitment") or [])
    ]

    fit_by_path: dict[str, dict[str, Any]] = {}
    for fit in out["part_fitment"]:
        path = fit.get("diagram_path") or ""
        if path and path not in fit_by_path:
            fit_by_path[path] = fit

    assets: list[dict[str, Any]] = []
    for asset in bundle.get("diagram_assets") or []:
        path = asset.get("storage_path") or ""
        fit = fit_by_path.get(path) or {}
        pnc = fit.get("pnc_code")
        chassis = fit.get("chassis_code")
        if not path or not pnc or not chassis:
            continue
        content_type = asset.get("content_type") or asset.get("mime_type") or "image/png"
        row: dict[str, Any] = {
            "storage_path": path,
            "pnc_code": pnc,
            "chassis_code": chassis,
            "content_type": content_type,
            "provenance": asset.get("provenance") or "scraped-reference",
        }
        if asset.get("source_url"):
            row["source_url"] = asset["source_url"]
        if fit.get("engine_code"):
            row["engine_code"] = fit["engine_code"]
        assets.append(row)
    out["diagram_assets"] = assets
    return out


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
    "external_data_id",
    "source_url",
)
_VARIANT_COLS_LEGACY = tuple(
    "megazip_data_id" if c == "external_data_id" else c for c in _VARIANT_COLS
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
    "external_item_id",
    "bbox_x",
    "bbox_y",
    "bbox_width",
    "bbox_height",
)
_DIAGRAM_PART_COLS_LEGACY = tuple(
    "megazip_item_id" if c == "external_item_id" else c for c in _DIAGRAM_PART_COLS
)


def _table_has_column(client: Any, table: str, column: str) -> bool:
    try:
        client.table(table).select(column).limit(1).execute()
        return True
    except Exception:  # noqa: BLE001 — PostgREST 400 when column missing
        return False


def _normalize_hierarchy_rows_for_schema(
    client: Any, bundle: dict[str, Any]
) -> tuple[tuple[str, ...], tuple[str, ...], dict[str, Any]]:
    """Map vendor-neutral fields onto live column names (pre/post rename migration)."""
    variant_cols = (
        _VARIANT_COLS
        if _table_has_column(client, "catalog_variants", "external_data_id")
        else _VARIANT_COLS_LEGACY
    )
    part_cols = (
        _DIAGRAM_PART_COLS
        if _table_has_column(client, "catalog_diagram_parts", "external_item_id")
        else _DIAGRAM_PART_COLS_LEGACY
    )
    out = dict(bundle)
    if variant_cols is _VARIANT_COLS_LEGACY:
        rows = []
        for row in bundle.get("catalog_variants") or []:
            mapped = {k: v for k, v in row.items() if k != "external_data_id"}
            mapped["megazip_data_id"] = (
                row.get("megazip_data_id") or row.get("external_data_id") or ""
            )
            rows.append(mapped)
        out["catalog_variants"] = rows
    if part_cols is _DIAGRAM_PART_COLS_LEGACY:
        rows = []
        for row in bundle.get("catalog_diagram_parts") or []:
            mapped = {k: v for k, v in row.items() if k != "external_item_id"}
            mapped["megazip_item_id"] = row.get("megazip_item_id") or row.get(
                "external_item_id"
            )
            rows.append(mapped)
        out["catalog_diagram_parts"] = rows
    return variant_cols, part_cols, out


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
    import logging

    log = logging.getLogger(__name__)
    projected = [_project(r, cols) for r in rows]
    count = 0
    total = len(projected)
    for chunk in _chunks(projected):
        client.table(table).upsert(chunk, on_conflict=conflict_cols).execute()
        count += len(chunk)
        if total >= 5000 and (count == len(chunk) or count % 20000 < len(chunk) or count >= total):
            log.info("%s upsert progress %s/%s", table, count, total)
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
    sanitized = sanitize_legacy_bundle_for_import(bundle)
    validate_bundle(
        {
            k: sanitized.get(k) or []
            for k in ("vehicle_master", "pnc_categories", "part_fitment", "diagram_assets")
        }
    )
    # Keep hierarchy rows from the caller; replace only legacy tables that were sanitized.
    bundle = {
        **bundle,
        "vehicle_master": sanitized["vehicle_master"],
        "pnc_categories": sanitized["pnc_categories"],
        "part_fitment": sanitized["part_fitment"],
        "diagram_assets": sanitized["diagram_assets"],
    }
    variant_cols, part_cols, bundle = _normalize_hierarchy_rows_for_schema(client, bundle)

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
        variant_cols,
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
        part_cols,
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
        # Repo-root .env last so hosted SoR wins over data-pipeline/.env local Docker overrides.
        load_env_files(root / ".env", root.parent / ".env", override=True)
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
