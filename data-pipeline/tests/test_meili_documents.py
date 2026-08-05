"""Tests for Meilisearch document builder."""

from data_pipeline.meili_documents import build_catalog_documents


def test_build_catalog_documents_part_vehicle_pnc():
    docs = build_catalog_documents(
        vehicle_master=[
            {
                "vin_prefix": "JN1",
                "model_variant": "Qashqai",
                "chassis_code": "J11",
                "engine_code": "MR20",
                "production_year": 2018,
            }
        ],
        pnc_categories=[
            {
                "pnc_code": "15208",
                "category_name": "Engine",
                "subcategory_name": "Oil filter",
            }
        ],
        part_fitment=[
            {
                "oem_part_number": "15208-65F0C",
                "pnc_code": "15208",
                "chassis_code": "J11",
                "engine_code": "MR20",
                "superseded_by": None,
                "diagram_path": "qashqai/oil.png",
            }
        ],
        stock_items=[
            {
                "oem_part_number": "15208-65F0C",
                "description": "Oil filter",
            }
        ],
        oe_cross_refs=[
            {
                "oem_part_number": "15208-65F0C",
                "oe_number": "OF-123",
                "brand": "Aftermarket",
            }
        ],
    )

    kinds = {d["doc_kind"] for d in docs}
    assert kinds == {"part", "vehicle", "pnc"}

    part = next(d for d in docs if d["doc_kind"] == "part")
    assert part["oem_part_number"] == "15208-65F0C"
    assert part["category_name"] == "Engine"
    assert "OF-123" in part["oe_numbers"]

    vehicle = next(d for d in docs if d["doc_kind"] == "vehicle")
    assert vehicle["model_variant"] == "Qashqai"

    pnc = next(d for d in docs if d["doc_kind"] == "pnc")
    assert pnc["pnc_code"] == "15208"
    assert pnc["fitment_count"] == 1
