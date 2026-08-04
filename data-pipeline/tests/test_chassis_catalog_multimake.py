"""Tests for multi-make chassis → VIN enrichment."""

from __future__ import annotations

from data_pipeline.amayama_catalog_auto import decode_from_chassis, enrich_hints_with_vin
from data_pipeline.chassis_catalog_registry import (
    list_brands,
    load_catalogs,
    resolve_chassis_vin,
    set_active_brand,
)


def setup_function() -> None:
    load_catalogs(force=True)
    set_active_brand("nissan")


def test_all_partsouq_makers_have_catalog_entry() -> None:
    brands = set(list_brands())
    # Curated orchestrator list (slug form)
    required = {
        "nissan",
        "toyota",
        "honda",
        "bmw",
        "mercedes-benz",
        "volkswagen",
        "ford",
        "hyundai",
        "kia",
        "mazda",
    }
    assert required <= brands
    assert len(brands) >= 40


def test_nissan_curated_jj10_prefix() -> None:
    set_active_brand("nissan")
    rows = decode_from_chassis("JJ10", discover_unmapped=False)
    assert rows
    assert rows[0].vin_prefix == "SJNFBAJ10"
    assert rows[0].source == "local"


def test_toyota_curated_and_stub() -> None:
    set_active_brand("toyota")
    curated = decode_from_chassis("ZVW30", discover_unmapped=False)
    assert curated[0].vin_prefix == "JTDKN3DU"
    stub = resolve_chassis_vin("ZZZ99", brand="toyota")
    assert stub["enrichment_source"] == "epc_stub"
    assert stub["vin_prefix"] == "JTDZZZ99"
    assert "Toyota" in (stub["model_variant"] or "")


def test_enrich_hints_bmw_stub() -> None:
    set_active_brand("bmw")
    out = enrich_hints_with_vin({"chassis_code": "G20", "model_variant": "3 Series"})
    assert out.get("vin_prefix") == "WBAG20"
    assert out.get("model_variant")


def test_orchestrator_parse_argv_includes_brand() -> None:
    from pathlib import Path

    from data_pipeline.partsouq_catalog_orchestrator import (
        OrchestratorOptions,
        build_maker_paths,
        build_parse_watch_argv,
    )

    paths = build_maker_paths("Toyota", Path("out/makers"))
    argv = build_parse_watch_argv(paths, OrchestratorOptions(makers=["Toyota"]))
    assert "--brand" in argv
    assert "toyota" in argv
    assert "--scrape-config" in argv
