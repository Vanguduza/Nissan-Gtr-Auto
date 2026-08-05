"""Phase C scaffold — StatsForecast / Prophet write-path into forecast_suggestions.

Does **not** run models in CI by default. Optional extra: `pip install -e ".[forecast]"`.

Contract:
- Read velocity / ABC / reorder hints from Postgres (service_role) or local fixtures.
- Write structured `reason` JSON into `forecast_suggestions` only — never auto-PO,
  never journal posts. Standing hard-exclusion policies apply (tax-authority / statutory
  payroll integrations remain out of scope).
- Gorse “bought together” is a separate docker profile (see infra/satellites).

See docs/plans/2026-08-03-ai-autonomous-erp-layer.md Phase C.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from typing import Any


@dataclass(frozen=True)
class ForecastSuggestionPatch:
    """Payload fragment merged into forecast_suggestions.reason."""

    source: str  # "statsforecast" | "prophet" | "stub"
    oem_part_number: str
    horizon_days: int
    point_forecast: float | None
    notes: str

    def to_reason_json(self) -> dict[str, Any]:
        return {
            "phase_c": True,
            "model": asdict(self),
        }


def build_stub_reason(
    oem_part_number: str,
    *,
    horizon_days: int = 28,
    notes: str = "Phase C scaffold — no model fitted",
) -> dict[str, Any]:
    """Deterministic stub used when forecast extras are not installed."""
    patch = ForecastSuggestionPatch(
        source="stub",
        oem_part_number=oem_part_number,
        horizon_days=horizon_days,
        point_forecast=None,
        notes=notes,
    )
    return patch.to_reason_json()


def try_statsforecast_available() -> bool:
    try:
        import statsforecast  # noqa: F401
        return True
    except ImportError:
        return False


def enrich_reason_for_oem(oem_part_number: str, existing: dict[str, Any] | None = None) -> dict[str, Any]:
    """Merge Phase C stub (or future model) into an existing reason object."""
    base = dict(existing or {})
    if try_statsforecast_available():
        # Real fit lands in a follow-up PR — keep scaffold fail-closed to stub.
        base.update(
            build_stub_reason(
                oem_part_number,
                notes="statsforecast installed but fit not wired yet",
            )
        )
    else:
        base.update(build_stub_reason(oem_part_number))
    return base


def main() -> None:
    sample = enrich_reason_for_oem("15208-65F0C")
    print(json.dumps(sample, indent=2))


if __name__ == "__main__":
    main()
