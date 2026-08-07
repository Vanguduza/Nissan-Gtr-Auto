"""Tests for EPC category resolution helpers."""

from __future__ import annotations

from data_pipeline.amayama_catalog_auto import (
    _content_type_for_diagram,
    _content_type_from_url,
)
from data_pipeline.parse_partsouq_html import (
    assembly_hints_from_diagram_title,
    category_hints_from_url,
    is_generic_part_name,
    subcategory_from_diagram_title,
)


def test_content_type_gif_diagrams() -> None:
    assert _content_type_from_url("https://partsouq.com/foo/demo.gif") == "image/gif"
    assert (
        _content_type_for_diagram(
            storage_path="partsouq/nissan/jj10/abc.gif",
            declared="image/png",
        )
        == "image/gif"
    )


def test_category_from_cname_query() -> None:
    hints = category_hints_from_url(
        "https://partsouq.com/en/catalog/genuine/parts?c=Nissan&cid=6&cname=POWER+TRAIN"
    )
    assert hints["category_name"] == "POWER TRAIN"


def test_generic_part_names_not_categories() -> None:
    assert is_generic_part_name("BOLT")
    assert is_generic_part_name("NUT")
    assert not is_generic_part_name("POWER TRAIN")


def test_subcategory_from_diagram_alt() -> None:
    assert (
        subcategory_from_diagram_title("NISSAN 200SX 07.1994 THROTTLE CHAMBER")
        is None
    )
    hints = assembly_hints_from_diagram_title(
        "QASHQAI+2 PISTON,CRANKSHAFT & FLYWHEEL; ILLUSTRATION"
    )
    assert hints["category_name"] == "PISTON,CRANKSHAFT & FLYWHEEL"
    assert hints["subcategory_name"] == "ILLUSTRATION"


def test_strip_vehicle_model_from_diagram_title() -> None:
    from data_pipeline.parse_partsouq_html import (
        normalize_epc_category_name,
        strip_vehicle_model_prefix,
    )

    assert strip_vehicle_model_prefix("MICRA MANIFOLD") == "MANIFOLD"
    assert (
        strip_vehicle_model_prefix("SUNNY/NX COUPE AIR CLEANER") == "AIR CLEANER"
    )
    assert normalize_epc_category_name("SUNNY/NX COUPE ENGINE ASSEMBLY") == (
        "ENGINE ASSEMBLY"
    )
    assert normalize_epc_category_name("BRAKE PIPING & CONTROL") == (
        "BRAKE PIPING & CONTROL"
    )
