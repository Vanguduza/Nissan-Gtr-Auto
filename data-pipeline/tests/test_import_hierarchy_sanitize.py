from __future__ import annotations

from data_pipeline.import_hierarchy_catalog import (
    sanitize_hierarchy_vendor_leakage,
    sanitize_legacy_bundle_for_import,
)
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


def test_sanitize_hierarchy_vendor_leakage_strips_megazip() -> None:
    bundle = {
        "catalog_makers": [{"slug": "toyota", "name": "Toyota", "source": "megazip"}],
        "catalog_variants": [
            {
                "maker_slug": "toyota",
                "model_slug": "camry-1",
                "slug": "acv40",
                "source_url": "https://www.megazip.net/parts/toyota/camry",
                "external_data_id": "9",
            }
        ],
        "catalog_diagrams": [
            {
                "storage_path": "megazip/toyota/a.png",
                "image_url": "https://storage.megazip.net/catalog/a.png",
                "source_url": "https://www.megazip.net/x",
            }
        ],
        "part_fitment": [{"diagram_path": "megazip/toyota/a.png", "oem_part_number": "1"}],
        "catalog_sections": [
            {"thumbnail_url": "https://storage.megazip.net/t.png", "source_url": None}
        ],
    }
    out = sanitize_hierarchy_vendor_leakage(bundle)
    assert out["catalog_makers"][0]["source"] == "epc"
    assert out["catalog_variants"][0]["source_url"] is None
    assert out["catalog_diagrams"][0]["storage_path"] == "epc/toyota/a.png"
    assert out["catalog_diagrams"][0]["image_url"] is None
    assert out["part_fitment"][0]["diagram_path"] == "epc/toyota/a.png"
    assert out["catalog_sections"][0]["thumbnail_url"] is None


def test_prepare_hierarchy_for_supabase_import_normalizes_ids() -> None:
    from data_pipeline.import_hierarchy_catalog import (
        import_schema_issues,
        prepare_hierarchy_for_supabase_import,
    )

    ready = prepare_hierarchy_for_supabase_import(
        {
            "catalog_makers": [{"slug": "toyota", "name": "Toyota", "source": "megazip"}],
            "catalog_variants": [
                {
                    "maker_slug": "toyota",
                    "model_slug": "camry-1",
                    "slug": "acv40",
                    "megazip_data_id": "9",
                    "source_url": "https://www.megazip.net/x",
                }
            ],
            "catalog_diagram_parts": [
                {
                    "itemslist_id": "1",
                    "megazip_item_id": "1",
                    "diagram_path": "megazip/t/a.png",
                }
            ],
        }
    )
    assert ready["catalog_makers"][0]["source"] == "epc"
    assert "megazip_data_id" not in ready["catalog_variants"][0]
    assert ready["catalog_variants"][0]["external_data_id"] == "9"
    assert "megazip_item_id" not in ready["catalog_diagram_parts"][0]
    assert ready["catalog_diagram_parts"][0]["external_item_id"] == "1"
    assert ready["catalog_diagram_parts"][0]["diagram_path"] == "epc/t/a.png"
    assert import_schema_issues(ready) == []
