"""Monotonic, compare-and-set payment transitions for authenticated provider events."""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from typing import Any

from fastapi import HTTPException

PAYABLE_ORDER_STATUSES = ("PENDING", "FAILED")


def _minor_units(raw: Any, *, stored: bool = False) -> int:
    try:
        if isinstance(raw, bool):
            raise InvalidOperation
        amount = Decimal(str(raw))
        if (
            not amount.is_finite()
            or amount <= 0
            or amount > Decimal("9999999999999999.99")
            or amount * 100 != (amount * 100).to_integral_value()
        ):
            raise InvalidOperation
        return int(amount * 100)
    except (InvalidOperation, ValueError, TypeError, OverflowError) as exc:
        raise HTTPException(
            status_code=503 if stored else 422,
            detail="stored/payment amount must be finite, positive and expressed in minor units",
        ) from exc


@dataclass(frozen=True)
class PaymentProof:
    provider: str
    amount_minor: int
    currency: str
    merchant_reference: str
    transaction_reference: str

    @classmethod
    def parse(
        cls,
        provider: Any,
        amount: Any,
        currency: Any,
        merchant_reference: Any,
        transaction_reference: Any,
    ) -> PaymentProof:
        provider = str(provider or "").strip().lower()
        currency = str(currency or "").strip().upper()
        merchant_reference = str(merchant_reference or "").strip()
        transaction_reference = str(transaction_reference or "").strip()
        if provider not in {"paynow", "contipay", "ecocash"} or currency not in {"USD", "ZIG"}:
            raise HTTPException(
                status_code=422, detail="payment provider and currency are required"
            )
        if not merchant_reference or not transaction_reference:
            raise HTTPException(
                status_code=422, detail="merchant and provider transaction references are required"
            )
        return cls(
            provider, _minor_units(amount), currency, merchant_reference, transaction_reference
        )


def _validate_proof(order: dict[str, Any], proof: PaymentProof) -> None:
    provider = str(order.get("payment_provider") or "").lower()
    source = order.get("payment_source_reference")
    if not source and provider in {"paynow", "contipay"}:
        source = order["id"]  # The retained aggregator checkout contract uses the order UUID.
    if not source or provider not in {"paynow", "contipay", "ecocash"}:
        raise HTTPException(
            status_code=503, detail="order has no registered provider/source reference"
        )
    currency = str(order.get("settle_currency") or order.get("currency") or "").upper()
    if provider == "ecocash" and currency != "ZIG":
        raise HTTPException(
            status_code=503,
            detail="EcoCash order requires an authoritative ZiG settlement snapshot",
        )
    if order.get("settle_currency"):
        raw_minor = order.get("settle_amount_minor")
        total = order.get("settle_total")
        if raw_minor is None and total is None:
            raise HTTPException(status_code=503, detail="stored settlement amount is missing")
        amount_minor = (
            _minor_units(total, stored=True)
            if raw_minor is None
            else _minor_units(Decimal(str(raw_minor)) / 100, stored=True)
        )
        if total is not None and _minor_units(total, stored=True) != amount_minor:
            raise HTTPException(status_code=503, detail="stored settlement amount fields disagree")
    else:
        amount_minor = _minor_units(order.get("total"), stored=True)
    if (
        proof.provider != provider
        or proof.merchant_reference != str(source)
        or proof.currency != currency
        or proof.amount_minor != amount_minor
    ):
        raise HTTPException(
            status_code=409, detail="payment proof does not match the stored order settlement"
        )
    registered_reference = order.get("payment_reference")
    if registered_reference and proof.transaction_reference != registered_reference:
        raise HTTPException(
            status_code=409,
            detail="payment proof does not match the registered provider transaction",
        )


def _current_order(db: Any, order_id: str) -> dict[str, Any]:
    rows = db.table("whatsapp_flow_orders").select("*").eq("id", order_id).limit(1).execute()
    if not rows.data:
        raise HTTPException(status_code=404, detail="order not found")
    return rows.data[0]


def mark_order_paid(db: Any, order_id: str, proof: PaymentProof) -> tuple[dict[str, Any], bool]:
    """Only the atomic state-change winner may queue the initial receipt.

    Database row locking rechecks the eligible status after a competing update.
    Already paid/fulfilled/cancelled/refunded states cannot be reopened by a replay.
    Missing provider references never clear an existing one.
    """
    snapshot = _current_order(db, order_id)
    _validate_proof(snapshot, proof)
    query = (
        db.table("whatsapp_flow_orders")
        .update({"status": "PAID", "payment_reference": proof.transaction_reference})
        .eq("id", order_id)
        .in_("status", list(PAYABLE_ORDER_STATUSES))
    )
    # Keep proof verification and the state claim bound to the same immutable
    # financial snapshot even if another worker edits a pending order concurrently.
    for field in (
        "payment_provider",
        "payment_source_reference",
        "payment_reference",
        "total",
        "currency",
        "settle_currency",
        "settle_total",
        "settle_amount_minor",
    ):
        value = snapshot.get(field)
        query = query.is_(field, "null") if value is None else query.eq(field, value)
    updated = query.execute()
    if updated.data:
        return updated.data[0], True
    current = _current_order(db, order_id)
    _validate_proof(current, proof)
    if current.get("status") in PAYABLE_ORDER_STATUSES:
        # Do not acknowledge a still-unsettled payment after a snapshot conflict.
        # The authenticated sender must retry against the current registered attempt.
        raise HTTPException(
            status_code=409, detail="payment snapshot changed; retry the verified callback"
        )
    return current, False


def record_unpaid_status(
    db: Any, order_id: str, provider_status: str
) -> tuple[dict[str, Any], bool]:
    """A failure/cancellation can only close a still-pending payment.

    Pending/unknown/fulfilment/refund notifications do not author business-state
    changes. Financial reversals and fulfilment have their own explicit workflows.
    A negative callback never replaces or erases the payment reference.
    """
    status = provider_status.strip().upper()
    if status in {"FAILED", "CANCELLED"}:
        updated = (
            db.table("whatsapp_flow_orders")
            .update({"status": status})
            .eq("id", order_id)
            .eq("status", "PENDING")
            .execute()
        )
        if updated.data:
            return updated.data[0], True
    return _current_order(db, order_id), False
