"""Tests for unmapped chassis discovery (no fake vin_prefixes)."""

from __future__ import annotations

import logging
from pathlib import Path

from data_pipeline.chassis_discovery import (
    curation_stubs,
    list_unmapped_chassis,
    note_unmapped_chassis,
    reset_warning_state,
    set_discovery_db,
)
from data_pipeline.vin_decode import decode_from_chassis, enrich_hints_with_vin


def _iso_db(tmp_path: Path) -> Path:
    db = tmp_path / "unmapped.db"
    set_discovery_db(db)
    reset_warning_state()
    return db


def test_known_chassis_no_discovery_event(tmp_path: Path, caplog) -> None:
    db = _iso_db(tmp_path)
    with caplog.at_level(logging.WARNING, logger="data_pipeline.chassis_discovery"):
        rows = decode_from_chassis("D40")
        hints = enrich_hints_with_vin({"chassis_code": "D40", "vid": "1"})
    assert rows
    assert rows[0].vin_prefix == "MNTCCND40"
    assert hints.get("vin_prefix") == "MNTCCND40"
    assert list_unmapped_chassis(db) == []
    assert not any("Unmapped chassis" in r.message for r in caplog.records)


def test_unknown_chassis_recorded_with_epc_stub(tmp_path: Path, caplog) -> None:
    db = _iso_db(tmp_path)
    chassis = "ZZ99TEST"  # guaranteed absent from curated map
    with caplog.at_level(logging.WARNING, logger="data_pipeline.chassis_discovery"):
        rows1 = decode_from_chassis(
            chassis,
            model_variant="Test Phantom",
            example_vid="999001",
            example_url="https://partsouq.com/en/catalog/genuine/vehicle?vid=999001",
        )
        rows2 = decode_from_chassis(
            chassis,
            example_vid="999002",
            example_url="https://partsouq.com/en/catalog/genuine/vehicle?vid=999002",
        )
        hints = enrich_hints_with_vin(
            {
                "chassis_code": chassis,
                "vid": "999003",
                "source_url": "https://partsouq.com/vehicle?vid=999003",
            }
        )

    # Multi-make epc_stub supplies a garage search key; still recorded for curation.
    assert rows1 and rows1[0].vin_prefix == f"JN1{chassis}"
    assert rows1[0].source == "epc_stub"
    assert rows2 and rows2[0].vin_prefix == f"JN1{chassis}"
    assert hints.get("vin_prefix") == f"JN1{chassis}"
    assert "source_url" not in hints

    found = list_unmapped_chassis(db)
    assert len(found) == 1
    row = found[0]
    assert row["chassis_code"] == chassis
    assert row["hit_count"] >= 2
    assert row["example_vid"] == "999001"
    assert "999001" in (row["example_url"] or "")
    assert row["model_variant"] == "Test Phantom"

    warnings = [r for r in caplog.records if "Unmapped chassis" in r.message]
    assert len(warnings) == 1
    assert chassis in warnings[0].message


def test_enrich_stub_prefix_and_curation_export(tmp_path: Path) -> None:
    _iso_db(tmp_path)
    hints = enrich_hints_with_vin(
        {"chassis_code": "NOPE1", "model_variant": "Unknown"}
    )
    assert hints.get("chassis_code") == "NOPE1" or hints.get("model_variant")
    assert hints.get("vin_prefix") == "JN1NOPE1"
    stubs = curation_stubs()
    assert any(s["chassis_code"] == "NOPE1" for s in stubs)
    stub = next(s for s in stubs if s["chassis_code"] == "NOPE1")
    assert stub["vin_prefixes"] == []

def test_note_unmapped_dedupes(tmp_path: Path, caplog) -> None:
    db = _iso_db(tmp_path)
    with caplog.at_level(logging.WARNING, logger="data_pipeline.chassis_discovery"):
        assert note_unmapped_chassis("ABC1", example_vid="1") is True
        assert note_unmapped_chassis("ABC1", example_vid="2") is False
        assert note_unmapped_chassis("ABC2", example_vid="3") is True
    rows = {r["chassis_code"]: r for r in list_unmapped_chassis(db)}
    assert rows["ABC1"]["hit_count"] == 2
    assert rows["ABC1"]["example_vid"] == "1"
    assert rows["ABC2"]["hit_count"] == 1
    assert sum(1 for r in caplog.records if "Unmapped chassis" in r.message) == 2
