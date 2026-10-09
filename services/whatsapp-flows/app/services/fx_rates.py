"""Ops daily ZiG rate lookup for D-57 settle (never invent payable amounts)."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import Any

from app.core.supabase_client import get_supabase


@dataclass(frozen=True)
class DailyZigRate:
    """Row from `daily_exchange_rates` used for EcoCash / ZiG settle."""

    rate: float  # ZiG per 1 USD
    fx_rate_id: str
    rate_date: str | None = None


def fetch_daily_zig_rate(*, as_of: date | None = None) -> DailyZigRate | None:
    """Latest ZIG `rate_date` ≤ as-of (same ordering as `get_zig_exchange_rate` / web).

    Returns None when no positive ops row — callers must fail closed for EcoCash.
    Does not fall back to invented env rates for payable settlement.
    """
    as_of_date = (as_of or date.today()).isoformat()
    sb = get_supabase()
    rows = (
        sb.table("daily_exchange_rates")
        .select("id,rate,rate_date")
        .eq("currency", "ZIG")
        .lte("rate_date", as_of_date)
        .order("rate_date", desc=True)
        .limit(1)
        .execute()
    )
    data: list[dict[str, Any]] = rows.data or []
    if not data:
        return None
    row = data[0]
    try:
        rate = float(row["rate"])
    except (TypeError, ValueError, KeyError):
        return None
    if not (rate == rate) or rate <= 0:
        return None
    fx_id = str(row.get("id") or "").strip()
    if not fx_id:
        return None
    rate_date = row.get("rate_date")
    return DailyZigRate(
        rate=rate,
        fx_rate_id=fx_id,
        rate_date=str(rate_date) if rate_date is not None else None,
    )
