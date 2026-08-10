from __future__ import annotations

from data_pipeline.bundle_filter import complete_fitments, filter_complete_bundle


def _sample_bundle() -> dict:
    return {
        "vehicle_master": [
            {"chassis_code": "B13", "engine_code": "GA16", "model_variant": "Nissan Sunny B13"},
            {"chassis_code": "R33", "engine_code": "RB25", "model_variant": "Nissan Skyline R33"},
        ],
        "pnc_categories": [
            {"pnc_code": "11001A", "category_name": "ENGINE", "subcategory_name": "Block"},
            {"pnc_code": "99999Z", "category_name": "UNCATEGORIZED", "subcategory_name": None},
        ],
        "part_fitment": [
            {
                "oem_part_number": "11001-01A01",
                "pnc_code": "11001A",
                "chassis_code": "B13",
                "engine_code": "GA16",
                "bbox_x": 10,
                "diagram_path": "partsouq/nissan/b13/diag.gif",
            },
            {
                "oem_part_number": "99999-00000",
                "pnc_code": "99999Z",
                "chassis_code": "R33",
                "engine_code": "RB25",
                "bbox_x": None,
                "diagram_path": "partsouq/nissan/r33/incomplete.gif",
            },
        ],
        "diagram_assets": [
            {"storage_path": "partsouq/nissan/b13/diag.gif", "content_type": "image/gif"},
            {"storage_path": "partsouq/nissan/r33/incomplete.gif", "content_type": "image/gif"},
        ],
        "_oem_display_names": {
            "11001-01A01": "BOLT",
            "99999-00000": "INCOMPLETE PART",
        },
    }


def test_complete_fitments_requires_bbox_and_diagram() -> None:
    rows = complete_fitments(_sample_bundle())
    assert len(rows) == 1
    assert rows[0]["chassis_code"] == "B13"


def test_filter_complete_bundle_excludes_identity_only_vehicles() -> None:
    filtered, meta = filter_complete_bundle(_sample_bundle(), completed_only=True)
    assert meta["vehicles_in"] == 2
    assert meta["vehicles_out"] == 1
    assert meta["parts_complete_chassis"] == ["B13"]
    assert meta["excluded_identity_only_chassis"] == ["R33"]
    assert [v["chassis_code"] for v in filtered["vehicle_master"]] == ["B13"]
    assert len(filtered["part_fitment"]) == 1
    assert filtered["pnc_categories"] == [
        {"pnc_code": "11001A", "category_name": "ENGINE", "subcategory_name": "Block"}
    ]
    assert filtered["diagram_assets"] == [
        {"storage_path": "partsouq/nissan/b13/diag.gif", "content_type": "image/gif"}
    ]
    assert filtered["_oem_display_names"] == {"11001-01A01": "BOLT"}


def test_filter_keeps_all_vehicles_when_not_completed_only() -> None:
    filtered, meta = filter_complete_bundle(_sample_bundle(), completed_only=False)
    assert meta["vehicles_out"] == 2
    assert len(filtered["part_fitment"]) == 1


def test_sanitize_vehicle_rows_drops_nulls_and_extras() -> None:
    bundle = _sample_bundle()
    bundle["vehicle_master"] = [
        {
            "vin_prefix": None,
            "chassis_code": "B13",
            "engine_code": None,
            "production_year": None,
            "model_variant": "Nissan Sunny B13",
            "catalog_model_slug": "sunny-1",
            "catalog_variant_slug": "b13",
        }
    ]
    filtered, _meta = filter_complete_bundle(bundle, completed_only=True)
    assert filtered["vehicle_master"] == [
        {"chassis_code": "B13", "model_variant": "Nissan Sunny B13"}
    ]
