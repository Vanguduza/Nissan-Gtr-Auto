from __future__ import annotations

import json
from pathlib import Path

from data_pipeline.transform_amayama import (
    extract_bbox,
    is_catalog_payload,
    normalize_oem,
    normalize_pnc,
    transform_payload,
    transform_raw_records,
    vehicle_context_from_url,
)
from data_pipeline.validate import validate_bundle

FIXTURES = Path(__file__).resolve().parent / "fixtures" / "amayama_raw"


def test_normalize_oem() -> None:
    assert normalize_oem("15208-65F0C") == "15208-65F0C"
    assert normalize_oem("1520865F0C") == "15208-65F0C"
    assert normalize_oem("15208 65F0C") == "15208-65F0C"
    assert normalize_oem("bad") is None


def test_normalize_pnc_from_oem() -> None:
    assert normalize_pnc(None, oem="15208-65F0C") == "15208"
    assert normalize_pnc("15208") == "15208"
    assert normalize_pnc("PNC-15208") == "15208"


def test_normalize_pnc_partsouq_alphanumeric() -> None:
    assert normalize_pnc("C8320") == "C8320"
    assert normalize_pnc("c8320") == "C8320"
    assert normalize_pnc("16132PA") == "16132PA"
    assert normalize_pnc("16132pa") == "16132PA"
    assert normalize_pnc(None, oem="C8320-12A3B") == "C8320"
    assert normalize_pnc("bad!") is None
    assert normalize_pnc("12") is None


def test_vehicle_context_from_url() -> None:
    ctx = vehicle_context_from_url(
        "https://www.amayama.com/en/catalogs/nissan/navara/d40/yd25/engine"
    )
    assert ctx["model_variant"] == "Navara"
    assert ctx["chassis_code"] == "D40"
    assert ctx["engine_code"] == "YD25"


def test_extract_bbox_percent() -> None:
    bbox = extract_bbox({"left": 10, "top": 20, "width": 15, "height": 25})
    assert bbox == {
        "bbox_x": 0.1,
        "bbox_y": 0.2,
        "bbox_width": 0.15,
        "bbox_height": 0.25,
    }


def test_extract_bbox_pixels_with_image_size() -> None:
    bbox = extract_bbox(
        {
            "x": 100,
            "y": 50,
            "width": 200,
            "height": 100,
            "image_width": 1000,
            "image_height": 500,
        }
    )
    assert bbox == {
        "bbox_x": 0.1,
        "bbox_y": 0.1,
        "bbox_width": 0.2,
        "bbox_height": 0.2,
    }


def test_transform_sample_payload_validates() -> None:
    payload = json.loads((FIXTURES / "diagram_payload.json").read_text(encoding="utf-8"))
    source = (
        "https://www.amayama.com/en/catalogs/nissan/navara/d40/yd25/engine/diagram-1"
    )
    assert is_catalog_payload(payload)
    partial = transform_payload(
        payload,
        source_url=source,
        vehicle_hints={"year_start": 2010, "year_end": 2011},
    )
    assert len(partial["part_fitment"]) >= 2
    assert partial["diagram_assets"][0]["provenance"] == "scraped-reference"
    vehicles = partial["vehicle_master"]
    assert vehicles
    assert all(v.get("vin_prefix") for v in vehicles)
    assert {v["production_year"] for v in vehicles} >= {2010, 2011}
    validate_bundle(partial)


def test_transform_raw_records_merges() -> None:
    payload = json.loads((FIXTURES / "diagram_payload.json").read_text(encoding="utf-8"))
    records = [
        (
            "https://www.amayama.com/en/catalogs/nissan/navara/d40/yd25/engine/a",
            payload,
        ),
        (
            "https://www.amayama.com/en/catalogs/nissan/navara/d40/yd25/engine/b",
            payload,
        ),
    ]
    bundle = transform_raw_records(records)
    # Deduped by natural keys
    oems = {r["oem_part_number"] for r in bundle["part_fitment"]}
    assert "15208-65F0C" in oems
    assert "40206-EA00A" in oems
    assert len(bundle["vehicle_master"]) == 1
