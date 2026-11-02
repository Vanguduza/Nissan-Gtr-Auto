"""POST /api/v1/payments/callback — Paynow / PSP server-to-server settle.

Fail closed: unauthenticated callers cannot mark orders PAID.
Auth (any one):
  - Paynow form body with valid SHA512 field hash (PAYNOW_INTEGRATION_KEY)
  - Header X-Payments-Webhook-Secret / X-Worker-Secret matching PAYMENTS_WEBHOOK_SECRET
  - Local only: PAYMENTS_ALLOW_UNVERIFIED_LOCAL=1 when secret and key are unset
"""

from __future__ import annotations

import json
import logging
from decimal import Decimal
from typing import Any
from urllib.parse import parse_qs

from fastapi import APIRouter, BackgroundTasks, HTTPException, Request
from pydantic import BaseModel, Field, ValidationError

from app.core.config import get_settings
from app.core.supabase_client import get_supabase
from app.core.webhook_auth import (
    assert_shared_secret,
    header_secret,
    verify_paynow_form_hash,
)
from app.services.meta_client import MetaClient
from app.services.payment_state import PaymentProof, mark_order_paid, record_unpaid_status
from app.services.receipt_pdf import build_receipt_pdf

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/payments", tags=["payments"])


class PaymentCallbackJson(BaseModel):
    """JSON callback shape (also accept form-urlencoded Paynow in raw handler)."""

    order_id: str = Field(..., description="whatsapp_flow_orders.id")
    status: str = Field(..., description="PAID | FAILED | PENDING | …")
    reference: str | None = None
    amount: Decimal | None = None
    provider: str | None = None
    merchant_reference: str | None = None
    currency: str | None = None


async def _deliver_receipt(order: dict[str, Any]) -> None:
    wa_id = order.get("wa_id")
    if not wa_id:
        logger.warning("order %s PAID but no wa_id — skip WhatsApp receipt", order.get("id"))
        return
    pdf = build_receipt_pdf(order)
    client = MetaClient()
    await client.send_receipt_message(
        str(wa_id),
        order_id=str(order["id"]),
        pdf_bytes=pdf,
    )


def _mark_paid(
    order_id: str, reference: str | None, proof: PaymentProof
) -> tuple[dict[str, Any], bool]:
    return mark_order_paid(get_supabase(), order_id, proof)


def _is_paid_status(status: str) -> bool:
    s = status.strip().lower()
    return s in {"paid", "ok", "awaiting delivery", "delivered", "success"}


def _authorize_payment_callback(
    request: Request, raw_body: bytes, is_form: bool
) -> dict[str, str] | None:
    settings = get_settings()
    key = (settings.paynow_integration_key or "").strip()
    secret = (settings.payments_webhook_secret or "").strip()
    local = settings.is_local_development and settings.payments_allow_unverified_local

    if is_form and key:
        ok, fields = verify_paynow_form_hash(raw_body.decode("utf-8", errors="replace"), key)
        if ok:
            return fields
        raise HTTPException(status_code=401, detail="invalid or missing Paynow hash")

    provided = header_secret(
        request,
        "X-Payments-Webhook-Secret",
        "x-payments-webhook-secret",
        "X-Worker-Secret",
        "x-worker-secret",
    )
    assert_shared_secret(
        provided=provided,
        expected=secret,
        allow_unverified_local=local and not key,
        unset_message=(
            "PAYMENTS_WEBHOOK_SECRET (or PAYNOW_INTEGRATION_KEY for form hash) required "
            "(set PAYMENTS_ALLOW_UNVERIFIED_LOCAL=1 for local stub only)"
        ),
    )


@router.post("/callback")
async def payment_callback(
    request: Request,
    background_tasks: BackgroundTasks,
) -> dict[str, Any]:
    """Accept JSON or Paynow-style form body; on PAID update order + send PDF receipt."""
    raw_body = await request.body()
    content_type = (request.headers.get("content-type") or "").lower()
    is_form = "application/json" not in content_type
    verified_fields = _authorize_payment_callback(request, raw_body, is_form=is_form)

    order_id: str | None = None
    status: str | None = None
    reference: str | None = None

    if not is_form:
        try:
            payload = json.loads(raw_body.decode("utf-8") or "{}")
        except Exception as exc:
            raise HTTPException(status_code=422, detail="JSON body required") from exc
        try:
            body = PaymentCallbackJson.model_validate(payload)
        except ValidationError as exc:
            raise HTTPException(status_code=422, detail="invalid payment callback payload") from exc
        order_id, status, reference = body.order_id, body.status, body.reference
        amount, currency, provider, merchant_reference = (
            body.amount,
            body.currency,
            body.provider,
            body.merchant_reference,
        )
    else:
        form_map = verified_fields or {
            k: (v[0] if v else "")
            for k, v in parse_qs(
                raw_body.decode("utf-8", errors="replace"),
                keep_blank_values=True,
            ).items()
        }
        # Consume the same normalized fields authenticated by the Paynow hash.
        order_id = form_map.get("reference", "").strip() or None
        status = form_map.get("status", "").strip() or None
        reference = form_map.get("paynowreference", "").strip() or None
        amount = form_map.get("amount")
        configured_currency = get_settings().paynow_settlement_currency.strip().upper()
        currency = form_map.get("currency", "").strip().upper() or configured_currency
        if configured_currency and currency != configured_currency:
            raise HTTPException(
                status_code=409,
                detail="Paynow callback currency does not match merchant configuration",
            )
        provider, merchant_reference = "paynow", order_id

    if not order_id or not status:
        raise HTTPException(status_code=422, detail="order_id and status required")

    if not _is_paid_status(status):
        order, changed = record_unpaid_status(get_supabase(), order_id, status)
        return {
            "ok": True,
            "order_id": order_id,
            "status": order["status"],
            "receipt": False,
            "ignored": not changed,
        }

    if is_form and not currency:
        raise HTTPException(
            status_code=503,
            detail="PAYNOW_SETTLEMENT_CURRENCY required when the signed callback omits currency",
        )
    proof = PaymentProof.parse(provider, amount, currency, merchant_reference, reference)
    order, claimed = _mark_paid(order_id, reference, proof)
    if claimed:
        background_tasks.add_task(_deliver_receipt, order)
    return {"ok": True, "order_id": order_id, "status": order["status"], "receipt": claimed}
