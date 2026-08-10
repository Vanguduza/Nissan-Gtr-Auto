"""JSON Schema validation — fail closed on any schema error."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import jsonschema
from jsonschema import Draft202012Validator

SCHEMA_NAMES = (
    "vehicle_master",
    "pnc_categories",
    "part_fitment",
    "diagram_assets",
)

PACKAGE_ROOT = Path(__file__).resolve().parent.parent
SCHEMAS_DIR = PACKAGE_ROOT / "schemas"


class ValidationError(Exception):
    """Raised when one or more payloads fail schema validation."""

    def __init__(self, errors: list[str]) -> None:
        self.errors = errors
        super().__init__(f"{len(errors)} validation error(s): " + "; ".join(errors))


def schema_path(name: str) -> Path:
    if name not in SCHEMA_NAMES:
        raise KeyError(f"Unknown schema: {name}")
    return SCHEMAS_DIR / f"{name}.schema.json"


def load_schema(name: str) -> dict[str, Any]:
    with schema_path(name).open(encoding="utf-8") as fh:
        return json.load(fh)


def load_validator(name: str) -> Draft202012Validator:
    schema = load_schema(name)
    jsonschema.Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema)


def validate_record(name: str, record: dict[str, Any]) -> None:
    validator = load_validator(name)
    errors = sorted(validator.iter_errors(record), key=lambda e: e.path)
    if errors:
        messages = [f"{name}: {e.message} @ {list(e.path)}" for e in errors]
        raise ValidationError(messages)


def validate_records(name: str, records: list[dict[str, Any]]) -> None:
    validator = load_validator(name)
    all_errors: list[str] = []
    for idx, record in enumerate(records):
        errors = sorted(validator.iter_errors(record), key=lambda e: e.path)
        if errors:
            all_errors.extend(
                f"[{idx}] {name}: {e.message} @ {list(e.path)}" for e in errors
            )
            if len(all_errors) >= 50:
                break
    if all_errors:
        raise ValidationError(all_errors)


def validate_bundle(bundle: dict[str, list[dict[str, Any]]]) -> None:
    """Validate a catalog bundle keyed by schema/table name."""
    for name in SCHEMA_NAMES:
        if name not in bundle:
            continue
        validate_records(name, bundle[name])


def validate_json_file(path: Path, schema_name: str) -> None:
    with path.open(encoding="utf-8") as fh:
        payload = json.load(fh)
    if isinstance(payload, list):
        validate_records(schema_name, payload)
    elif isinstance(payload, dict):
        validate_record(schema_name, payload)
    else:
        raise ValidationError([f"{path}: expected object or array"])


def validate_fixture_dir(fixture_dir: Path) -> None:
    mapping = {
        "vehicle_master.json": "vehicle_master",
        "pnc_categories.json": "pnc_categories",
        "part_fitment.json": "part_fitment",
        "diagram_assets.json": "diagram_assets",
    }
    errors: list[str] = []
    for filename, schema_name in mapping.items():
        file_path = fixture_dir / filename
        if not file_path.exists():
            continue
        try:
            validate_json_file(file_path, schema_name)
        except ValidationError as exc:
            errors.extend(exc.errors)
    if errors:
        raise ValidationError(errors)


def main(argv: list[str] | None = None) -> int:
    import argparse

    parser = argparse.ArgumentParser(description="Validate catalog JSON against schemas.")
    parser.add_argument(
        "paths",
        nargs="*",
        type=Path,
        help="Fixture dirs or JSON files (default: fixtures/navara_d40_yd25)",
    )
    args = parser.parse_args(argv)

    targets = args.paths or [PACKAGE_ROOT / "fixtures" / "navara_d40_yd25"]
    try:
        for target in targets:
            if target.is_dir():
                validate_fixture_dir(target)
            else:
                schema_name = target.stem.replace(".schema", "")
                if schema_name not in SCHEMA_NAMES:
                    raise ValidationError([f"Cannot infer schema for {target}"])
                validate_json_file(target, schema_name)
    except ValidationError as exc:
        for msg in exc.errors:
            print(f"ERROR: {msg}")
        return 1

    print(f"OK: validated {len(targets)} target(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
