"""Idempotent catalog import — natural-key upsert (Python-side or Supabase)."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Protocol

from data_pipeline.validate import SCHEMA_NAMES, validate_bundle

# Natural keys used for idempotent upsert (documented for DB unique indexes).
VEHICLE_KEY = ("vin_prefix", "chassis_code", "engine_code", "production_year", "model_variant")
PNC_KEY = ("pnc_code",)
FITMENT_KEY = ("oem_part_number", "chassis_code", "engine_code", "pnc_code")
OE_CROSS_REF_KEY = ("oem_part_number", "oe_number", "brand")


def _key_tuple(record: dict[str, Any], fields: tuple[str, ...]) -> tuple[Any, ...]:
    return tuple(record.get(f) for f in fields)


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

    def row_counts(self) -> dict[str, int]:
        return {
            "vehicle_master": len(self.vehicle_master),
            "pnc_categories": len(self.pnc_categories),
            "part_fitment": len(self.part_fitment),
            "oe_cross_refs": len(self.oe_cross_refs),
            "diagram_assets": len(self.diagram_assets),
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
        return bundle

    with path.open(encoding="utf-8") as fh:
        payload = json.load(fh)
    if not isinstance(payload, dict):
        raise ValueError(f"Expected object bundle in {path}")
    return payload


@dataclass
class ImportResult:
    stats: dict[str, ImportStats]
    store: InMemoryCatalogStore | None = None


def import_catalog(
    bundle: dict[str, list[dict[str, Any]]],
    store: CatalogStore | None = None,
    *,
    oe_cross_refs: list[dict[str, Any]] | None = None,
    validate: bool = True,
) -> ImportResult:
    if validate:
        validate_bundle(bundle)

    mem = store if store is not None else InMemoryCatalogStore()
    stats: dict[str, ImportStats] = {}

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
        stats["diagram_assets"] = diagram_stats

    return ImportResult(stats=stats, store=mem if isinstance(mem, InMemoryCatalogStore) else None)


def import_supabase(bundle: dict[str, list[dict[str, Any]]], *, url: str, key: str) -> ImportResult:
    """Optional live import via supabase-py (service role). Not used in unit tests."""
    try:
        from supabase import create_client
    except ImportError as exc:
        raise RuntimeError("Install optional deps: pip install data_pipeline[supabase]") from exc

    client = create_client(url, key)
    mem = InMemoryCatalogStore()
    result = import_catalog(bundle, store=mem, validate=True)

    def _upsert_rows(table: str, rows: list[dict[str, Any]], key_fields: tuple[str, ...]) -> None:
        for row in rows:
            q = client.table(table).select("*")
            for field in key_fields:
                value = row.get(field)
                if value is None:
                    q = q.is_(field, "null")
                else:
                    q = q.eq(field, value)
            existing = q.limit(1).execute().data
            if existing:
                client.table(table).update(row).eq("id", existing[0]["id"]).execute()
            else:
                client.table(table).insert(row).execute()

    _upsert_rows("vehicle_master", bundle.get("vehicle_master", []), VEHICLE_KEY)
    for row in bundle.get("pnc_categories", []):
        client.table("pnc_categories").upsert(row, on_conflict="pnc_code").execute()
    _upsert_rows("part_fitment", bundle.get("part_fitment", []), FITMENT_KEY)

    return result


def main(argv: list[str] | None = None) -> int:
    import argparse

    parser = argparse.ArgumentParser(description="Import validated catalog JSON (dry-run by default).")
    parser.add_argument(
        "fixture",
        nargs="?",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "fixtures" / "navara_d40_yd25",
    )
    parser.add_argument(
        "--live",
        action="store_true",
        help="Import to Supabase (requires SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY)",
    )
    args = parser.parse_args(argv)

    bundle = load_bundle(args.fixture)
    if args.live:
        import os

        url = os.environ.get("SUPABASE_URL")
        key = os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
        if not url or not key:
            print("ERROR: SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY required for --live")
            return 1
        result = import_supabase(bundle, url=url, key=key)
    else:
        result = import_catalog(bundle)
        counts = result.store.row_counts() if result.store else {}
        print(f"Dry-run import OK: {counts}")

    for table, stat in result.stats.items():
        print(f"  {table}: +{stat.inserted} ~{stat.updated} ={stat.unchanged}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
