"""Full sync Supabase catalog tables → Meilisearch `parts` index."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import UTC, datetime
from typing import Any

from data_pipeline.meili_documents import build_catalog_documents

INDEX_UID = "parts"
PAGE_SIZE = 1000


def _env(name: str, default: str | None = None) -> str:
    val = os.environ.get(name, default)
    if not val:
        raise RuntimeError(f"Missing required env: {name}")
    return val


def _meili_request(
    host: str,
    api_key: str,
    method: str,
    path: str,
    body: dict[str, Any] | list[Any] | None = None,
) -> Any:
    url = f"{host.rstrip('/')}{path}"
    data = None
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Meili {method} {path} failed ({exc.code}): {detail}") from exc


def ensure_index(host: str, master_key: str) -> None:
    try:
        _meili_request(host, master_key, "GET", f"/indexes/{INDEX_UID}")
    except RuntimeError:
        _meili_request(
            host,
            master_key,
            "POST",
            "/indexes",
            {"uid": INDEX_UID, "primaryKey": "id"},
        )
    _meili_request(
        host,
        master_key,
        "PATCH",
        f"/indexes/{INDEX_UID}/settings",
        {
            "searchableAttributes": [
                "search_blob",
                "oem_part_number",
                "description",
                "pnc_code",
                "category_name",
                "subcategory_name",
                "model_variant",
                "vin_prefix",
                "chassis_code",
                "engine_code",
                "oe_numbers",
                "superseded_by",
            ],
            "filterableAttributes": [
                "doc_kind",
                "category_name",
                "pnc_code",
                "chassis_code",
                "model_variant",
            ],
            "sortableAttributes": ["oem_part_number", "model_variant", "pnc_code"],
            "typoTolerance": {"enabled": True},
        },
    )


def fetch_all(supabase: Any, table: str, select: str = "*") -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    offset = 0
    while True:
        resp = (
            supabase.table(table)
            .select(select)
            .range(offset, offset + PAGE_SIZE - 1)
            .execute()
        )
        batch = resp.data or []
        rows.extend(batch)
        if len(batch) < PAGE_SIZE:
            break
        offset += PAGE_SIZE
    return rows


def push_documents(host: str, master_key: str, documents: list[dict[str, Any]]) -> int:
    if not documents:
        return 0
    chunk = 500
    task_uid: int | None = None
    for i in range(0, len(documents), chunk):
        result = _meili_request(
            host,
            master_key,
            "POST",
            f"/indexes/{INDEX_UID}/documents",
            documents[i : i + chunk],
        )
        if isinstance(result, dict) and result.get("taskUid") is not None:
            task_uid = int(result["taskUid"])
    return task_uid or 0


def update_sync_state(supabase: Any, document_count: int, task_uid: int) -> None:
    supabase.table("catalog_meili_sync_state").upsert(
        {
            "id": 1,
            "last_full_sync_at": datetime.now(UTC).isoformat(),
            "document_count": document_count,
            "index_uid": INDEX_UID,
            "meili_task_uid": task_uid or None,
            "updated_at": datetime.now(UTC).isoformat(),
        }
    ).execute()


def run_full_sync(
    *,
    supabase_url: str | None = None,
    supabase_key: str | None = None,
    meili_host: str | None = None,
    meili_master_key: str | None = None,
    dry_run: bool = False,
) -> dict[str, Any]:
    supabase_url = supabase_url or _env("SUPABASE_URL")
    supabase_key = supabase_key or _env("SUPABASE_SERVICE_KEY", os.environ.get("SUPABASE_SERVICE_ROLE_KEY"))
    meili_host = meili_host or _env("MEILI_HOST", "http://127.0.0.1:7700")
    meili_master_key = meili_master_key or _env("MEILI_MASTER_KEY")

    try:
        from supabase import create_client
    except ImportError as exc:
        raise RuntimeError("Install supabase: pip install -e '.[supabase]'") from exc

    sb = create_client(supabase_url, supabase_key)

    vehicle_master = fetch_all(sb, "vehicle_master")
    pnc_categories = fetch_all(sb, "pnc_categories")
    part_fitment = fetch_all(sb, "part_fitment")
    stock_items = fetch_all(sb, "stock_items", "oem_part_number,description")
    oe_cross_refs = fetch_all(sb, "oe_cross_refs")

    documents = build_catalog_documents(
        vehicle_master=vehicle_master,
        pnc_categories=pnc_categories,
        part_fitment=part_fitment,
        stock_items=stock_items,
        oe_cross_refs=oe_cross_refs,
    )

    summary = {
        "documents": len(documents),
        "vehicle_master": len(vehicle_master),
        "pnc_categories": len(pnc_categories),
        "part_fitment": len(part_fitment),
        "stock_items": len(stock_items),
        "oe_cross_refs": len(oe_cross_refs),
        "dry_run": dry_run,
    }

    if dry_run:
        return summary

    ensure_index(meili_host, meili_master_key)
    task_uid = push_documents(meili_host, meili_master_key, documents)
    try:
        update_sync_state(sb, len(documents), task_uid)
    except Exception as exc:  # noqa: BLE001 — migration may not be applied locally
        summary["sync_state_warning"] = str(exc)

    summary["meili_task_uid"] = task_uid
    return summary


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Sync Supabase catalog → Meilisearch parts index")
    parser.add_argument("--full", action="store_true", help="Run full sync (default)")
    parser.add_argument("--dry-run", action="store_true", help="Build documents only; no Meili push")
    args = parser.parse_args(argv)

    if not args.full and not args.dry_run:
        parser.error("Specify --full and/or --dry-run")

    summary = run_full_sync(dry_run=args.dry_run)
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
