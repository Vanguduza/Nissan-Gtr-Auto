from __future__ import annotations

from data_pipeline.hierarchy import (
    HierarchyLevel,
    classify_url,
    parse_year_range,
    should_enqueue_child,
)


def test_classify_hierarchy_levels() -> None:
    root = classify_url("https://www.amayama.com/en/catalogs/nissan")
    model = classify_url("https://www.amayama.com/en/catalogs/nissan/navara")
    chassis = classify_url("https://www.amayama.com/en/catalogs/nissan/navara/d40")
    engine = classify_url(
        "https://www.amayama.com/en/catalogs/nissan/navara/d40/yd25"
    )
    diagram = classify_url(
        "https://www.amayama.com/en/catalogs/nissan/navara/d40/yd25/engine/oil-filter"
    )
    assert root.level == HierarchyLevel.ROOT
    assert model.level == HierarchyLevel.MODEL
    assert chassis.level == HierarchyLevel.CHASSIS
    assert chassis.chassis_code == "D40"
    assert engine.engine_code == "YD25"
    assert diagram.level == HierarchyLevel.DIAGRAM


def test_parse_year_range() -> None:
    assert parse_year_range("Navara D40 2005-2015") == (2005, 2015)
    assert parse_year_range("2010 – present")[0] == 2010


def test_should_enqueue_child_stays_in_lineage() -> None:
    parent = classify_url("https://www.amayama.com/en/catalogs/nissan/navara/d40")
    child_ok = "https://www.amayama.com/en/catalogs/nissan/navara/d40/yd25"
    child_bad = "https://www.amayama.com/en/catalogs/nissan/x-trail/t31"
    assert should_enqueue_child(parent, child_ok)
    assert not should_enqueue_child(parent, child_bad)


def test_classify_partsouq_levels() -> None:
    root = classify_url("https://partsouq.com/en/catalog/genuine/locate?c=Nissan")
    filt = classify_url("https://partsouq.com/en/catalog/genuine/filter?c=Nissan&model=GTR")
    groups = classify_url(
        "https://partsouq.com/en/catalog/genuine/groups?c=NISSAN201809&q=SJNFBAJ11Z1164960"
    )
    unit = classify_url(
        "https://partsouq.com/en/catalog/genuine/unit?c=NISSAN201809&uid=123"
    )
    assert root.level == HierarchyLevel.ROOT
    assert filt.level == HierarchyLevel.MODEL
    assert groups.level == HierarchyLevel.ASSEMBLY
    assert unit.level == HierarchyLevel.DIAGRAM
    assert should_enqueue_child(root, groups.url)


def test_is_in_scope_english_nissan_only() -> None:
    from data_pipeline.amayama_catalog_auto import ScrapeConfig, is_in_scope_url

    cfg = ScrapeConfig()
    assert is_in_scope_url(
        "https://partsouq.com/en/catalog/genuine/locate?c=Nissan", cfg
    )
    assert is_in_scope_url(
        "https://partsouq.com/en/catalog/genuine/groups?c=NISSAN201809&q=x", cfg
    )
    assert not is_in_scope_url(
        "https://partsouq.com/es/catalog/genuine/locate?c=Nissan", cfg
    )
    assert not is_in_scope_url(
        "https://partsouq.com/en/catalog/genuine/locate?c=Toyota", cfg
    )
    assert not is_in_scope_url(
        "https://partsouq.com/en/catalog/genuine/locate?c=Infiniti", cfg
    )
    assert not is_in_scope_url(
        "https://partsouq.com/de/catalog/genuine/locate?c=Nissan", cfg
    )
