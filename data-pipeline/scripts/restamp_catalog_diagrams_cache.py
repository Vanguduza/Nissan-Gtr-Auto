"""Restamp Cache-Control on existing ``catalog-diagrams`` Storage objects.

Re-upserts the same paths with long ``cache-control`` (immutable diagrams).
Does **not** delete or wipe the bucket.

Prefer local bytes under ``out/megazip/<maker>/diagrams/`` when present;
otherwise download from Storage then re-upload.

Usage (repo root or data-pipeline/)::

  python data-pipeline/scripts/restamp_catalog_diagrams_cache.py
  python data-pipeline/scripts/restamp_catalog_diagrams_cache.py --force
  python data-pipeline/scripts/restamp_catalog_diagrams_cache.py --dry-run

Credentials: repo-root ``.env`` wins over ``data-pipeline/.env`` (override=True).
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import os
import sys
from pathlib import Path

import httpx

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials  # noqa: E402
from data_pipeline.storage_diagrams import (  # noqa: E402
    DIAGRAM_CACHE_CONTROL,
    DIAGRAMS_BUCKET,
    content_type_for_path,
    rest_upload_headers,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
logger = logging.getLogger("restamp_diagrams")

CONCURRENCY = 16
LIST_PAGE = 1000


def _walk_objects(
    client: httpx.Client,
    base: str,
    key: str,
    prefix: str = "",
) -> list[str]:
    headers = {
        "apikey": key,
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
    }
    files: list[str] = []
    folders: list[str] = []
    offset = 0
    while True:
        resp = client.post(
            f"{base}/storage/v1/object/list/{DIAGRAMS_BUCKET}",
            headers=headers,
            json={
                "prefix": prefix,
                "limit": LIST_PAGE,
                "offset": offset,
                "sortBy": {"column": "name", "order": "asc"},
            },
        )
        resp.raise_for_status()
        batch = resp.json()
        if not isinstance(batch, list) or not batch:
            break
        for item in batch:
            name = item.get("name") or ""
            full = f"{prefix}{name}" if prefix else name
            if item.get("id"):
                files.append(full)
            else:
                folders.append(f"{full}/")
        if len(batch) < LIST_PAGE:
            break
        offset += len(batch)
    for folder in folders:
        files.extend(_walk_objects(client, base, key, folder))
    return files


def _local_candidates(repo: Path, object_path: str) -> list[Path]:
    name = Path(object_path).name
    roots = [
        repo / "data-pipeline" / "out" / "megazip" / "nissan" / "diagrams",
        ROOT / "out" / "megazip" / "nissan" / "diagrams",
        ROOT / "out" / "diagram_downloads",
    ]
    return [r / name for r in roots]


def _read_local(object_path: str, repo: Path) -> bytes | None:
    for candidate in _local_candidates(repo, object_path):
        if candidate.is_file() and candidate.stat().st_size > 0:
            return candidate.read_bytes()
    return None


async def _head_cache_ok(
    client: httpx.AsyncClient,
    base: str,
    object_path: str,
) -> bool:
    # Smart CDN often returns Cache-Control: no-cache on HEAD even when GET is correct.
    url = f"{base}/storage/v1/object/public/{DIAGRAMS_BUCKET}/{object_path}?cachecheck=1"
    try:
        resp = await client.get(url)
        cc = (resp.headers.get("cache-control") or "").lower()
        return "max-age=" in cc and "no-cache" not in cc
    except Exception:  # noqa: BLE001
        return False


async def _restamp_one(
    client: httpx.AsyncClient,
    sem: asyncio.Semaphore,
    *,
    base: str,
    key: str,
    object_path: str,
    repo: Path,
    force: bool,
    dry_run: bool,
) -> str:
    async with sem:
        if not force and await _head_cache_ok(client, base, object_path):
            return "skipped"

        data = _read_local(object_path, repo)
        if data is None:
            auth_url = f"{base}/storage/v1/object/authenticated/{DIAGRAMS_BUCKET}/{object_path}"
            try:
                resp = await client.get(
                    auth_url,
                    headers={"apikey": key, "Authorization": f"Bearer {key}"},
                )
                if resp.status_code != 200 or not resp.content:
                    logger.warning("download fail %s -> %s", object_path, resp.status_code)
                    return "fail"
                data = resp.content
            except Exception as exc:  # noqa: BLE001
                logger.warning("download fail %s: %s", object_path, exc)
                return "fail"

        if dry_run:
            return "ok"

        ctype = content_type_for_path(object_path)
        headers = rest_upload_headers(api_key=key, content_type=ctype)
        upload_url = f"{base}/storage/v1/object/{DIAGRAMS_BUCKET}/{object_path}"
        for attempt in range(5):
            try:
                resp = await client.post(upload_url, content=data, headers=headers)
                if resp.status_code in (200, 201):
                    return "ok"
                if resp.status_code in (400, 409):
                    resp = await client.put(upload_url, content=data, headers=headers)
                    if resp.status_code in (200, 201):
                        return "ok"
                if resp.status_code in (429, 500, 502, 503, 504):
                    await asyncio.sleep(0.5 * (attempt + 1))
                    continue
                logger.warning(
                    "upload %s -> %s %s",
                    object_path,
                    resp.status_code,
                    resp.text[:120],
                )
                return "fail"
            except Exception as exc:  # noqa: BLE001
                await asyncio.sleep(0.5 * (attempt + 1))
                if attempt == 4:
                    logger.warning("upload fail %s: %s", object_path, exc)
                    return "fail"
        return "fail"


async def main_async(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Restamp catalog-diagrams Cache-Control")
    parser.add_argument(
        "--force",
        action="store_true",
        help="Re-upsert even when public HEAD already has max-age",
    )
    parser.add_argument("--dry-run", action="store_true", help="List/plan only; no uploads")
    parser.add_argument("--concurrency", type=int, default=CONCURRENCY)
    args = parser.parse_args(argv)

    repo = Path(__file__).resolve().parents[2]
    load_env_files(ROOT / ".env", repo / ".env", override=True)
    url, key = resolve_supabase_credentials()
    if not url or not key:
        logger.error("missing SUPABASE_URL / service role key")
        return 2
    base = url.rstrip("/")

    logger.info(
        "listing %s (cache-control target=%s)",
        DIAGRAMS_BUCKET,
        DIAGRAM_CACHE_CONTROL,
    )
    with httpx.Client(timeout=120.0) as sync:
        paths = _walk_objects(sync, base, key)
    logger.info("objects=%s", len(paths))
    if not paths:
        return 0

    sem = asyncio.Semaphore(max(1, args.concurrency))
    timeout = httpx.Timeout(120.0, connect=30.0)
    limits = httpx.Limits(
        max_connections=args.concurrency + 8,
        max_keepalive_connections=args.concurrency,
    )
    ok = fail = skipped = 0

    async with httpx.AsyncClient(
        timeout=timeout, limits=limits, http2=False, follow_redirects=True
    ) as client:
        batch_size = 100
        for i in range(0, len(paths), batch_size):
            chunk = paths[i : i + batch_size]
            results = await asyncio.gather(
                *[
                    _restamp_one(
                        client,
                        sem,
                        base=base,
                        key=key,
                        object_path=p,
                        repo=repo,
                        force=args.force,
                        dry_run=args.dry_run,
                    )
                    for p in chunk
                ]
            )
            ok += results.count("ok")
            fail += results.count("fail")
            skipped += results.count("skipped")
            logger.info(
                "progress %s/%s restamped=%s failed=%s skipped=%s",
                min(i + batch_size, len(paths)),
                len(paths),
                ok,
                fail,
                skipped,
            )

    logger.info(
        "DONE restamped=%s failed=%s skipped=%s dry_run=%s",
        ok,
        fail,
        skipped,
        args.dry_run,
    )
    return 0 if fail == 0 else 1


def main() -> int:
    if os.name == "nt":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    return asyncio.run(main_async())


if __name__ == "__main__":
    raise SystemExit(main())
