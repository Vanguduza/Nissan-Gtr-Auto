"""EcoCash direct C2B — initiate push, lookup poll, webhook settle."""

from __future__ import annotations

import logging
from typing import Any

from fastapi import APIRouter, BackgroundTasks, Header, HTTPException, Request
from pydantic import BaseModel, Field

from app.core.config import get_settings
from app.core.supabase_client import get_supabase
from app.services.ecocash_client import (
    EcoCashClient,
    EcoCashError,
    ecocash_status_is_paid,
)
from app.services.meta_client import MetaClient
from app.services.receipt_pdf import build_receipt_pdf

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/payments/ecocash", tags=["ecocash"])


class EcoCashPushBody(BaseModel):
    order_id: str
    phone: str | None = Field(
        None,
        description="Override MSISDN; default order.wa_id",
    )


class EcoCashLookupBody(BaseModel):
    order_id: str


async def _deliver_receipt(order: dict[str, Any]) -> None:
    wa_id = order.get("wa_id")
    if not wa_id:
        logger.warning("order %s PAID but no wa_id — skip WhatsApp receipt", order.get("id"))
        return
    pdf = build_receipt_pdf(order)
    await MetaClient().send_receipt_message(
        str(wa_id),
        order_id=str(order["id"]),
        pdf_bytes=pdf,
    )


def _get_order(order_id: str) -> dict[str, Any]:
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
    return existing.data[0]


def _mark_paid(order_id: str, reference: str | None) -> dict[str, Any]:
    order = _get_order(order_id)
    if order.get("status") == "PAID":
        return order
    updated = (
        get_supabase()
        .table("whatsapp_flow_orders")
        .update({"status": "PAID", "payment_reference": reference})
        .eq("id", order_id)
        .execute()
    )
    return (updated.data or [order])[0]


async def push_ecocash_for_order(
    order: dict[str, Any],
    *,
    phone_override: str | None = None,
) -> dict[str, Any]:
    """Initiate EcoCash C2B for a PENDING order; notify customer on WhatsApp."""
    payer = (
        phone_override
        or order.get("ecocash_payer_msisdn")
        or order.get("wa_id")
    )
    wa_notify = order.get("wa_id") or payer
    if not payer:
        raise HTTPException(
            status_code=422,
            detail="EcoCash needs payer MSISDN (ecocash_payer_msisdn or override)",
        )
    source_ref = order.get("payment_source_reference") or str(order["id"])
    client = EcoCashClient()
    try:
        result = await client.initiate_c2b(
            customer_phone=str(payer),
            amount=float(order["total"]),
            currency=str(order.get("currency") or "USD"),
            reason=f"GTR order {str(order['id'])[:8]}",
            source_reference=str(source_ref),
        )
    except EcoCashError as exc:
        logger.exception("EcoCash C2B failed for %s", order.get("id"))
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    try:
        from app.services.ecocash_client import normalize_ecocash_msisdn

        normalized = normalize_ecocash_msisdn(str(payer))
    except EcoCashError:
        normalized = str(payer)

    get_supabase().table("whatsapp_flow_orders").update(
        {
            "payment_provider": "ecocash",
            "payment_source_reference": result.source_reference,
            "payment_reference": result.provider_reference,
            "ecocash_payer_msisdn": normalized,
        },
    ).eq("id", order["id"]).execute()

    amount_label = f"{order.get('currency', 'USD')} {float(order['total']):.2f}"
    stub_note = " (sandbox/stub — set ECOCASH_API_KEY for live)" if result.stub else ""
    same = (
        wa_notify
        and normalized
        and "".join(c for c in str(wa_notify) if c.isdigit())[-9:]
        == normalized[-9:]
    )
    pin_hint = (
        "Open EcoCash on this phone and enter your PIN to approve."
        if same
        else (
            f"Approve the EcoCash PIN on {normalized[:5]}****{normalized[-2:]} "
            "(the EcoCash number you chose — may not be this WhatsApp phone)."
        )
    )
    text = (
        f"Order {str(order['id'])[:8]}… — {amount_label}\n"
        f"EcoCash payment request sent{stub_note}.\n"
        f"{pin_hint}"
    )
    try:
        await MetaClient().send_text(str(wa_notify), text)
    except Exception:  # noqa: BLE001
        logger.exception("WhatsApp EcoCash prompt failed for %s", order.get("id"))

    return {
        "ok": True,
        "order_id": order["id"],
        "payer_msisdn": normalized,
        "source_reference": result.source_reference,
        "provider_reference": result.provider_reference,
        "status": result.status,
        "stub": result.stub,
    }


@router.post("/push")
async def ecocash_push(body: EcoCashPushBody) -> dict[str, Any]:
    """Manual / retry EcoCash C2B push for an order."""
    order = _get_order(body.order_id)
    if order.get("status") == "PAID":
        return {"ok": True, "order_id": body.order_id, "status": "PAID", "skipped": True}
    return await push_ecocash_for_order(order, phone_override=body.phone)


