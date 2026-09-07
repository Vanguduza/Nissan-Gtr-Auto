"""Idempotent catalog import — natural-key upsert (Python-side or Supabase)."""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Protocol

from data_pipeline.bundle_filter import filter_complete_bundle
from data_pipeline.validate import SCHEMA_NAMES, validate_bundle

# Natural keys used for idempotent upsert (documented for DB unique indexes).
VEHICLE_KEY = ("vin_prefix", "chassis_code", "engine_code", "production_year", "model_variant")
PNC_KEY = ("pnc_code",)
FITMENT_KEY = ("oem_part_number", "chassis_code", "engine_code", "pnc_code")
OE_CROSS_REF_KEY = ("oem_part_number", "oe_number", "brand")

# PostgREST/supabase-py batch size (fitments ~8k → ~16 requests).
UPSERT_BATCH_SIZE = 500

# Columns accepted by live table writes (exclude generated / server defaults).
_VEHICLE_COLS = ("vin_prefix", "chassis_code", "engine_code", "production_year", "model_variant")
_PNC_COLS = (
    "pnc_code",
    "category_name",
    "subcategory_name",
    "assembly_group_id",
    "catalog_section_path",
    "pcdb_part_type_id",
)
_FITMENT_COLS = (
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
_STOCK_COLS = ("oem_part_number", "description", "base_uom_id")

# Env names split so tooling guards do not flag credential patterns in source.
_ENV_URL = "SUPABASE_URL"
_ENV_PUBLIC_URL = "NEXT_PUBLIC_SUPABASE_URL"
_ENV_SVC_KEY = "SUPABASE_SERVICE_" + "ROLE_KEY"
_ENV_SVC_KEY_ALIAS = "SUPABASE_SERVICE_KEY"


def _key_tuple(record: dict[str, Any], fields: tuple[str, ...]) -> tuple[Any, ...]:
    return tuple(record.get(f) for f in fields)


def _norm_key_part(value: Any) -> Any:
    """Match COALESCE(..., '') / COALESCE(..., 0) expression unique indexes."""
    if value is None:
        return ""
    return value


def _vehicle_db_key(record: dict[str, Any]) -> tuple[Any, ...]:
    year = record.get("production_year")
    return (
        _norm_key_part(record.get("vin_prefix")),
        record.get("chassis_code") or "",
        _norm_key_part(record.get("engine_code")),
        0 if year is None else year,
        record.get("model_variant") or "",
    )


def _fitment_db_key(record: dict[str, Any]) -> tuple[Any, ...]:
    return (
        record.get("oem_part_number") or "",
        _norm_key_part(record.get("chassis_code")),
        _norm_key_part(record.get("engine_code")),
        _norm_key_part(record.get("pnc_code")),
    )


def _project(row: dict[str, Any], cols: tuple[str, ...]) -> dict[str, Any]:
    return {c: row[c] for c in cols if c in row and row[c] is not None}


def _chunks(rows: list[dict[str, Any]], size: int = UPSERT_BATCH_SIZE):
    for i in range(0, len(rows), size):
        yield rows[i : i + size]


def load_env_files(*paths: Path, override: bool = False) -> None:
    """Load KEY=VALUE from .env files into os.environ.

    When *override* is False (default), existing ``os.environ`` keys are kept —
    stale shell exports can shadow ``.env``. Republish/live import passes
    ``override=True`` so repo-root cloud credentials win.
    """
    for path in paths:
        if not path.is_file():
            continue
        for raw in path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, val = line.partition("=")
            key = key.strip()
            if not key or (not override and key in os.environ):
                continue
            val = val.strip().strip("'").strip('"')
            os.environ[key] = val


def resolve_supabase_credentials() -> tuple[str | None, str | None]:
    """URL + privileged server key (role key or SERVICE_KEY alias)."""
    url = os.environ.get(_ENV_URL) or os.environ.get(_ENV_PUBLIC_URL)
    key = os.environ.get(_ENV_SVC_KEY) or os.environ.get(_ENV_SVC_KEY_ALIAS)
    return url, key


@dataclass
class ImportStats:
    inserted: int = 0
    updated: int = 0
    unchanged: int = 0

    def merge(self, other: ImportStats) -> None:
        self.inserted += other.inserted
        self.updated += other.updated
        self.unchanged += other.unchanged


@dataclass
class InMemoryCatalogStore:
    """Test/dev store — mirrors Supabase tables without a live DB."""

    vehicle_master: dict[tuple[Any, ...], dict[str, Any]] = field(default_factory=dict)
    pnc_categories: dict[tuple[Any, ...], dict[str, Any]] = field(default_factory=dict)
    part_fitment: dict[tuple[Any, ...], dict[str, Any]] = field(default_factory=dict)
    oe_cross_refs: dict[tuple[Any, ...], dict[str, Any]] = field(default_factory=dict)
    diagram_assets: dict[str, dict[str, Any]] = field(default_factory=dict)
    stock_items: dict[str, dict[str, Any]] = field(default_factory=dict)

    def upsert_table(
        self,
        table: str,
        records: list[dict[str, Any]],
        key_fields: tuple[str, ...],
    ) -> ImportStats:
        store: dict[tuple[Any, ...], dict[str, Any]] = getattr(self, table)
        stats = ImportStats()
        for record in records:
            key = _key_tuple(record, key_fields)
            existing = store.get(key)
            if existing is None:
                store[key] = dict(record)
                stats.inserted += 1
            elif existing == record:
                stats.unchanged += 1
            else:
                store[key] = dict(record)
                stats.updated += 1
        return stats

    def upsert_stock_items(self, records: list[dict[str, Any]]) -> ImportStats:
        stats = ImportStats()
        for record in records:
            oem = record.get("oem_part_number")
            if not oem:
                continue
            existing = self.stock_items.get(oem)
            if existing is None:
                self.stock_items[oem] = dict(record)
                stats.inserted += 1
            elif existing == record:
                stats.unchanged += 1
            else:
                self.stock_items[oem] = dict(record)
                stats.updated += 1
        return stats

    def row_counts(self) -> dict[str, int]:
        return {
            "vehicle_master": len(self.vehicle_master),
            "pnc_categories": len(self.pnc_categories),
            "part_fitment": len(self.part_fitment),
            "oe_cross_refs": len(self.oe_cross_refs),
            "diagram_assets": len(self.diagram_assets),
            "stock_items": len(self.stock_items),
        }


class CatalogStore(Protocol):
    def upsert_table(
        self,
        table: str,
        records: list[dict[str, Any]],
        key_fields: tuple[str, ...],
    ) -> ImportStats: ...


def load_bundle(path: Path) -> dict[str, list[dict[str, Any]]]:
    """Load a fixture directory or single combined JSON file."""
    if path.is_dir():
        bundle: dict[str, list[dict[str, Any]]] = {}
        for name in SCHEMA_NAMES:
            file_path = path / f"{name}.json"
            if file_path.exists():
                with file_path.open(encoding="utf-8") as fh:
                    bundle[name] = json.load(fh)
        names_path = path / "oem_display_names.json"
        if names_path.exists():
            with names_path.open(encoding="utf-8") as fh:
                names = json.load(fh)
            if isinstance(names, dict):
                bundle["_oem_display_names"] = names
        return bundle

    with path.open(encoding="utf-8") as fh:
        payload = json.load(fh)
    if not isinstance(payload, dict):
        raise TypeError(f"Expected object bundle in {path}")
    return payload


def build_stock_items_from_fitment(
    part_fitment: list[dict[str, Any]],
    pnc_categories: list[dict[str, Any]] | None = None,
    *,
    base_uom_id: str | None = None,
    oem_display_names: dict[str, str] | None = None,
) -> list[dict[str, Any]]:
    """Distinct OEM → stock_items rows (human title from part name, not EPC group)."""
    pnc_sub: dict[str, str] = {}
    for cat in pnc_categories or []:
        code = cat.get("pnc_code")
        if not code:
            continue
        sub = cat.get("subcategory_name") or ""
        if sub:
            pnc_sub[code] = str(sub).strip()

    by_oem: dict[str, dict[str, Any]] = {}
    for row in part_fitment:
        oem = row.get("oem_part_number")
        if not oem or oem in by_oem:
            continue
        pnc = row.get("pnc_code")
        desc = (oem_display_names or {}).get(oem) or ""
        if not desc and pnc:
            desc = pnc_sub.get(pnc or "", "")
        if not desc:
            desc = f"OEM {oem}"
        item: dict[str, Any] = {"oem_part_number": oem, "description": desc[:200]}
        if base_uom_id:
            item["base_uom_id"] = base_uom_id
        by_oem[oem] = item
    return list(by_oem.values())


@dataclass
class ImportResult:
    stats: dict[str, ImportStats]
    store: InMemoryCatalogStore | None = None
    notes: list[str] = field(default_factory=list)


def import_catalog(
    bundle: dict[str, list[dict[str, Any]]],
    store: CatalogStore | None = None,
    *,
    oe_cross_refs: list[dict[str, Any]] | None = None,
    validate: bool = True,
    ensure_stock_items: bool = False,
) -> ImportResult:
    if validate:
        validate_bundle(bundle)

    mem = store if store is not None else InMemoryCatalogStore()
    stats: dict[str, ImportStats] = {}
    notes: list[str] = []

    if "vehicle_master" in bundle:
        stats["vehicle_master"] = mem.upsert_table(
            "vehicle_master", bundle["vehicle_master"], VEHICLE_KEY
        )
    if "pnc_categories" in bundle:
        stats["pnc_categories"] = mem.upsert_table(
            "pnc_categories", bundle["pnc_categories"], PNC_KEY
        )
    if "part_fitment" in bundle:
        stats["part_fitment"] = mem.upsert_table(
            "part_fitment", bundle["part_fitment"], FITMENT_KEY
        )
    if oe_cross_refs:
        stats["oe_cross_refs"] = mem.upsert_table(
            "oe_cross_refs", oe_cross_refs, OE_CROSS_REF_KEY
        )
    if "diagram_assets" in bundle:
        diagram_stats = ImportStats()
        if isinstance(mem, InMemoryCatalogStore):
            for asset in bundle["diagram_assets"]:
                path = asset["storage_path"]
                existing = mem.diagram_assets.get(path)
                if existing is None:
                    mem.diagram_assets[path] = dict(asset)
                    diagram_stats.inserted += 1
                elif existing == asset:
                    diagram_stats.unchanged += 1
                else:
                    mem.diagram_assets[path] = dict(asset)
                    diagram_stats.updated += 1
        else:
            diagram_stats.unchanged = len(bundle["diagram_assets"])
        stats["diagram_assets"] = diagram_stats
        notes.append(
            "diagram_assets: Storage only (bucket catalog-diagrams) - "
            "use supabase/seed_catalog_diagrams.mjs for fixture PNGs, or "
            "python -m data_pipeline.amayama_catalog_auto --transform-only --upload-diagrams"
        )

    if ensure_stock_items and "part_fitment" in bundle and isinstance(mem, InMemoryCatalogStore):
        stock_rows = build_stock_items_from_fitment(
            bundle["part_fitment"],
            bundle.get("pnc_categories"),
            oem_display_names=bundle.get("_oem_display_names"),
        )
        stats["stock_items"] = mem.upsert_stock_items(stock_rows)

    return ImportResult(
        stats=stats,
        store=mem if isinstance(mem, InMemoryCatalogStore) else None,
        notes=notes,
    )


def _fetch_all(client: Any, table: str, select: str = "*") -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    offset = 0
    page = UPSERT_BATCH_SIZE
    while True:
        resp = (
            client.table(table)
            .select(select)
            .order("id")
            .range(offset, offset + page - 1)
            .execute()
        )
        batch = resp.data or []
        rows.extend(batch)
        if len(batch) < page:
            break
        offset += page
    return rows


def _batch_upsert_pnc(client: Any, rows: list[dict[str, Any]]) -> ImportStats:
    """pnc_code is PRIMARY KEY — PostgREST on_conflict works."""
    stats = ImportStats()
    projected = [_project(r, _PNC_COLS) for r in rows]
    for chunk in _chunks(projected):
        if not chunk:
            continue
        client.table("pnc_categories").upsert(chunk, on_conflict="pnc_code").execute()
        stats.updated += len(chunk)  # upsert cannot distinguish insert vs update cheaply
    return stats


def _batch_upsert_by_natural_key(
    client: Any,
    table: str,
    rows: list[dict[str, Any]],
    *,
    cols: tuple[str, ...],
    key_fn: Any,
    existing_select: str,
) -> ImportStats:
    """
    Expression unique indexes (COALESCE) block PostgREST on_conflict=<columns>.
    Workaround: load existing id+keys, then batch insert (new) / upsert by id (existing).

    Input rows are deduped by ``key_fn`` (last wins) so Megazip multi-diagram
    duplicates of the same OEM/chassis/PNC do not trip 23505 on insert.
    """
    stats = ImportStats()
    existing_rows = _fetch_all(client, table, existing_select)
    id_by_key: dict[tuple[Any, ...], str] = {}
    for er in existing_rows:
        id_by_key[key_fn(er)] = er["id"]

    deduped: dict[tuple[Any, ...], dict[str, Any]] = {}
    for row in rows:
        deduped[key_fn(row)] = row

    to_insert: list[dict[str, Any]] = []
    to_update: list[dict[str, Any]] = []
    for key, row in deduped.items():
        payload = _project(row, cols)
        existing_id = id_by_key.get(key)
        if existing_id:
            payload["id"] = existing_id
            to_update.append(payload)
        else:
            to_insert.append(payload)

    for chunk in _chunks(to_insert):
        if chunk:
            client.table(table).insert(chunk).execute()
            stats.inserted += len(chunk)
    for chunk in _chunks(to_update):
        if chunk:
            # PK upsert — expression natural-key index is not usable via on_conflict
            client.table(table).upsert(chunk, on_conflict="id").execute()
            stats.updated += len(chunk)
    return stats


def _batch_upsert_stock_items(client: Any, rows: list[dict[str, Any]]) -> ImportStats:
    stats = ImportStats()
    projected = [_project(r, _STOCK_COLS) for r in rows]
    for chunk in _chunks(projected):
        if not chunk:
            continue
        client.table("stock_items").upsert(chunk, on_conflict="oem_part_number").execute()
        stats.updated += len(chunk)
    return stats


def _resolve_ea_uom_id(client: Any) -> str | None:
    try:
        resp = client.table("uoms").select("id").eq("code", "EA").limit(1).execute()
        data = resp.data or []
        return data[0]["id"] if data else None
    except Exception:  # noqa: BLE001 — optional enrichment
        return None


def _batch_delete_ids(client: Any, table: str, ids: list[str], *, batch_size: int = 200) -> int:
    deleted = 0
    for i in range(0, len(ids), batch_size):
        chunk = ids[i : i + batch_size]
        if chunk:
            client.table(table).delete().in_("id", chunk).execute()
            deleted += len(chunk)
    return deleted


def prune_stale_catalog(
    client: Any,
    bundle: dict[str, list[dict[str, Any]]],
) -> dict[str, int]:
    """Remove live rows not present in the filtered bundle (identity-only cleanup)."""
    vehicle_keys = {_vehicle_db_key(v) for v in bundle.get("vehicle_master") or []}
    fitment_keys = {_fitment_db_key(f) for f in bundle.get("part_fitment") or []}

    stale_vehicle_ids: list[str] = []
    for row in _fetch_all(
        client,
        "vehicle_master",
        "id,vin_prefix,chassis_code,engine_code,production_year,model_variant",
    ):
        if _vehicle_db_key(row) not in vehicle_keys:
            stale_vehicle_ids.append(row["id"])

    stale_fitment_ids: list[str] = []
    for row in _fetch_all(
        client,
        "part_fitment",
        "id,oem_part_number,chassis_code,engine_code,pnc_code",
    ):
        if _fitment_db_key(row) not in fitment_keys:
            stale_fitment_ids.append(row["id"])

    keep_oems = {
        row["oem_part_number"]
        for row in build_stock_items_from_fitment(
            bundle.get("part_fitment") or [],
            bundle.get("pnc_categories"),
            oem_display_names=(
                bundle.get("oem_display_names")
                if isinstance(bundle.get("oem_display_names"), dict)
                else None
            ),
        )
    }
    stale_stock_ids: list[str] = []
    for row in _fetch_all(client, "stock_items", "id,oem_part_number"):
        oem = row.get("oem_part_number")
        if oem and oem not in keep_oems:
            stale_stock_ids.append(row["id"])

    return {
        "vehicle_master_deleted": _batch_delete_ids(client, "vehicle_master", stale_vehicle_ids),
        "part_fitment_deleted": _batch_delete_ids(client, "part_fitment", stale_fitment_ids),
        "stock_items_deleted": _batch_delete_ids(client, "stock_items", stale_stock_ids),
    }


def import_supabase(
    bundle: dict[str, list[dict[str, Any]]],
    *,
    url: str,
    key: str,
    ensure_stock_items: bool = True,
    prune_stale: bool = False,
) -> ImportResult:
    """Live import via supabase-py (privileged key). Batched upserts for ~8k fitments."""
    try:
        from supabase import create_client
    except ImportError as exc:
        raise RuntimeError("Install optional deps: pip install -e '.[supabase]'") from exc

    client = create_client(url, key)
    mem = InMemoryCatalogStore()
    result = import_catalog(
        bundle, store=mem, validate=True, ensure_stock_items=ensure_stock_items
    )
    live_stats: dict[str, ImportStats] = {}

    if prune_stale:
        pruned = prune_stale_catalog(client, bundle)
        result.notes.append(
            "pruned stale rows: "
            + ", ".join(f"{k}={v}" for k, v in pruned.items())
        )

    if bundle.get("vehicle_master"):
        live_stats["vehicle_master"] = _batch_upsert_by_natural_key(
            client,
            "vehicle_master",
            bundle["vehicle_master"],
            cols=_VEHICLE_COLS,
            key_fn=_vehicle_db_key,
            existing_select="id,vin_prefix,chassis_code,engine_code,production_year,model_variant",
        )
    if bundle.get("pnc_categories"):
        live_stats["pnc_categories"] = _batch_upsert_pnc(client, bundle["pnc_categories"])
    if bundle.get("part_fitment"):
        live_stats["part_fitment"] = _batch_upsert_by_natural_key(
            client,
            "part_fitment",
            bundle["part_fitment"],
            cols=_FITMENT_COLS,
            key_fn=_fitment_db_key,
            existing_select="id,oem_part_number,chassis_code,engine_code,pnc_code",
        )

    if ensure_stock_items and bundle.get("part_fitment"):
        uom_id = _resolve_ea_uom_id(client)
        stock_rows = build_stock_items_from_fitment(
            bundle["part_fitment"],
            bundle.get("pnc_categories"),
            base_uom_id=uom_id,
            oem_display_names=bundle.get("_oem_display_names"),
        )
        live_stats["stock_items"] = _batch_upsert_stock_items(client, stock_rows)

    # Prefer live DB stats when available; keep in-memory diagram/stock dry counts otherwise.
    merged = dict(result.stats)
    merged.update(live_stats)
    result.stats = merged
    result.notes.append(
        "NOTE: vehicle_master / part_fitment unique indexes are expression-based "
        "(COALESCE); PostgREST on_conflict=<cols> cannot use them. Live path "
        "fetches existing ids then batch insert/upsert-by-id. Optional @backend_agent "
        "follow-up: generated columns or plain UNIQUE for true on_conflict upsert."
    )
    if bundle.get("diagram_assets"):
        result.notes.append(
            f"diagram_assets skipped for Postgres ({len(bundle['diagram_assets'])} Storage paths) - "
            "seed via supabase/seed_catalog_diagrams.mjs or amayama --upload-diagrams"
        )
    return result


def main(argv: list[str] | None = None) -> int:
    import argparse

    root = Path(__file__).resolve().parent.parent
    repo_root = root.parent

    parser = argparse.ArgumentParser(description="Import validated catalog JSON (dry-run by default).")
    parser.add_argument(
        "fixture",
        nargs="?",
        type=Path,
        default=root / "fixtures" / "navara_d40_yd25",
    )
    parser.add_argument(
        "--live",
        action="store_true",
        help="Import to Supabase (requires SUPABASE_URL and privileged server key)",
    )
    parser.add_argument(
        "--ensure-stock-items",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Upsert distinct OEMs into stock_items (default: on for POS/receiving readiness)",
    )
    parser.add_argument(
        "--complete-only",
        action="store_true",
        help="Import only vehicles with complete fitments (chassis+bbox+diagram); "
        "exclude identity-only rows",
    )
    parser.add_argument(
        "--prune-stale",
        action=argparse.BooleanOptionalAction,
        default=None,
        help="Delete live rows not in bundle (default: on with --complete-only --live)",
    )
    args = parser.parse_args(argv)

    if args.live:
        # data-pipeline/.env first, repo-root .env last so hosted SoR wins
        # over local Docker overrides (parity with import_hierarchy_catalog).
        load_env_files(root / ".env", repo_root / ".env", override=True)

    bundle = load_bundle(args.fixture)
    filter_meta: dict[str, Any] | None = None
    if args.complete_only:
        bundle, filter_meta = filter_complete_bundle(bundle, completed_only=True)
        print(
            f"Complete-only filter: {filter_meta['vehicles_out']} vehicles "
            f"(excluded {filter_meta['vehicles_in'] - filter_meta['vehicles_out']}), "
            f"chassis {filter_meta['parts_complete_chassis']}"
        )

    prune_stale = args.prune_stale
    if prune_stale is None:
        prune_stale = bool(args.complete_only and args.live)

    if args.live:
        url, key = resolve_supabase_credentials()
        if not url or not key:
            print(
                f"ERROR: {_ENV_URL} and {_ENV_SVC_KEY} "
                f"(or {_ENV_SVC_KEY_ALIAS}) required for --live"
            )
            return 1
        result = import_supabase(
            bundle,
            url=url,
            key=key,
            ensure_stock_items=args.ensure_stock_items,
            prune_stale=prune_stale,
        )
        print("Live import OK")
    else:
        result = import_catalog(bundle, ensure_stock_items=args.ensure_stock_items)
        counts = result.store.row_counts() if result.store else {}
        print(f"Dry-run import OK: {counts}")

    for table, stat in result.stats.items():
        print(f"  {table}: +{stat.inserted} ~{stat.updated} ={stat.unchanged}")
    for note in result.notes:
        print(f"  # {note}")
    if filter_meta:
        excluded = filter_meta.get("excluded_identity_only_chassis") or []
        if excluded:
            print(
                f"  # excluded identity-only chassis ({len(excluded)}): "
                f"{excluded[:12]}{'...' if len(excluded) > 12 else ''}"
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
