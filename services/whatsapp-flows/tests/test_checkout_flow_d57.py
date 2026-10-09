"""Checkout flow wiring for D-57 EcoCash fail-closed settle."""

from __future__ import annotations

from dataclasses import dataclass

from app.api.v1 import flow_endpoint as fe
from app.services.checkout_display import build_checkout_display, usd_major_to_minor
from app.services.fx_rates import DailyZigRate


@dataclass
class _Line:
    oem: str = "16100-JF00A"
    description: str = "Water Pump"
    unit_price: float = 10.0
    qty: int = 1
    stock_item_id: str = "stock-1"


class _FakeQuote:
    currency = "USD"
    total = 10.0
    subtotal = 10.0
    delivery_fee = 0.0
    lines = [_Line()]
    missing_oems: list[str] = []

    def summary_text(self) -> str:
        return "1x Water Pump — USD 10.00\nTotal: USD 10.00"


def test_checkout_ecocash_fails_closed_without_daily_rate(monkeypatch) -> None:
    monkeypatch.setattr(fe, "calculate_cart", lambda *a, **k: _FakeQuote())
    monkeypatch.setattr(fe, "fetch_daily_zig_rate", lambda *a, **k: None)

    out = fe.handle_flow_action(
        {
            "action": "checkout",
            "data": {
                "part_ids": ["16100-JF00A"],
                "payment_method": "ecocash",
                "ecocash_payer_mode": "whatsapp",
                "wa_id": "263771234567",
            },
        },
    )
    assert out["screen"] == "CHECKOUT"
    assert "Daily ZiG rate required" in out["data"]["error_message"]


def test_checkout_ecocash_persists_zig_settle(monkeypatch) -> None:
    captured: dict = {}

    monkeypatch.setattr(fe, "calculate_cart", lambda *a, **k: _FakeQuote())
    monkeypatch.setattr(
        fe,
        "fetch_daily_zig_rate",
        lambda *a, **k: DailyZigRate(rate=25.0, fx_rate_id="rate-1"),
    )

    def _fake_create(**kwargs):
        captured.update(kwargs)
        display = kwargs["checkout_display"]
        return {
            "id": "order-1",
            "currency": "USD",
            "total": 10.0,
            "wa_id": kwargs.get("wa_id"),
            "settle_currency": display.pay_currency,
            "settle_amount_minor": display.payable.amount_minor,
            "fx_rate_id": display.fx_rate_id,
        }

    monkeypatch.setattr(fe, "create_pending_order", _fake_create)

    out = fe.handle_flow_action(
        {
            "action": "checkout",
            "data": {
                "part_ids": ["16100-JF00A"],
                "payment_method": "ecocash",
                "ecocash_payer_mode": "whatsapp",
                "wa_id": "263771234567",
            },
        },
    )
    assert out["screen"] == "SUCCESS"
    display = captured["checkout_display"]
    assert display.pay_currency == "ZIG"
    assert display.payable.amount_minor == 25_000  # 10.00 USD → 1000 minor × 25
    assert display.fx_rate_id == "rate-1"
    side = out["_gtr_side_effects"]
    assert side["send_ecocash_push"] is True


def test_checkout_paynow_keeps_usd_payable(monkeypatch) -> None:
    captured: dict = {}
    monkeypatch.setattr(fe, "calculate_cart", lambda *a, **k: _FakeQuote())
    monkeypatch.setattr(
        fe,
        "fetch_daily_zig_rate",
        lambda *a, **k: DailyZigRate(rate=27.5, fx_rate_id="rate-2"),
    )

    def _fake_create(**kwargs):
        captured.update(kwargs)
        display = kwargs["checkout_display"]
        return {
            "id": "order-2",
            "currency": "USD",
            "total": 10.0,
            "payment_link": "https://example.test/pay",
            "wa_id": kwargs.get("wa_id"),
            "settle_currency": display.pay_currency,
            "settle_amount_minor": display.payable.amount_minor,
        }

    monkeypatch.setattr(fe, "create_pending_order", _fake_create)

    out = fe.handle_flow_action(
        {
            "action": "checkout",
            "data": {
                "part_ids": ["16100-JF00A"],
                "payment_method": "paynow",
                "wa_id": "263771234567",
            },
        },
    )
    assert out["screen"] == "SUCCESS"
    display = captured["checkout_display"]
    assert display.pay_currency == "USD"
    assert display.payable.amount_minor == usd_major_to_minor(10.0)
    assert display.indicative_zig_minor == build_checkout_display(
        usd_minor=1000,
        pay_method="paynow",
        zig_rate_per_usd=27.5,
    ).indicative_zig_minor
    assert out["_gtr_side_effects"]["amount_label"] == "USD 10.00"