@router.post("/lookup")
async def ecocash_lookup(
    body: EcoCashLookupBody,
    background_tasks: BackgroundTasks,
) -> dict[str, Any]:
    """Poll EcoCash transaction status; mark PAID + send PDF receipt when successful."""
    order = _get_order(body.order_id)
    if order.get("status") == "PAID":
        return {"ok": True, "order_id": body.order_id, "status": "PAID", "receipt": False}

    phone = order.get("ecocash_payer_msisdn") or order.get("wa_id")
    source_ref = order.get("payment_source_reference")
    if not phone or not source_ref:
        raise HTTPException(
            status_code=422,
            detail="order missing ecocash_payer_msisdn/wa_id or source reference",
        )

    client = EcoCashClient()
    try:
        raw = await client.lookup_transaction(
            source_mobile_number=str(phone),
            source_reference=str(source_ref),
        )
    except EcoCashError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    status = str(
        raw.get("transactionStatus") or raw.get("status") or raw.get("message") or "",
    )
    if not ecocash_status_is_paid(status, raw):
        return {
            "ok": True,
            "order_id": body.order_id,
            "status": status or "PENDING",
            "paid": False,
            "raw": raw,
        }

    ref = str(
        raw.get("ecocashReference")
        or raw.get("transactionReference")
        or raw.get("reference")
        or order.get("payment_reference")
        or source_ref,
    )
    paid = _mark_paid(body.order_id, ref)
    background_tasks.add_task(_deliver_receipt, paid)
    return {"ok": True, "order_id": body.order_id, "status": "PAID", "receipt": True, "raw": raw}


@router.post("/callback")
async def ecocash_callback(
    request: Request,
    background_tasks: BackgroundTasks,
    x_ecocash_signature: str | None = Header(default=None, alias="X-EcoCash-Signature"),
) -> dict[str, Any]:
    """Server-to-server EcoCash settle (shape may vary — map common fields).

    Plug-in: set ECOCASH_WEBHOOK_SECRET when EcoCash documents a signature header.
    Until then, accept JSON with sourceReference / order id + success status.
    """
    settings = get_settings()
    raw_body = await request.body()
    if settings.ecocash_webhook_secret:
        # Fail closed when secret configured but header missing/wrong (exact algo TBD by EcoCash).
        if not x_ecocash_signature or x_ecocash_signature != settings.ecocash_webhook_secret:
            raise HTTPException(status_code=401, detail="invalid EcoCash webhook signature")

    try:
        body = await request.json()
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=422, detail="JSON body required") from exc
    if not isinstance(body, dict):
        raise HTTPException(status_code=422, detail="JSON object required")

    source_ref = str(
        body.get("sourceReference")
        or body.get("source_reference")
        or body.get("merchantReference")
        or "",
    ).strip()
    order_id = str(body.get("order_id") or body.get("orderId") or "").strip()
    status = str(
        body.get("transactionStatus") or body.get("status") or body.get("paymentStatus") or "",
    )

    sb = get_supabase()
    order: dict[str, Any] | None = None
    if order_id:
        rows = sb.table("whatsapp_flow_orders").select("*").eq("id", order_id).limit(1).execute()
        order = rows.data[0] if rows.data else None
    elif source_ref:
        rows = (
            sb.table("whatsapp_flow_orders")
            .select("*")
            .eq("payment_source_reference", source_ref)
            .limit(1)
            .execute()
        )
        order = rows.data[0] if rows.data else None

    if not order:
        raise HTTPException(status_code=404, detail="order not found for EcoCash callback")

    if not ecocash_status_is_paid(status, body):
        mapped = status.strip().upper() if status else "PENDING"
        if mapped not in {"PENDING", "FAILED", "CANCELLED", "FULFILLING", "COMPLETED"}:
            mapped = "FAILED"
        sb.table("whatsapp_flow_orders").update(
            {
                "status": mapped,
                "payment_reference": str(
                    body.get("ecocashReference")
                    or body.get("transactionReference")
                    or body.get("reference")
                    or "",
                )
                or None,
            },
        ).eq("id", order["id"]).execute()
        return {"ok": True, "order_id": order["id"], "status": mapped, "receipt": False}

    ref = str(
        body.get("ecocashReference")
        or body.get("transactionReference")
        or body.get("reference")
        or source_ref
        or order.get("payment_reference"),
    )
    paid = _mark_paid(str(order["id"]), ref)
    background_tasks.add_task(_deliver_receipt, paid)
    # raw_body kept for future HMAC verify once EcoCash publishes the algorithm
    _ = raw_body
    return {"ok": True, "order_id": paid["id"], "status": "PAID", "receipt": True}
