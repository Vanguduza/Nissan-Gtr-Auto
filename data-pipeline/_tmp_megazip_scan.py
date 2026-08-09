"""Scan hosted catalog tables for megazip string leakage."""

from __future__ import annotations

from pathlib import Path
from urllib.parse import urlparse

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials
from supabase import create_client

load_env_files(Path(".env"), Path("../.env"), override=True)
url, key = resolve_supabase_credentials()
print("host", urlparse(url or "").hostname)
client = create_client(url, key)


def count_ilike(table: str, column: str) -> int:
    resp = (
        client.table(table)
        .select(column, count="exact")
        .ilike(column, "%megazip%")
        .limit(1)
        .execute()
    )
    return int(resp.count or 0)


def sample_ilike(table: str, columns: str, column: str, n: int = 3) -> list:
    resp = (
        client.table(table)
        .select(columns)
        .ilike(column, "%megazip%")
        .limit(n)
        .execute()
    )
    return resp.data or []


checks = [
    ("catalog_makers", "source", "slug,name,source"),
    ("catalog_models", "source_url", "slug,source_url"),
    ("catalog_variants", "source_url", "slug,source_url,megazip_data_id"),
    ("catalog_variants", "megazip_data_id", "slug,megazip_data_id"),
    ("catalog_sections", "source_url", "slug,source_url"),
    ("catalog_diagrams", "storage_path", "slug,storage_path,source_url,image_url"),
    ("catalog_diagrams", "source_url", "slug,storage_path,source_url"),
    ("catalog_diagrams", "image_url", "slug,image_url"),
    ("catalog_diagram_parts", "megazip_item_id", "oem_part_number,megazip_item_id"),
    ("catalog_diagram_parts", "diagram_path", "oem_part_number,diagram_path"),
    ("part_fitment", "diagram_path", "oem_part_number,diagram_path"),
]

for table, col, cols in checks:
    try:
        n = count_ilike(table, col)
        print(f"{table}.{col}: {n}")
        if n:
            print("  sample", sample_ilike(table, cols, col))
    except Exception as exc:  # noqa: BLE001
        print(f"{table}.{col}: ERR {exc}")
