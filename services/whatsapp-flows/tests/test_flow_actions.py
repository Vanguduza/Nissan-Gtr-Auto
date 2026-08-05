"""Unit tests for Flow action routing (no network / no Supabase)."""

from __future__ import annotations

from app.api.v1.flow_endpoint import handle_flow_action


def test_ping() -> None:
    assert handle_flow_action({"action": "ping"}) == {"data": {"status": "active"}}


def test_init_routes_to_vin_search() -> None:
    out = handle_flow_action({"action": "INIT"})
    assert out["screen"] == "VIN_SEARCH"
    assert "welcome_text" in out["data"]


def test_data_exchange_nested_action_calculate_cart(monkeypatch) -> None:
    from app.services import cart_service

    class FakeQuote:
        currency = "USD"
        total = 10.0
        lines = []
        missing_oems = []

        def summary_text(self) -> str:
            return "1x Water Pump — USD 10.00\nTotal: USD 10.00"

    monkeypatch.setattr(
        cart_service,
        "calculate_cart",
        lambda *a, **k: FakeQuote(),
    )
    # Re-bind symbol used inside flow_endpoint
    import app.api.v1.flow_endpoint as fe

    monkeypatch.setattr(fe, "calculate_cart", lambda *a, **k: FakeQuote())

    out = handle_flow_action(
        {
            "action": "data_exchange",
            "screen": "PARTS_SELECT",
            "data": {"action": "calculate_cart", "part_ids": ["16100-JF00A"]},
        },
    )
    assert out["screen"] == "CART_REVIEW"
    assert "Water Pump" in out["data"]["cart_summary_text"]
