"""D-57 checkout display — Python parity with `@gtr/payments` `buildCheckoutDisplay`.

Browse/cart stays USD; ZiG only at pay (EcoCash) with ops daily rate + optional
`fx_rate_id`. Payable is always MoneyMinor-style (amount_minor + currency).
Never invents rates or payable amounts — callers supply usd_minor + ops rate.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

PayMethod = Literal["contipay", "paynow", "ecocash", "cod", "stub", "cash"]
CurrencyCode = Literal["USD", "ZIG"]

# Both USD and ZiG use 2 decimal minor units in GTR money helpers.
_MINOR_PER_MAJOR = 100


class CheckoutDisplayError(ValueError):
    """Fail-closed checkout display errors (missing/invalid ZiG rate)."""


@dataclass(frozen=True)
class MoneyMinor:
    amount_minor: int
    currency: CurrencyCode
    fx_rate_id: str | None = None


@dataclass(frozen=True)
class CheckoutDisplay:
    browse_currency: CurrencyCode  # always USD for D-57
    pay_currency: CurrencyCode
    payable: MoneyMinor
    fx_rate_id: str | None = None
    indicative_zig_minor: int | None = None


def usd_major_to_minor(usd_major: float) -> int:
    """Convert USD major units to minor (half-away-from-zero), matching `@gtr/shared`."""
    if not _is_finite(usd_major):
        raise CheckoutDisplayError("usd_major must be finite")
    return _round_half_away(usd_major * _MINOR_PER_MAJOR)


def minor_to_major(amount_minor: int) -> float:
    return amount_minor / float(_MINOR_PER_MAJOR)


def build_checkout_display(
    *,
    usd_minor: int,
    pay_method: PayMethod,
    zig_rate_per_usd: float | None = None,
    fx_rate_id: str | None = None,
) -> CheckoutDisplay:
    """Build D-57 checkout display from USD minor + ops rate.

    Raises:
        CheckoutDisplayError: when EcoCash lacks a positive finite ZiG rate.
    """
    if not isinstance(usd_minor, int):
        raise CheckoutDisplayError("usd_minor must be an int (MoneyMinor)")

    zig_wallet = pay_method == "ecocash"
    if zig_wallet:
        rate = zig_rate_per_usd
        if rate is None or not _is_finite(rate) or rate <= 0:
            raise CheckoutDisplayError("Daily ZiG rate required for EcoCash checkout")
        zig_minor = _round_half_away(float(usd_minor) * float(rate))
        return CheckoutDisplay(
            browse_currency="USD",
            pay_currency="ZIG",
            payable=MoneyMinor(
                amount_minor=zig_minor,
                currency="ZIG",
                fx_rate_id=fx_rate_id,
            ),
            fx_rate_id=fx_rate_id,
        )

    indicative: int | None = None
    if (
        zig_rate_per_usd is not None
        and _is_finite(zig_rate_per_usd)
        and zig_rate_per_usd > 0
    ):
        indicative = _round_half_away(float(usd_minor) * float(zig_rate_per_usd))

    return CheckoutDisplay(
        browse_currency="USD",
        pay_currency="USD",
        payable=MoneyMinor(
            amount_minor=usd_minor,
            currency="USD",
            fx_rate_id=None,
        ),
        fx_rate_id=None,
        indicative_zig_minor=indicative,
    )


def build_zig_settlement(
    *,
    usd_minor: int,
    zig_rate_per_usd: float | None,
    fx_rate_id: str | None,
) -> CheckoutDisplay:
    """ZiG settlement from USD minor + ops rate (fail-closed when rate missing)."""
    return build_checkout_display(
        usd_minor=usd_minor,
        pay_method="ecocash",
        zig_rate_per_usd=zig_rate_per_usd,
        fx_rate_id=fx_rate_id,
    )


def payable_amount_label(display: CheckoutDisplay) -> str:
    """Human label for WhatsApp CTA / EcoCash prompt (major units)."""
    major = minor_to_major(display.payable.amount_minor)
    return f"{display.payable.currency} {major:.2f}"


def _is_finite(value: float) -> bool:
    return value == value and value not in (float("inf"), float("-inf"))


def _round_half_away(scaled: float) -> int:
    """Match JS Math.round half-away-from-zero / Kotlin kotlin.math.round."""
    if scaled >= 0:
        return int(scaled + 0.5)
    return int(scaled - 0.5)
