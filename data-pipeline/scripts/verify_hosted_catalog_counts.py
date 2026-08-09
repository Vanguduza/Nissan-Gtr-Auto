"""Verify hosted catalog row counts using privileged credentials from .env."""

from __future__ import annotations

import sys
from pathlib import Path
from urllib.parse import urlparse

import httpx

ROOT = Path(__file__).resolve().parents[2]
PIPELINE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PIPELINE))

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials  # noqa: E402


def main() -> int:
    load_env_files(PIPELINE / ".env", ROOT / ".env", override=True)
    url, key = resolve_supabase_credentials()
    host = urlparse(url or "").hostname or ""
    print(f"host={host} key_present={bool(key)}")
    if not url or not key:
        return 2
    tables = [
        "vehicle_master",
        "pnc_categories",
        "part_fitment",
        "stock_items",
        "catalog_makers",
        "catalog_models",
        "catalog_variants",
        "catalog_sections",
    ]
    with httpx.Client(timeout=60.0) as client:
        for t in tables:
            r = client.get(
                f"{url}/rest/v1/{t}",
                params={"select": "*", "limit": "1"},
                headers={
                    "apikey": key,
                    "Authorization": f"Bearer {key}",
                    "Prefer": "count=exact",
                    "Range": "0-0",
                },
            )
            print(t, "status", r.status_code, "range", r.headers.get("content-range"), r.text[:80])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
