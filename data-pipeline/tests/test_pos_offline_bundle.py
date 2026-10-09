"""POS offline bundle builder over in-memory fakes of the routing tables and R2.

Also writes the sample bundle the tablet and web readers are tested against when run with
``POS_OFFLINE_FIXTURE_OUT=<dir>`` (see ``write_fixture``).
"""

from __future__ import annotations

import gzip
import hashlib
import importlib.util
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("build_pos_offline_bundle", ROOT / "scripts" / "build_pos_offline_bundle.py")
bundle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bundle)

PNG = bytes.fromhex("89504e470d0a1a0a0000000d4948445200000001000000010806000000")


def ndjson_gz(rows: list[dict]) -> bytes:
    return gzip.compress("\n".join(json.dumps(r) for r in rows).encode(), mtime=0)


def part(oem: str, name: str, section: str, diagram: str, **extra) -> dict:
    return {
        "normalized_oem_number": oem.replace("-", ""),
        "display_oem_number": oem,
        "name": name,
        "description": name,
        "category_name": extra.pop("category", name),
        "subcategory_name": extra.pop("subcategory", "Brakes"),
        "section_id": section,
        "section_slug": section.lower(),
        "diagram_id": diagram,
        "search_text": f"{name} {oem}".lower(),
        **extra,
    }


class FakeR2:
    def __init__(self, objects: dict[str, bytes]):
        self.objects = dict(objects)

    def get(self, key: str) -> bytes:
        return self.objects[key]

    def put(self, key: str, body: bytes, content_type: str) -> None:
        self.objects[key] = bytes(body)


class FakeCatalog:
    def __init__(self, vehicles, objects):
        self._vehicles, self._objects = vehicles, objects

    def release(self, maker):
        return {"id": "rel-1", "version": "test-release", "bucket_name": "test"}

    def vehicles(self, maker):
        return self._vehicles

    def objects(self, release_id, maker, kind):
        return [o for o in self._objects if o["object_kind"] == kind]


def sample():
    vehicles = [
        {"id": "VM-gtr-1", "model_family": "GT-R", "chassis_code": "R35", "engine_code": "VR38DETT", "year_start": 2007, "year_end": 2022, "sales_region": "JP"},
        {"id": "VM-nav-1", "model_family": "Navara", "chassis_code": "D23", "engine_code": "YD25DDTI", "year_start": 2015, "year_end": None, "sales_region": None},
    ]
    gtr_page1 = [
        part("41060-JF00A", "Front brake pad set", "SEC-BRAKE", "DG-pads"),
        part("41060-JF00A", "Front brake pad set", "SEC-BRAKE", "DG-pads"),
        part("40206-JF00B", "Front brake rotor", "SEC-BRAKE", "DG-rotor"),
    ]
    gtr_page2 = [part("15208-65F0E", "Oil filter", "SEC-ENGINE", "DG-oil", subcategory="Engine", category="Lubrication")]
    nav = [part("41060-4JA0A", "Front brake pad set", "SEC-BRAKE", "DG-nav-pads")]
    diagram_pads = [
        {**part("41060-JF00A", "Front brake pad set", "SEC-BRAKE", "DG-pads"), "pnc_code": "41060", "callout_ref": "1",
         "bbox_x": 0.1, "bbox_y": 0.2, "bbox_width": 0.05, "bbox_height": 0.04},
    ]
    shared_section = [
        part("40206-JF00B", "Front brake rotor", "SEC-BRAKE", "DG-rotor"),
        part("15208-65F0E", "Oil filter", "SEC-ENGINE", "DG-oil"),
    ]
    store = {
        "v/gtr-1.gz": ndjson_gz(gtr_page1),
        "v/gtr-2.gz": ndjson_gz(gtr_page2),
        "v/nav.gz": ndjson_gz(nav),
        "dp/pads.gz": ndjson_gz(diagram_pads),
        "dp/shared.gz": ndjson_gz(shared_section),
        "dp/nav.gz": ndjson_gz(nav),
        "img/pads.png": PNG,
        "img/rotor.png": PNG + b"rotor",
    }
    objects = [
        {"object_kind": "vehicle_search", "scope_key": "VM-gtr-1", "object_key": "v/gtr-1.gz", "metadata": {"pages": ["v/gtr-1.gz", "v/gtr-2.gz"]}},
        {"object_kind": "vehicle_search", "scope_key": "VM-nav-1", "object_key": "v/nav.gz", "metadata": {}},
        {"object_kind": "vehicle_search", "scope_key": "VM-not-in-master", "object_key": "v/nav.gz", "metadata": {}},
        {"object_kind": "diagram_parts", "scope_key": "DG-pads", "object_key": "dp/pads.gz", "metadata": {"callout_boxes": True}},
        {"object_kind": "diagram_parts", "scope_key": "DG-rotor", "object_key": "dp/shared.gz", "metadata": {"pages": ["dp/shared.gz"], "filter": "diagram_id"}},
        {"object_kind": "diagram_parts", "scope_key": "DG-oil", "object_key": "dp/shared.gz", "metadata": {"pages": ["dp/shared.gz"], "filter": "diagram_id"}},
        {"object_kind": "diagram_parts", "scope_key": "DG-nav-pads", "object_key": "dp/nav.gz", "metadata": {}},
        {"object_kind": "diagram_image", "scope_key": "DG-pads", "object_key": "img/pads.png", "metadata": {}},
        {"object_kind": "diagram_image", "scope_key": "DG-rotor", "object_key": "img/rotor.png", "metadata": {}},
        {"object_kind": "diagram_image", "scope_key": "DG-nav-pads", "object_key": "img/pads.png", "metadata": {}},
    ]
    return vehicles, objects, store


