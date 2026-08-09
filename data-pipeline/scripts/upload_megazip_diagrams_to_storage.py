"""Download (if needed) and upload Megazip publish-bundle diagrams to Storage.

Maps megazip/ → epc/ storage paths. Vendor CDN URLs stay local to this script only.
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
from pathlib import Path

import httpx

from data_pipeline.import_catalog import load_env_files, resolve_supabase_credentials

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
logger = logging.getLogger("upload_diagrams")

BUCKET = "catalog-diagrams"
CONCURRENCY = 12


def _epc_path(storage_path: str) -> str:
    if storage_path.lower().startswith("megazip/"):
        return "epc/" + storage_path[len("megazip/") :]
    return storage_path


async def _ensure_local(
    client: httpx.AsyncClient,
    sem: asyncio.Semaphore,
    url: str,
    dest: Path,
) -> Path | None:
    if dest.is_file() and dest.stat().st_size > 0:
        return dest
    if not url:
        return dest if dest.is_file() else None
    async with sem:
        for attempt in range(4):
            try:
                resp = await client.get(url)
                if resp.status_code == 200 and resp.content:
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    dest.write_bytes(resp.content)
                    return dest
            except Exception:  # noqa: BLE001
                await asyncio.sleep(0.4 * (attempt + 1))
    return None


async def _upload_one(
    client: httpx.AsyncClient,
    sem: asyncio.Semaphore,
    base: str,
    key: str,
    local: Path,
    storage_path: str,
) -> bool:
    async with sem:
        url = f"{base}/storage/v1/object/{BUCKET}/{storage_path}"
        headers = {
            "apikey": key,
            "Authorization": f"Bearer {key}",
            "Content-Type": "image/png",
            "x-upsert": "true",
        }
        data = local.read_bytes()
        for attempt in range(5):
            try:
                resp = await client.post(url, content=data, headers=headers)
                if resp.status_code in (200, 201):
                    return True
                # some gateways use PUT for upsert
                if resp.status_code in (400, 409):
                    resp = await client.put(url, content=data, headers=headers)
                    if resp.status_code in (200, 201):
                        return True
                if resp.status_code in (429, 500, 502, 503, 504):
                    await asyncio.sleep(0.5 * (attempt + 1))
                    continue
                logger.warning("upload %s -> %s %s", storage_path, resp.status_code, resp.text[:120])
                return False
            except Exception as exc:  # noqa: BLE001
                await asyncio.sleep(0.5 * (attempt + 1))
                if attempt == 4:
                    logger.warning("upload fail %s: %s", storage_path, exc)
                    return False
        return False


async def main_async() -> int:
    repo = Path(__file__).resolve().parents[2]
    pipe = Path(__file__).resolve().parents[1]
    load_env_files(pipe / ".env", repo / ".env", override=True)
    url, key = resolve_supabase_credentials()
    if not url or not key:
        logger.error("missing credentials")
        return 2
    base = url.rstrip("/")
    bundle_path = pipe / "out" / "megazip" / "nissan" / "bundle" / "catalog_diagrams.json"
    diagrams_dir = pipe / "out" / "megazip" / "nissan" / "diagrams"
    diagrams_dir.mkdir(parents=True, exist_ok=True)

    logger.info("loading %s", bundle_path)
    rows = json.loads(bundle_path.read_text(encoding="utf-8"))
    # unique by epc storage path
    by_path: dict[str, dict] = {}
    for row in rows:
        sp = row.get("storage_path") or ""
        if not sp:
            continue
        epc = _epc_path(sp)
        prev = by_path.get(epc)
        if not prev or (row.get("image_url") and not prev.get("image_url")):
            by_path[epc] = row
    logger.info("unique diagram paths=%s (from %s rows)", len(by_path), len(rows))

    timeout = httpx.Timeout(120.0, connect=30.0)
    limits = httpx.Limits(max_connections=CONCURRENCY + 8, max_keepalive_connections=CONCURRENCY)
    sem = asyncio.Semaphore(CONCURRENCY)
    uploaded = 0
    failed = 0
    missing = 0

    async with httpx.AsyncClient(timeout=timeout, limits=limits, http2=False, follow_redirects=True) as client:
        items = list(by_path.items())
        batch_size = 100
        for i in range(0, len(items), batch_size):
            chunk = items[i : i + batch_size]

            async def one(epc: str, row: dict) -> str:
                name = Path(epc).name
                dest = diagrams_dir / name
                local = await _ensure_local(client, sem, row.get("image_url") or "", dest)
                if not local:
                    return "missing"
                ok = await _upload_one(client, sem, base, key, local, epc)
                return "ok" if ok else "fail"

            results = await asyncio.gather(*[one(epc, row) for epc, row in chunk])
            uploaded += results.count("ok")
            failed += results.count("fail")
            missing += results.count("missing")
            logger.info(
                "progress %s/%s uploaded=%s failed=%s missing=%s",
                min(i + batch_size, len(items)),
                len(items),
                uploaded,
                failed,
                missing,
            )

    logger.info("DONE uploaded=%s failed=%s missing=%s", uploaded, failed, missing)
    return 0 if failed == 0 else 1


def main() -> int:
    if os.name == "nt":
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
    return asyncio.run(main_async())


if __name__ == "__main__":
    raise SystemExit(main())
