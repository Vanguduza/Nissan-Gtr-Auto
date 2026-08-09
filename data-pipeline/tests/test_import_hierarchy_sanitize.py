from __future__ import annotations

from data_pipeline.import_hierarchy_catalog import sanitize_legacy_bundle_for_import
from data_pipeline.validate import validate_bundle


def test_sanitize_legacy_bundle_for_import_megazip_shapes() -> None:
    bundle = {
        "vehicle_master": [
            {
                "vin_prefix": None,
                "chassis_code": "T31",
                "engine_code": None,
                "production_year": None,
                "model_variant": "Nissan X-TRAIL",
                "catalog_model_slug": "x-trail-2064",
            }
        ],
        "pnc_categories": [
            {
                "pnc_code": "11001",
                "category_name": "ENGINE",
                "pcdb_part_type_id": 12,
                "pcdb_part_type_label": "Engine Assembly",
            }
        ],
        "part_fitment": [
            {
                "oem_part_number": "11001-01A01",
                "pnc_code": "11001",
                "chassis_code": "T31",
                "engine_code": None,
                "diagram_path": "epc/nissan/a.png",
                "bbox_x": 1,
                "bbox_y": 2,
                "bbox_width": 3,
                "bbox_height": 4,
            }
        ],
        "diagram_assets": [
            {
                "storage_path": "epc/nissan/a.png",
                "source_url": "https://cdn.example/a.png",
                "mime_type": "image/png",
            }
        ],
    }
    sanitized = sanitize_legacy_bundle_for_import(bundle)
    assert sanitized["vehicle_master"] == [
        {"chassis_code": "T31", "model_variant": "Nissan X-TRAIL"}
    ]
    assert "pcdb_part_type_label" not in sanitized["pnc_categories"][0]
    assert "engine_code" not in sanitized["part_fitment"][0]
    assert sanitized["diagram_assets"][0]["content_type"] == "image/png"
    assert sanitized["diagram_assets"][0]["pnc_code"] == "11001"
    validate_bundle(
        {
            k: sanitized[k]
            for k in ("vehicle_master", "pnc_categories", "part_fitment", "diagram_assets")
        }
    )