def run_build(tmp_path: Path) -> FakeR2:
    vehicles, objects, store = sample()
    r2 = FakeR2(store)
    code = bundle.build(FakeCatalog(vehicles, objects), lambda _release: r2, tmp_path / "work", threads=2, pack_target_bytes=300)
    assert code == 0
    return r2


def published(r2: FakeR2) -> tuple[dict, dict[str, bytes]]:
    prefix = "bundles/nissan/test-release/pos-offline"
    manifest = json.loads(r2.objects[f"{prefix}/manifest.json"])
    files = {f["path"]: r2.objects[f"{prefix}/{manifest['build']}/{f['path']}"] for f in manifest["files"]}
    return manifest, files


def test_fnv1a32_matches_reference_values():
    assert bundle.fnv1a32("") == 0x811C9DC5
    assert bundle.fnv1a32("a") == 0xE40C292C
    assert bundle.fnv1a32("foobar") == 0xBF9CF968


def test_build_publishes_manifest_packs_and_routes(tmp_path):
    manifest, files = published(run_build(tmp_path))
    assert manifest["format"] == "gtr-pos-offline/1"
    assert manifest["counts"] == {"vehicles": 2, "vehicle_shards": 2, "diagrams": 4, "diagram_images": 3}
    assert sum(1 for f in manifest["files"] if f["kind"] == "pack") > 1, "small pack target splits packs"
    for f in manifest["files"]:
        assert hashlib.sha256(files[f["path"]]).hexdigest() == f["sha256"]
        assert len(files[f["path"]]) == f["bytes"]
    assert manifest["total_bytes"] == sum(f["bytes"] for f in manifest["files"])

    def read(loc):
        pack = files[f"pack-{loc[0]:04d}.bin"]
        return pack[loc[1] : loc[1] + loc[2]]

    vehicles = json.loads(gzip.decompress(files["vehicles.json.gz"]))
    assert [v["id"] for v in vehicles["vehicles"]] == ["VM-gtr-1", "VM-nav-1"]
    assert set(vehicles["shards"]) == {"VM-gtr-1", "VM-nav-1"}, "only vehicles in the master"
    gtr_pages = vehicles["shards"]["VM-gtr-1"]
    assert len(gtr_pages) == 2
    assert b"Oil filter" in gzip.decompress(read(gtr_pages[1]))

    def route(diagram_id):
        name = f"routes-{bundle.fnv1a32(diagram_id) % manifest['route_buckets']:02d}.json.gz"
        return json.loads(gzip.decompress(files[name]))[diagram_id]

    rotor = route("DG-rotor")
    assert rotor["f"] == 1 and len(rotor["p"]) == 1 and read(rotor["i"]).endswith(b"rotor")
    assert "f" not in route("DG-pads") and read(route("DG-pads")["i"]) == PNG
    assert "i" not in route("DG-oil")
    # One stored copy of an object shared by several routes.
    assert route("DG-pads")["i"] == route("DG-nav-pads")["i"]
    assert route("DG-rotor")["p"] == route("DG-oil")["p"]


def test_rerun_resumes_without_refetching(tmp_path):
    vehicles, objects, store = sample()
    r2 = FakeR2(store)
    catalog = FakeCatalog(vehicles, objects)
    assert bundle.build(catalog, lambda _r: r2, tmp_path / "work", threads=2, pack_target_bytes=300) == 0
    fetched = []
    original = r2.get
    r2.get = lambda key: fetched.append(key) or original(key)
    assert bundle.build(catalog, lambda _r: r2, tmp_path / "work", threads=2, pack_target_bytes=300) == 0
    assert fetched == []


def test_write_fixture(tmp_path):
    """Writes the published sample bundle (manifest + files) for the reader tests on request."""
    out = os.environ.get("POS_OFFLINE_FIXTURE_OUT")
    manifest, files = published(run_build(tmp_path))
    if not out:
        return
    target = Path(out)
    target.mkdir(parents=True, exist_ok=True)
    (target / "manifest.json").write_text(json.dumps(manifest, indent=1))
    for name, body in files.items():
        (target / name).write_bytes(body)
