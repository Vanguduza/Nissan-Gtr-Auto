"""POST /api/v1/payments/callback — Paynow / PSP server-to-server settle."""

from __future__ import annotations

import logging
from typing import Any

from fastapi import APIRouter, BackgroundTasks, HTTPException, Request
from pydantic import BaseModel, Field

from app.core.supabase_client import get_supabase
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


@router.post("/callback")
async def payment_callback(
    request: Request,
    background_tasks: BackgroundTasks,
) -> dict[str, Any]:
    """Accept JSON or Paynow-style form body; on PAID update order + send PDF receipt."""
    content_type = (request.headers.get("content-type") or "").lower()
    order_id: str | None = None
    status: str | None = None
    reference: str | None = None

    if "application/json" in content_type:
        body = PaymentCallbackJson.model_validate(await request.json())
        order_id, status, reference = body.order_id, body.status, body.reference
    else:
        form = await request.form()
        # Paynow uses reference / paynowreference / status / etc.
        order_id = str(
            form.get("order_id")
            or form.get("reference")
            or form.get("MerchantReference")
            or "",
        ).strip() or None
        status = str(form.get("status") or form.get("Status") or "").strip() or None
        reference = str(
            form.get("paynowreference")
            or form.get("PaynowReference")
            or form.get("reference")
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
