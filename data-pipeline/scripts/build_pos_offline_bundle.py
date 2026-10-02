"""Build the POS offline catalogue bundle for the current Nissan release and publish it to R2.

The bundle is what a till (tablet or web POS) downloads to browse and search the complete catalogue
with no connection: the vehicle master, every vehicle's search shard, every diagram's part list and
every diagram image, exactly as `catalog-live-r2` serves them. Prices and stock are not in it; tills
pull those live (`pull_pos_offline_snapshot`) when they download or sync.

Layout under ``bundles/<maker>/<release>/pos-offline/``::

    manifest.json                  # written last: the published build (format gtr-pos-offline/1)
    <build>/pack-0000.bin ...      # stored R2 objects back to back, unchanged (gzip NDJSON / PNG)
    <build>/vehicles.json.gz       # vehicle-master rows + each vehicle's shard pages [pack, offset, length]
    <build>/routes-00.json.gz ...  # diagram id -> part-list pages (+ diagram_id filter flag) and image

Diagram routes are split into ``ROUTE_BUCKETS`` files by ``fnv1a32(diagram_id) % ROUTE_BUCKETS`` so a
till loads only the bucket it needs. Packs are filled data-first (vehicles, part lists, then images).

The build is resumable: finished packs are recorded in ``--work/state.json`` and skipped on a rerun.

Database access is either PostgREST (SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY) or the Supabase
Management API SQL endpoint (SUPABASE_ACCESS_TOKEN + SUPABASE_PROJECT_REF). R2 credentials as
CLOUDFLARE_ACCOUNT_ID / CLOUDFLARE_R2_ACCESS_KEY_ID / CLOUDFLARE_R2_SECRET_ACCESS_KEY (bucket from the
release row). Never commit credentials; pass them through the environment only.

Usage::

    python data-pipeline/scripts/build_pos_offline_bundle.py --work /tmp/pos-bundle [--limit-images N]
"""

from __future__ import annotations

import argparse
import datetime as dt
import gzip
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.parse
import threading
import urllib.request
from concurrent.futures import ThreadPoolExecutor

try:  # Pooled keep-alive connections when available; plain urllib otherwise.
    import requests
except ImportError:  # pragma: no cover - optional dependency
    requests = None
from pathlib import Path

FORMAT = "gtr-pos-offline/1"
ROUTE_BUCKETS = 64
PACK_TARGET_BYTES = 128 * 1024 * 1024
PAGE = 1000


def fnv1a32(text: str) -> int:
    """32-bit FNV-1a over UTF-8 bytes. Clients use the same function to pick a routes file."""
    h = 0x811C9DC5
    for b in text.encode("utf-8"):
        h ^= b
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h


# ---------------------------------------------------------------------------------------------
# Supabase (PostgREST, service role)
# ---------------------------------------------------------------------------------------------

class Supabase:
    def __init__(self, url: str, key: str):
        self.url = url.rstrip("/")
        self.headers = {"apikey": key, "Authorization": f"Bearer {key}", "Content-Type": "application/json"}

    def _open(self, req: urllib.request.Request):
        for attempt in range(5):
            try:
                with urllib.request.urlopen(req, timeout=120) as r:
                    return json.loads(r.read())
            except Exception:
                if attempt == 4:
                    raise
                time.sleep(2 + attempt * 3)

    def select(self, table: str, query: str):
        req = urllib.request.Request(f"{self.url}/rest/v1/{table}?{query}", headers=self.headers)
        return self._open(req)

    def rpc(self, name: str, args: dict):
        req = urllib.request.Request(
            f"{self.url}/rest/v1/rpc/{name}", data=json.dumps(args).encode(), headers=self.headers, method="POST"
        )
        return self._open(req)


class ManagementSql:
    """Read-only SQL through the Supabase Management API (an operator's personal access token)."""

    def __init__(self, token: str, project_ref: str):
        self.url = f"https://api.supabase.com/v1/projects/{project_ref}/database/query"
        self.headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    def query(self, sql: str) -> list:
        req = urllib.request.Request(self.url, data=json.dumps({"query": sql}).encode(), headers=self.headers, method="POST")
        for attempt in range(5):
            try:
                with urllib.request.urlopen(req, timeout=600) as r:
                    return json.loads(r.read())
            except Exception:
                if attempt == 4:
                    raise
                time.sleep(3 + attempt * 5)
        raise AssertionError("unreachable")


