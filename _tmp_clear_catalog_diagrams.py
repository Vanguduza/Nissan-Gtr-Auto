"""One-shot: list + delete all objects in catalog-diagrams Storage bucket.

Loads credentials from gitignored .env. Never prints secrets.
"""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BUCKET = "catalog-diagrams"
PROJECT_REF = "gylrgwqyuiwkyykardwc"
BATCH = 100


def load_env(path: Path) -> dict[str, str]:
    vals: dict[str, str] = {}
    if not path.is_file():
        return vals
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        vals[k.strip()] = v.strip().strip("\"'")
    return vals


def api_request(
    method: str,
    url: str,
    *,
    key: str,
    body: bytes | None = None,
    content_type: str | None = None,
) -> tuple[int, bytes]:
    headers = {
        "Authorization": f"Bearer {key}",
        "apikey": key,
    }
    if content_type:
        headers["Content-Type"] = content_type
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def list_prefixes(base: str, key: str, bucket: str, prefix: str = "") -> list[str]:
    """List folder prefixes at one level via limit/offset listing with prefix."""
    # Storage list API: POST /storage/v1/object/list/{bucket}
    url = f"{base}/storage/v1/object/list/{urllib.parse.quote(bucket)}"
    payload = json.dumps(
        {"prefix": prefix, "limit": 1000, "offset": 0, "delimiter": "/"}
    ).encode()
    status, raw = api_request(
        "POST", url, key=key, body=payload, content_type="application/json"
    )
    if status != 200:
        raise RuntimeError(f"list prefixes failed {status}: {raw[:500]!r}")
    rows = json.loads(raw.decode())
    # With delimiter, folders appear as {name, id:null} or similar
    prefixes = []
    for row in rows:
        name = row.get("name") or ""
        # folder markers often have id None and metadata None
        if row.get("id") is None and name and not name.endswith("/"):
            # may be a "folder" entry under delimiter listing
            prefixes.append(name if prefix == "" else f"{prefix}{name}")
        elif row.get("id") is None and name:
            prefixes.append(name if name.endswith("/") else f"{name}/")
    return prefixes


def list_all_objects(base: str, key: str, bucket: str) -> list[str]:
    """Recursively list all object paths (no delimiter) with pagination."""
    url = f"{base}/storage/v1/object/list/{urllib.parse.quote(bucket)}"
    paths: list[str] = []
    offset = 0
    while True:
        payload = json.dumps(
            {
                "prefix": "",
                "limit": 1000,
                "offset": offset,
                # no delimiter → flat recursive? Actually Storage list is NOT recursive
                # without walking prefixes. We'll walk.
            }
        ).encode()
        status, raw = api_request(
            "POST", url, key=key, body=payload, content_type="application/json"
        )
        if status != 200:
            raise RuntimeError(f"list root failed {status}: {raw[:500]!r}")
        rows = json.loads(raw.decode())
        if not rows:
            break
        for row in rows:
            name = row.get("name") or ""
            if row.get("id") is not None:
                paths.append(name)
            # else folder — handled by walk
        if len(rows) < 1000:
            break
        offset += 1000
    return paths


def walk_all_objects(base: str, key: str, bucket: str) -> tuple[list[str], list[str]]:
    """BFS walk prefixes; return (object_paths, top_level_prefixes)."""
    url = f"{base}/storage/v1/object/list/{urllib.parse.quote(bucket)}"
    objects: list[str] = []
    top_prefixes: list[str] = []
    queue: list[str] = [""]
    seen_dirs: set[str] = set()

    while queue:
        prefix = queue.pop(0)
        if prefix in seen_dirs:
            continue
        seen_dirs.add(prefix)
        offset = 0
        while True:
            payload = json.dumps(
                {
                    "prefix": prefix,
                    "limit": 1000,
                    "offset": offset,
                    "delimiter": "/",
                }
            ).encode()
            status, raw = api_request(
                "POST", url, key=key, body=payload, content_type="application/json"
            )
            if status != 200:
                raise RuntimeError(
                    f"list prefix={prefix!r} failed {status}: {raw[:500]!r}"
                )
            rows = json.loads(raw.decode())
            if not rows:
                break
            for row in rows:
                name = (row.get("name") or "").strip("/")
                if not name:
                    continue
                full = f"{prefix}{name}" if prefix else name
                if row.get("id") is None:
                    # folder
                    folder = full if full.endswith("/") else f"{full}/"
                    if prefix == "":
                        top_prefixes.append(folder.rstrip("/"))
                    if folder not in seen_dirs:
                        queue.append(folder)
                else:
                    objects.append(full)
            if len(rows) < 1000:
                break
            offset += 1000

    return objects, sorted(set(top_prefixes))


