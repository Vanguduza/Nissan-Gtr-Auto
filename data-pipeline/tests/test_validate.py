from __future__ import annotations

import json
from pathlib import Path

import pytest

from data_pipeline.validate import ValidationError, validate_fixture_dir, validate_record

FIXTURE_DIR = Path(__file__).resolve().parent.parent / "fixtures" / "navara_d40_yd25"


def test_fixture_pack_validates() -> None:
    validate_fixture_dir(FIXTURE_DIR)


def test_invalid_vehicle_rejected() -> None:
    with pytest.raises(ValidationError):
        validate_record("vehicle_master", {"chassis_code": "D40"})


def test_invalid_oem_pattern_rejected() -> None:
    with pytest.raises(ValidationError):
        validate_record(
            "part_fitment",
            {"oem_part_number": "not-a-part"},
        )


def test_validate_cli_ok(capsys) -> None:
    from data_pipeline.validate import main

    assert main([str(FIXTURE_DIR)]) == 0
    assert "OK" in capsys.readouterr().out