def _lit(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


class Catalog:
    """The three reads the build needs, over either database client."""

    def __init__(self, rest: Supabase | None, sql: ManagementSql | None):
        self.rest, self.sql = rest, sql

    def release(self, maker: str) -> dict | None:
        if self.sql:
            rows = self.sql.query(f"select id, version, bucket_name from catalog_releases where maker_slug = {_lit(maker)} and is_current")
        else:
            rows = self.rest.select("catalog_releases", f"select=id,version,bucket_name&maker_slug=eq.{maker}&is_current=eq.true")
        return rows[0] if rows else None

    def vehicles(self, maker: str) -> list[dict]:
        out: list[dict] = []
        while True:
            if self.sql:
                page = self.sql.query(f"select * from list_customer_vehicle_master({_lit(maker)}, {PAGE}, {len(out)})")
            else:
                page = self.rest.rpc("list_customer_vehicle_master", {"p_maker": maker, "p_limit": PAGE, "p_offset": len(out)})
            out.extend(page)
            if len(page) < PAGE:
                return out

    def objects(self, release_id: str, maker: str, kind: str) -> list[dict]:
        if not self.sql:
            base = f"select=object_kind,scope_key,object_key,metadata&release_id=eq.{release_id}&maker_slug=eq.{maker}"
            return paged(self.rest, "catalog_r2_serving_objects", f"{base}&object_kind=eq.{kind}&order=scope_key")
        out: list[dict] = []
        step = 50_000
        while True:
            page = self.sql.query(
                "select object_kind, scope_key, object_key, metadata from catalog_r2_serving_objects "
                f"where release_id = {_lit(release_id)} and maker_slug = {_lit(maker)} and object_kind = {_lit(kind)} "
                f"order by scope_key limit {step} offset {len(out)}"
            )
            out.extend(page)
            if len(page) < step:
                return out


# ---------------------------------------------------------------------------------------------
# R2 (S3 SigV4)
# ---------------------------------------------------------------------------------------------

class R2:
    def __init__(self, account: str, key_id: str, secret: str, bucket: str):
        self.host = f"{account}.r2.cloudflarestorage.com"
        self.key_id, self.secret, self.bucket = key_id, secret, bucket

    def _sign(self, method: str, path: str, headers: dict, payload_hash: str) -> dict:
        t = dt.datetime.now(dt.timezone.utc)
        amz, day = t.strftime("%Y%m%dT%H%M%SZ"), t.strftime("%Y%m%d")
        hdrs = {**{k.lower(): v for k, v in headers.items()}, "host": self.host, "x-amz-content-sha256": payload_hash, "x-amz-date": amz}
        signed = ";".join(sorted(hdrs))
        canon = f"{method}\n{path}\n\n" + "".join(f"{k}:{hdrs[k]}\n" for k in sorted(hdrs)) + f"\n{signed}\n{payload_hash}"
        scope = f"{day}/auto/s3/aws4_request"
        sts = f"AWS4-HMAC-SHA256\n{amz}\n{scope}\n{hashlib.sha256(canon.encode()).hexdigest()}"
        k = ("AWS4" + self.secret).encode()
        for part in (day, "auto", "s3", "aws4_request"):
            k = hmac.new(k, part.encode(), hashlib.sha256).digest()
        sig = hmac.new(k, sts.encode(), hashlib.sha256).hexdigest()
        out = {k2: v for k2, v in hdrs.items() if k2 != "host"}
        out["Authorization"] = f"AWS4-HMAC-SHA256 Credential={self.key_id}/{scope}, SignedHeaders={signed}, Signature={sig}"
        return out

    def _path(self, key: str) -> str:
        return "/" + self.bucket + "/" + urllib.parse.quote(key)

    _local = threading.local()

    def _session(self):
        if requests is None:
            return None
        session = getattr(self._local, "session", None)
        if session is None:
            session = self._local.session = requests.Session()
        return session

    def get(self, key: str) -> bytes:
        """Raw stored bytes (no decompression): the bundle keeps objects exactly as served."""
        path = self._path(key)
        for attempt in range(6):
            try:
                headers = self._sign("GET", path, {}, hashlib.sha256(b"").hexdigest())
                session = self._session()
                if session is not None:
                    r = session.get(f"https://{self.host}{path}", headers=headers, timeout=120)
                    r.raise_for_status()
                    return r.content
                req = urllib.request.Request(f"https://{self.host}{path}", headers=headers)
                with urllib.request.urlopen(req, timeout=120) as r:
                    return r.read()
            except Exception:
                if attempt == 5:
                    raise
                time.sleep(1 + attempt * 2)
        raise AssertionError("unreachable")

    def put(self, key: str, body: bytes, content_type: str) -> None:
        path = self._path(key)
        digest = hashlib.sha256(body).hexdigest()
        for attempt in range(6):
            try:
                hdrs = self._sign("PUT", path, {"content-type": content_type}, digest)
                req = urllib.request.Request(f"https://{self.host}{path}", data=body, headers=hdrs, method="PUT")
                urllib.request.urlopen(req, timeout=600).read()
                return
            except Exception:
                if attempt == 5:
                    raise
                time.sleep(2 + attempt * 3)


# ---------------------------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------------------------

def paged(sb: Supabase, table: str, query: str):
    rows = []
    while True:
        page = sb.select(table, f"{query}&limit={PAGE}&offset={len(rows)}")
        rows.extend(page)
        if len(page) < PAGE:
            return rows


def pages_of(obj: dict) -> list[str]:
    meta = obj.get("metadata") or {}
    pages = meta.get("pages")
    return [str(p) for p in pages] if isinstance(pages, list) and pages else [obj["object_key"]]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--work", required=True, help="Local working directory (resumable state + one pack at a time)")
    ap.add_argument("--maker", default="nissan")
    ap.add_argument("--threads", type=int, default=48)
    ap.add_argument("--limit-images", type=int, default=None, help="Testing only: cap the number of images")
    ap.add_argument("--pack-mb", type=int, default=PACK_TARGET_BYTES // (1024 * 1024),
                    help="Pack size; smaller packs save progress more often (a rerun resumes after the last full pack)")
    args = ap.parse_args()

    env = os.environ
    if env.get("SUPABASE_ACCESS_TOKEN") and env.get("SUPABASE_PROJECT_REF"):
        db = Catalog(None, ManagementSql(env["SUPABASE_ACCESS_TOKEN"], env["SUPABASE_PROJECT_REF"]))
    else:
        db = Catalog(Supabase(env["SUPABASE_URL"], env["SUPABASE_SERVICE_ROLE_KEY"]), None)

    def make_r2(release: dict) -> R2:
        return R2(
            env.get("CLOUDFLARE_ACCOUNT_ID") or env["R2_ACCOUNT_ID"],
            env.get("CLOUDFLARE_R2_ACCESS_KEY_ID") or env["R2_ACCESS_KEY_ID"],
            env.get("CLOUDFLARE_R2_SECRET_ACCESS_KEY") or env["R2_SECRET_ACCESS_KEY"],
            release["bucket_name"] or env.get("CLOUDFLARE_R2_BUCKET", ""),
        )

    return build(db, make_r2, Path(args.work), args.maker, args.threads, args.limit_images, args.pack_mb * 1024 * 1024)


def build(db, make_r2, work: Path, maker: str = "nissan", threads: int = 48, limit_images: int | None = None,
          pack_target_bytes: int = PACK_TARGET_BYTES) -> int:
    """Builds and publishes the bundle. [db] is a `Catalog`; [make_r2] gives an object with
    get(key) -> bytes and put(key, body, content_type) for the release's bucket."""
    release = db.release(maker)
    if not release:
        print("no current release", file=sys.stderr)
        return 1
    r2 = make_r2(release)
    work.mkdir(parents=True, exist_ok=True)
    state_path = work / "state.json"
    state = json.loads(state_path.read_text()) if state_path.exists() else {}
    if state.get("release") != release["version"]:
        state = {"release": release["version"], "build": dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ"), "packs": []}
    prefix = f"bundles/{maker}/{release['version']}/pos-offline"
    build_prefix = f"{prefix}/{state['build']}"

    # 1. What the bundle holds, from the routing manifest of the current release.
    vehicles = db.vehicles(maker)
    print(f"vehicles: {len(vehicles)}", flush=True)
    objects = {kind: db.objects(release["id"], maker, kind) for kind in ("vehicle_search", "diagram_parts", "diagram_image")}
    for kind, rows in objects.items():
        print(f"{kind}: {len(rows)} routes", flush=True)
    vehicle_ids = {v["id"] for v in vehicles}
    vehicle_objects = [o for o in objects["vehicle_search"] if o["scope_key"] in vehicle_ids]
    image_objects = objects["diagram_image"]
    if limit_images is not None:
        image_objects = image_objects[: limit_images]

    ordered: list[str] = []
    seen: set[str] = set()
    for group in (vehicle_objects, objects["diagram_parts"], image_objects):
        for obj in group:
            for key in pages_of(obj):
                if key not in seen:
                    seen.add(key)
                    ordered.append(key)
    print(f"distinct objects: {len(ordered)}", flush=True)

    # 2. Packs: stored objects back to back, each pack uploaded then removed locally.
    located: dict[str, list[int]] = {}
    for pack in state["packs"]:
        for key, off, length in pack["entries"]:
            located[key] = [pack["index"], off, length]
    todo = [k for k in ordered if k not in located]
    print(f"to pack: {len(todo)} (already packed: {len(located)})", flush=True)

    def flush(index: int, data: bytearray, entries: list) -> None:
        body = bytes(data)
        name = f"pack-{index:04d}.bin"
        r2.put(f"{build_prefix}/{name}", body, "application/octet-stream")
        state["packs"].append({"index": index, "name": name, "bytes": len(body), "sha256": hashlib.sha256(body).hexdigest(), "entries": entries})
        state_path.write_text(json.dumps(state))
        for key, off, length in entries:
            located[key] = [index, off, length]
        print(f"uploaded {name}: {len(body) / 1e6:.1f} MB, {len(entries)} objects", flush=True)

    index = len(state["packs"])
    data, entries = bytearray(), []
    t0 = time.time()
    done = 0
    with ThreadPoolExecutor(threads) as ex:
        for start in range(0, len(todo), 4000):
            chunk = todo[start : start + 4000]
            for key, body in zip(chunk, ex.map(r2.get, chunk)):
                if data and len(data) + len(body) > pack_target_bytes:
                    flush(index, data, entries)
                    index, data, entries = index + 1, bytearray(), []
                entries.append([key, len(data), len(body)])
                data += body
            done += len(chunk)
            print(f"  fetched {done}/{len(todo)} in {int(time.time() - t0)}s", flush=True)
    if entries:
        flush(index, data, entries)

    # 3. Routes, written as small gzip JSON files.
    files: list[dict] = [{"path": p["name"], "kind": "pack", "bytes": p["bytes"], "sha256": p["sha256"]} for p in state["packs"]]

    def publish_json(name: str, kind: str, payload) -> None:
        body = gzip.compress(json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode(), 9)
        r2.put(f"{build_prefix}/{name}", body, "application/gzip")
        files.append({"path": name, "kind": kind, "bytes": len(body), "sha256": hashlib.sha256(body).hexdigest()})

    shards = {o["scope_key"]: [located[k] for k in pages_of(o)] for o in vehicle_objects}
    publish_json("vehicles.json.gz", "vehicles", {"vehicles": vehicles, "shards": shards})

    buckets: list[dict] = [{} for _ in range(ROUTE_BUCKETS)]
    for o in objects["diagram_parts"]:
        route = buckets[fnv1a32(o["scope_key"]) % ROUTE_BUCKETS].setdefault(o["scope_key"], {})
        route["p"] = [located[k] for k in pages_of(o)]
        if (o.get("metadata") or {}).get("filter") == "diagram_id":
            route["f"] = 1
    images = 0
    for o in image_objects:
        if o["object_key"] in located:
            buckets[fnv1a32(o["scope_key"]) % ROUTE_BUCKETS].setdefault(o["scope_key"], {})["i"] = located[o["object_key"]]
            images += 1
    for i, bucket in enumerate(buckets):
        publish_json(f"routes-{i:02d}.json.gz", "routes", bucket)

    # 4. Manifest last: publishing it is what makes this build the one tills download.
    manifest = {
        "format": FORMAT,
        "maker": maker,
        "release": release["version"],
        "build": state["build"],
        "built_at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "route_buckets": ROUTE_BUCKETS,
        "counts": {
            "vehicles": len(vehicles),
            "vehicle_shards": len(shards),
            "diagrams": len(objects["diagram_parts"]),
            "diagram_images": images,
        },
        "total_bytes": sum(f["bytes"] for f in files),
        "files": files,
    }
    r2.put(f"{prefix}/manifest.json", json.dumps(manifest, indent=1).encode(), "application/json")
    print(f"published {prefix}/manifest.json: {manifest['total_bytes'] / 1e9:.2f} GB in {len(files)} files", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