def delete_objects(base: str, key: str, bucket: str, paths: list[str]) -> int:
    """Bulk remove; returns count requested."""
    url = f"{base}/storage/v1/object/{urllib.parse.quote(bucket)}"
    deleted = 0
    for i in range(0, len(paths), BATCH):
        chunk = paths[i : i + BATCH]
        payload = json.dumps({"prefixes": chunk}).encode()
        status, raw = api_request(
            "DELETE", url, key=key, body=payload, content_type="application/json"
        )
        if status not in (200, 204):
            raise RuntimeError(
                f"delete batch @{i} failed {status}: {raw[:800]!r}"
            )
        deleted += len(chunk)
        print(f"  deleted batch {i // BATCH + 1}: {len(chunk)} (total {deleted})")
    return deleted


def list_buckets(base: str, key: str) -> list[dict]:
    status, raw = api_request("GET", f"{base}/storage/v1/bucket", key=key)
    if status != 200:
        raise RuntimeError(f"list buckets failed {status}: {raw[:500]!r}")
    return json.loads(raw.decode())


def main() -> int:
    # Prefer root .env (hosted) over data-pipeline/.env (often local).
    # Merge local first, then root overrides; process env wins last.
    env: dict[str, str] = {}
    for p in (ROOT / "data-pipeline" / ".env", ROOT / ".env"):
        env.update(load_env(p))
    for k in (
        "SUPABASE_URL",
        "NEXT_PUBLIC_SUPABASE_URL",
        "SUPABASE_SERVICE_KEY",
        "SUPABASE_SERVICE_ROLE_KEY",
    ):
        if os.environ.get(k):
            env[k] = os.environ[k]

    candidates = [
        env.get("SUPABASE_URL") or "",
        env.get("NEXT_PUBLIC_SUPABASE_URL") or "",
    ]
    base = ""
    for c in candidates:
        c = c.rstrip("/")
        if PROJECT_REF in c:
            base = c
            break
    if not base:
        base = (candidates[0] or candidates[1]).rstrip("/")

    key = env.get("SUPABASE_SERVICE_ROLE_KEY") or env.get("SUPABASE_SERVICE_KEY") or ""
    if not base or not key:
        print("ERROR: missing SUPABASE_URL or service role key in .env", file=sys.stderr)
        return 1

    from urllib.parse import urlparse

    host = urlparse(base).hostname or ""
    if PROJECT_REF not in host:
        print(
            f"ERROR: URL host {host!r} does not contain project ref {PROJECT_REF}",
            file=sys.stderr,
        )
        return 1

    print(f"project_host={host}")
    print("Listing buckets…")
    buckets = list_buckets(base, key)
    bucket_ids = [b.get("id") or b.get("name") for b in buckets]
    print(f"buckets ({len(bucket_ids)}): {', '.join(sorted(x for x in bucket_ids if x))}")

    diagram_related = [
        b
        for b in bucket_ids
        if b
        and (
            "diagram" in b.lower()
            or b == "catalog-diagrams"
            or "catalog" in b.lower() and "diagram" in b.lower()
        )
    ]
    # Only touch catalog-diagrams (and any bucket with 'diagram' in the name)
    targets = sorted(set(diagram_related) | ({BUCKET} if BUCKET in bucket_ids else set()))
    if BUCKET not in bucket_ids:
        print(f"WARNING: {BUCKET} not in bucket list")
    print(f"targets: {targets}")

    total_deleted = 0
    for bucket in targets:
        print(f"\n=== {bucket} ===")
        objects, prefixes = walk_all_objects(base, key, bucket)
        print(f"top_prefixes: {prefixes or '(none)'}")
        print(f"object_count: {len(objects)}")
        if objects:
            # sample a few paths
            sample = objects[:5]
            print(f"sample: {sample}")
            print("Deleting…")
            total_deleted += delete_objects(base, key, bucket, objects)
        else:
            print("already empty")

        # verify
        objects2, prefixes2 = walk_all_objects(base, key, bucket)
        print(f"verify_object_count: {len(objects2)}")
        print(f"verify_top_prefixes: {prefixes2 or '(none)'}")
        if objects2:
            print(f"REMAINING sample: {objects2[:10]}")
            return 2

    print(f"\nDONE deleted≈{total_deleted} objects across {targets}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
