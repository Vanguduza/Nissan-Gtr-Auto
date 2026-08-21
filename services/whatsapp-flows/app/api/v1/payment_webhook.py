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
from typing import Any
from urllib.parse import parse_qs

from fastapi import APIRouter, BackgroundTasks, HTTPException, Request
from pydantic import BaseModel, Field

from app.core.config import get_settings
from app.core.supabase_client import get_supabase
from app.core.webhook_auth import (
    assert_shared_secret,
    header_secret,
    verify_paynow_form_hash,
)
from app.services.meta_client import MetaClient
from app.services.receipt_pdf import build_receipt_pdf

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/payments", tags=["payments"])


class PaymentCallbackJson(BaseModel):
    """JSON callback shape (also accept form-urlencoded Paynow in raw handler)."""

    order_id: str = Field(..., description="whatsapp_flow_orders.id")
    status: str = Field(..., description="PAID | FAILED | PENDING | …")
    reference: str | None = None
    amount: float | None = None
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


def _mark_paid(order_id: str, reference: str | None) -> dict[str, Any]:
    sb = get_supabase()
    existing = (
        sb.table("whatsapp_flow_orders")
        .select("*")
        .eq("id", order_id)
        .limit(1)
        .execute()
    )
    if not existing.data:
        raise HTTPException(status_code=404, detail="order not found")
    order = existing.data[0]
    if order.get("status") == "PAID":
        return order  # idempotent
    updated = (
        sb.table("whatsapp_flow_orders")
        .update(
            {
                "status": "PAID",
                "payment_reference": reference,
            },
        )
        .eq("id", order_id)
        .execute()
    )
    return (updated.data or [order])[0]


def _is_paid_status(status: str) -> bool:
    s = status.strip().lower()
    return s in {"paid", "ok", "awaiting delivery", "delivered", "success"}


def _authorize_payment_callback(request: Request, raw_body: bytes, is_form: bool) -> None:
    settings = get_settings()
    key = (settings.paynow_integration_key or "").strip()
    secret = (settings.payments_webhook_secret or "").strip()
    local = bool(settings.payments_allow_unverified_local)

    if is_form and key:
        ok, _fields = verify_paynow_form_hash(raw_body.decode("utf-8", errors="replace"), key)
        if ok:
            return
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
    _authorize_payment_callback(request, raw_body, is_form=is_form)

    order_id: str | None = None
    status: str | None = None
    reference: str | None = None

    if not is_form:
        try:
            payload = json.loads(raw_body.decode("utf-8") or "{}")
        except Exception as exc:  # noqa: BLE001
            raise HTTPException(status_code=422, detail="JSON body required") from exc
        body = PaymentCallbackJson.model_validate(payload)
        order_id, status, reference = body.order_id, body.status, body.reference
    else:
        form_map = {
            k: (v[0] if v else "")
            for k, v in parse_qs(
                raw_body.decode("utf-8", errors="replace"),
                keep_blank_values=True,
            ).items()
        }
        # Paynow uses reference / paynowreference / status / etc.
        order_id = str(
            form_map.get("order_id")
            or form_map.get("reference")
            or form_map.get("MerchantReference")
            or "",
        ).strip() or None
        status = str(form_map.get("status") or form_map.get("Status") or "").strip() or None
        reference = str(
            form_map.get("paynowreference")
            or form_map.get("PaynowReference")
            or form_map.get("reference")
            or "",
        ).strip() or None

    if not order_id or not status:
        raise HTTPException(status_code=422, detail="order_id and status required")

    if not _is_paid_status(status):
        mapped = status.strip().upper()
        if mapped not in {"PENDING", "FAILED", "CANCELLED", "FULFILLING", "COMPLETED"}:
            mapped = "FAILED"
        sb = get_supabase()
        sb.table("whatsapp_flow_orders").update(
            {"status": mapped, "payment_reference": reference},
        ).eq("id", order_id).execute()
        return {"ok": True, "order_id": order_id, "status": mapped, "receipt": False}

    order = _mark_paid(order_id, reference)
    background_tasks.add_task(_deliver_receipt, order)
    return {"ok": True, "order_id": order_id, "status": "PAID", "receipt": True}
