from __future__ import annotations

import json
from pathlib import Path

from data_pipeline.parse_fast import parse_fast_file

FIXTURE_DIR = Path(__file__).resolve().parent.parent / "fixtures" / "navara_d40_yd25"
FAST_SOURCE = FIXTURE_DIR / "fast_source.json"


def test_parse_fast_produces_valid_bundle() -> None:
    bundle = parse_fast_file(FAST_SOURCE)
    assert len(bundle["vehicle_master"]) == 1
    assert len(bundle["pnc_categories"]) == 4
    oems = {row["oem_part_number"] for row in bundle["part_fitment"]}
    assert oems == {
        "15208-65F0C",
        "40206-EA00A",
        "21410-JF00A",
        "21010-JF00A",
        "16546-00Q0A",
    }


def test_parse_matches_checked_in_fixtures() -> None:
    bundle = parse_fast_file(FAST_SOURCE)
    for name in ("vehicle_master", "pnc_categories", "part_fitment", "diagram_assets"):
        expected = json.loads((FIXTURE_DIR / f"{name}.json").read_text(encoding="utf-8"))
        assert bundle[name] == expected
