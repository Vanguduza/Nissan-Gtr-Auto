"""D-57 checkout display parity with `@gtr/payments` buildCheckoutDisplay."""

from __future__ import annotations

import pytest

from app.services.checkout_display import (
    CheckoutDisplayError,
    build_checkout_display,
    build_zig_settlement,
    minor_to_major,
    payable_amount_label,
    usd_major_to_minor,
)


def test_paynow_keeps_usd_with_indicative_zig() -> None:
    d = build_checkout_display(
        usd_minor=10_000,
        pay_method="paynow",
        zig_rate_per_usd=27.5,
    )
    assert d.browse_currency == "USD"
    assert d.pay_currency == "USD"
    assert d.payable.amount_minor == 10_000
    assert d.payable.currency == "USD"
    assert d.fx_rate_id is None
    assert d.indicative_zig_minor == 275_000


def test_ecocash_converts_to_zig_with_fx_rate_id() -> None:
    d = build_checkout_display(
        usd_minor=10_000,
        pay_method="ecocash",
        zig_rate_per_usd=25.0,
        fx_rate_id="rate-1",
    )
    assert d.pay_currency == "ZIG"
    assert d.payable.amount_minor == 250_000
    assert d.fx_rate_id == "rate-1"
    assert d.payable.fx_rate_id == "rate-1"
    assert payable_amount_label(d) == "ZIG 2500.00"


def test_ecocash_requires_positive_zig_rate() -> None:
    with pytest.raises(CheckoutDisplayError, match="Daily ZiG rate required"):
        build_checkout_display(usd_minor=10_000, pay_method="ecocash")
    with pytest.raises(CheckoutDisplayError, match="Daily ZiG rate required"):
        build_zig_settlement(usd_minor=10_000, zig_rate_per_usd=0.0, fx_rate_id=None)
    with pytest.raises(CheckoutDisplayError, match="Daily ZiG rate required"):
        build_checkout_display(
            usd_minor=10_000,
            pay_method="ecocash",
            zig_rate_per_usd=float("nan"),
        )


def test_zig_settlement_from_money_minor_not_float_invent() -> None:
    # 42.00 USD → 4200 minor × 26.5 = 111300 ZiG minor
    d = build_zig_settlement(
        usd_minor=4_200,
        zig_rate_per_usd=26.5,
        fx_rate_id="seed-rate",
    )
    assert d.payable.amount_minor == 111_300
    assert d.payable.currency == "ZIG"
    assert d.fx_rate_id == "seed-rate"
    assert minor_to_major(d.payable.amount_minor) == 1113.0


def test_usd_major_to_minor_half_away() -> None:
    assert usd_major_to_minor(12.34) == 1234
    assert usd_major_to_minor(10.0) == 1000
