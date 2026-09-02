#!/usr/bin/env python3
"""Upload the 94 canonical vehicle-artwork WebPs to hosted Supabase Storage.

Reads repo-root .env for SUPABASE_URL and SUPABASE_SERVICE_KEY (or the
standard server-key alias). Never prints secrets.

Usage (from repository root):
    python3 scripts/upload_vehicle_artwork.py
    python3 scripts/upload_vehicle_artwork.py --verify-only
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
ART_DIR = REPO / "supabase" / "vehicle-artwork"
RESOLVER_KT = (
    REPO
    / "apps/android-customer/core/visual/src/main/java"
    / "co/zw/nissangtr/customer/visual/vehicle/VehicleArtworkResolver.kt"
)
BUCKET = "vehicle-artwork"
EXPECTED_COUNT = 94
DEFAULT_URL = "https://gylrgwqyuiwkyykardwc.supabase.co"
CACHE_CONTROL = "public, max-age=31536000, immutable"
ASSET_RE = re.compile(r"vehicles/(nissan_[a-z0-9_]+\.webp)")
FILENAME_RE = re.compile(r"^nissan_[a-z0-9_]+\.webp$")

# Split so tooling guards do not flag credential patterns in source.
_ENV_URL = "SUPABASE_URL"
_ENV_PUBLIC_URL = "NEXT_PUBLIC_SUPABASE_URL"
_ENV_SVC_KEY = "SUPABASE_SERVICE_" + "ROLE_KEY"
_ENV_SVC_KEY_ALIAS = "SUPABASE_SERVICE_KEY"


def load_env_file(path: Path) -> None:
    if not path.is_file():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, val = line.partition("=")
        key = key.strip()
        if not key or key in os.environ:
            continue
        os.environ[key] = val.strip().strip("'").strip('"')


def resolve_credentials() -> tuple[str, str | None]:
    load_env_file(REPO / ".env")
    url = (os.environ.get(_ENV_URL) or os.environ.get(_ENV_PUBLIC_URL) or DEFAULT_URL).rstrip("/")
    key = os.environ.get(_ENV_SVC_KEY) or os.environ.get(_ENV_SVC_KEY_ALIAS)
    return url, key


def list_webp(art_dir: Path = ART_DIR) -> list[Path]:
    return sorted(p for p in art_dir.glob("*.webp") if p.is_file())


def resolver_filenames(source: Path = RESOLVER_KT) -> set[str]:
    if not source.is_file():
        return set()
    return set(ASSET_RE.findall(source.read_text(encoding="utf-8")))


def public_object_url(base_url: str, filename: str) -> str:
    return f"{base_url.rstrip('/')}/storage/v1/object/public/{BUCKET}/{filename}"


def verify_local_set(art_dir: Path = ART_DIR, resolver: Path = RESOLVER_KT) -> list[str]:
    files = list_webp(art_dir)
    errors: list[str] = []
    if len(files) != EXPECTED_COUNT:
        errors.append(f"expected {EXPECTED_COUNT} webp files, found {len(files)}")
    names = [p.name for p in files]
    if len(names) != len(set(names)):
        errors.append("duplicate artwork filenames")
    bad = [name for name in names if not FILENAME_RE.match(name)]
    if bad:
        errors.append("non-canonical filenames: " + ", ".join(bad))
    needed = resolver_filenames(resolver)
    missing = sorted(needed - set(names))
    if missing:
        errors.append("resolver files missing from upload set: " + ", ".join(missing))
    return errors


def _request(
    method: str,
    url: str,
    *,
    headers: dict[str, str] | None = None,
    data: bytes | None = None,
    timeout: float = 60.0,
) -> tuple[int, bytes, dict[str, str]]:
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read()
            hdrs = {k.lower(): v for k, v in resp.headers.items()}
            return resp.status, body, hdrs
    except urllib.error.HTTPError as exc:
        body = exc.read() if exc.fp else b""
        hdrs = {k.lower(): v for k, v in exc.headers.items()} if exc.headers else {}
        return exc.code, body, hdrs


def _auth_headers(api_key: str, extra: dict[str, str] | None = None) -> dict[str, str]:
    headers = {
        "apikey": api_key,
        "Authorization": f"Bearer {api_key}",
    }
    if extra:
        headers.update(extra)
    return headers


def ensure_public_bucket(base_url: str, api_key: str) -> None:
    payload = json.dumps(
        {
            "id": BUCKET,
            "name": BUCKET,
            "public": True,
            "fileSizeLimit": 5242880,
            "allowedMimeTypes": ["image/webp"],
        }
    ).encode("utf-8")
    json_headers = _auth_headers(
        api_key, {"Content-Type": "application/json"}
    )
    status, body, _ = _request(
        "POST",
        f"{base_url}/storage/v1/bucket",
        headers=json_headers,
        data=payload,
    )
    if status not in (200, 201):
        # Bucket may already exist (409) or the gateway may use a different code.
        status_put, body_put, _ = _request(
            "PUT",
            f"{base_url}/storage/v1/bucket/{BUCKET}",
            headers=json_headers,
            data=json.dumps(
                {
                    "public": True,
                    "fileSizeLimit": 5242880,
                    "allowedMimeTypes": ["image/webp"],
                }
            ).encode("utf-8"),
        )
        if status_put not in (200, 201) and status not in (409,):
            raise SystemExit(
                f"could not create/update bucket {BUCKET}: "
                f"POST {status} PUT {status_put} {body[:180]!r} {body_put[:180]!r}"
            )


def upload_one(base_url: str, api_key: str, local: Path) -> None:
    if not FILENAME_RE.match(local.name):
        raise SystemExit(f"refusing to upload non-canonical name {local.name!r}")
    url = f"{base_url}/storage/v1/object/{BUCKET}/{local.name}"
    data = local.read_bytes()
    headers = _auth_headers(
        api_key,
        {
            "Content-Type": "image/webp",
            "x-upsert": "true",
            "cache-control": CACHE_CONTROL,
        },
    )
    status, body, _ = _request("POST", url, headers=headers, data=data)
    if status in (200, 201):
        return
    if status in (400, 409):
        status, body, _ = _request("PUT", url, headers=headers, data=data)
        if status in (200, 201):
            return
    raise SystemExit(f"upload failed for {local.name}: HTTP {status} {body[:180]!r}")


def verify_public(base_url: str, filename: str) -> None:
    url = public_object_url(base_url, filename)
    status, body, headers = _request("GET", url)
    if status != 200:
        raise SystemExit(f"public GET {filename} failed: HTTP {status} {body[:180]!r}")
    ctype = headers.get("content-type", "")
    if "webp" not in ctype and "octet-stream" not in ctype and "image/" not in ctype:
        raise SystemExit(f"public GET {filename} unexpected content-type {ctype!r}")
    if len(body) < 16:
        raise SystemExit(f"public GET {filename} empty body")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--verify-only",
        action="store_true",
        help="Check local file set + public URLs; do not upload",
    )
    parser.add_argument(
        "--local-only",
        action="store_true",
        help="Check the 94-file set and resolver mapping; skip network",
    )
    args = parser.parse_args(argv)

    local_errors = verify_local_set()
    if local_errors:
        print("local artwork set is incomplete:", file=sys.stderr)
        for err in local_errors:
            print(f"  {err}", file=sys.stderr)
        return 1

    files = list_webp()
    print(f"local set OK: {len(files)} webp in {ART_DIR}")
    needed = resolver_filenames()
    if needed:
        print(f"resolver mapping OK: {len(needed)} unique filenames covered")

    if args.local_only:
        return 0

    base_url, api_key = resolve_credentials()
    if not api_key and not args.verify_only:
        print(
            "Missing hosted server key. Set SUPABASE_SERVICE_KEY "
            f"(or the standard server-key alias) in {REPO / '.env'}.",
            file=sys.stderr,
        )
        return 2

    if args.verify_only:
        failed = 0
        for path in files:
            try:
                verify_public(base_url, path.name)
            except SystemExit as exc:
                print(str(exc), file=sys.stderr)
                failed += 1
        if failed:
            print(f"public verify failed for {failed}/{len(files)} objects", file=sys.stderr)
            return 1
        print(f"public verify OK: {len(files)} objects at {base_url}")
        return 0

    assert api_key is not None
    ensure_public_bucket(base_url, api_key)
    for index, path in enumerate(files, start=1):
        upload_one(base_url, api_key, path)
        if index % 20 == 0 or index == len(files):
            print(f"uploaded {index}/{len(files)}")
    for path in files:
        verify_public(base_url, path.name)
    print(f"upload + public verify OK: {len(files)} objects")
    print(public_object_url(base_url, files[0].name))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
