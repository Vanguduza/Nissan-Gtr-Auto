"""Tests for priority chassis queue filtering."""

from __future__ import annotations

import json
from pathlib import Path

from data_pipeline.amayama_catalog_auto import claim_next_url, enqueue_url, init_db
from data_pipeline.parse_partsouq_html import normalize_chassis_code
from data_pipeline.priority_chassis import PriorityChassisFilter, build_priority_filter


def test_normalize_ad0nn_to_ad0() -> None:
    assert normalize_chassis_code("AD0NN") == "AD0"


def test_priority_filter_matches_aliases() -> None:
    pf = build_priority_filter(
        None,
        extra_codes=["AD0NN", "D40"],
    )
    assert pf is not None
    assert pf.chassis_in_priority("AD0NN") is True
    assert pf.chassis_in_priority("AD0") is True
    assert pf.chassis_in_priority("R35") is False


def test_priority_filter_bootstrap_and_vehicle_discovery() -> None:
    pf = PriorityChassisFilter.from_codes(["D40"])
    assert pf.is_eligible(
        "https://partsouq.com/en/catalog/genuine/locate?c=Nissan",
        {},
        hierarchy_level=0,
    )
    assert pf.is_eligible(
        "https://partsouq.com/en/catalog/genuine/vehicle?c=Nissan&vid=99",
        {},
        hierarchy_level=2,
    )
    assert not pf.is_eligible(
        "https://partsouq.com/en/catalog/genuine/unit?c=Nissan&uid=1",
        {"chassis_code": "R35"},
        hierarchy_level=5,
    )


def test_claim_next_url_priority_skips_non_priority_parts(tmp_path: Path) -> None:
    db = tmp_path / "state.db"
    init_db(db)
    pf = PriorityChassisFilter.from_codes(["D40"])
    enqueue_url(
        db,
        "https://partsouq.com/en/catalog/genuine/unit?c=Nissan&uid=1",
        hierarchy_level=5,
        vehicle_context={"chassis_code": "R35"},
    )
    enqueue_url(
        db,
        "https://partsouq.com/en/catalog/genuine/unit?c=Nissan&uid=2",
        hierarchy_level=5,
        vehicle_context={"chassis_code": "D40"},
    )
    claimed = claim_next_url(db, mode="hybrid", priority=pf)
    assert claimed is not None
    assert claimed[0].endswith("uid=2")


def test_claim_next_url_priority_vid_map(tmp_path: Path) -> None:
    db = tmp_path / "state.db"
    init_db(db)
    pf = PriorityChassisFilter.from_codes(["T31"])
    pf.vid_chassis["190773"] = "T31"
    enqueue_url(
        db,
        "https://partsouq.com/en/catalog/genuine/parts?c=Nissan&vid=190773&gid=1",
        hierarchy_level=5,
        vehicle_context={},
    )
    enqueue_url(
        db,
        "https://partsouq.com/en/catalog/genuine/parts?c=Nissan&vid=999999&gid=1",
        hierarchy_level=5,
        vehicle_context={"chassis_code": "R35"},
    )
    claimed = claim_next_url(db, mode="hybrid", priority=pf)
    assert claimed is not None
    assert "vid=190773" in claimed[0]


def test_claim_next_url_priority_scans_beyond_first_batch(tmp_path: Path) -> None:
    """Hybrid order front-loads non-priority vehicles; claim must scan deeper."""
    db = tmp_path / "state.db"
    init_db(db)
    pf = PriorityChassisFilter.from_codes(["D40"])
    for i in range(600):
        enqueue_url(
            db,
            f"https://partsouq.com/en/catalog/genuine/vehicle?c=Nissan&vid={i}",
            hierarchy_level=2,
            vehicle_context={"chassis_code": "R35"},
        )
    enqueue_url(
        db,
        "https://partsouq.com/en/catalog/genuine/unit?c=Nissan&uid=99",
        hierarchy_level=5,
        vehicle_context={"chassis_code": "D40"},
    )
    claimed = claim_next_url(db, mode="hybrid", priority=pf)
    assert claimed is not None
    assert "uid=99" in claimed[0]


def test_load_priority_chassis_file(tmp_path: Path) -> None:
    path = tmp_path / "pri.json"
    path.write_text(
        json.dumps(
            {
                "chassis_codes": ["D22", "AD0NN"],
                "chassis": {
                    "D22": {"model_variant": "Navara D22", "aliases": ["D22"]},
                    "AD0": {"canonical": "AD0", "aliases": ["AD0NN", "AD0"]},
                },
            }
        ),
        encoding="utf-8",
    )
    pf = build_priority_filter(path)
    assert pf is not None
    assert "D22" in pf.codes
    assert pf.chassis_in_priority("AD0NN") is True


def test_resolve_parse_db_out_layout(tmp_path: Path) -> None:
    from data_pipeline.priority_chassis import resolve_parse_db

    state_db = tmp_path / "crawler_state.db"
    parse_db = tmp_path / "out" / "cache_parse_state.db"
    parse_db.parent.mkdir(parents=True)
    parse_db.write_text("", encoding="utf-8")
    assert resolve_parse_db(state_db=state_db) == parse_db
