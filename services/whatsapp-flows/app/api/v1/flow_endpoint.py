"""POST /api/v1/whatsapp/flow — Meta Flow encrypted data exchange."""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

from fastapi import APIRouter, BackgroundTasks, HTTPException, Response
from pydantic import BaseModel

from app.core.config import get_settings
from app.core.flow_crypto import FlowCrypto, FlowCryptoError
from app.services.cart_service import (
    DeliveryMethod,
    PaymentProvider,
    calculate_cart,
    create_pending_order,
)
from app.services.meta_client import MetaClient
from app.api.v1.ecocash_webhook import push_ecocash_for_order
from app.core.supabase_client import get_supabase

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/whatsapp", tags=["whatsapp-flow"])


class EncryptedFlowBody(BaseModel):
    encrypted_aes_key: str
    encrypted_flow_data: str
    initial_vector: str


def _crypto() -> FlowCrypto:
    settings = get_settings()
    path = Path(settings.flow_private_key_path)
    if not path.is_file():
        raise HTTPException(
            status_code=503,
            detail=f"Flow private key missing at {path}",
        )
    return FlowCrypto.from_pem_path(path)


def _screen(name: str, data: dict[str, Any] | None = None) -> dict[str, Any]:
    out: dict[str, Any] = {"screen": name}
    if data is not None:
        out["data"] = data
    return out


async def _send_payment_cta_bg(
    wa_id: str,
    payment_url: str,
    order_id: str,
    amount_label: str,
) -> None:
    client = MetaClient()
    try:
        await client.send_payment_cta(
            wa_id,
            payment_url=payment_url,
            order_id=order_id,
            amount_label=amount_label,
        )
    except Exception:  # noqa: BLE001
        logger.exception("send_payment_cta failed for order %s", order_id)


async def _send_ecocash_push_bg(order_id: str) -> None:
    try:
        rows = (
            get_supabase()
            .table("whatsapp_flow_orders")
            .select("*")
            .eq("id", order_id)
            .limit(1)
            .execute()
        )
        if not rows.data:
            logger.error("EcoCash push: order %s missing", order_id)
            return
        await push_ecocash_for_order(rows.data[0])
    except Exception:  # noqa: BLE001
        logger.exception("EcoCash push failed for order %s", order_id)


def handle_flow_action(decrypted: dict[str, Any]) -> dict[str, Any]:
    """State machine for Flow data_exchange actions."""
    raw_action = str(decrypted.get("action") or "").strip()
    data = decrypted.get("data") if isinstance(decrypted.get("data"), dict) else {}
    screen = decrypted.get("screen")

    # Meta sends action=data_exchange; custom intent lives in data.action or screen.
    if raw_action == "data_exchange":
        nested = str(data.get("action") or "").strip()
        if nested:
            action = nested
        elif screen == "PARTS_SELECT":
            action = "calculate_cart"
        elif screen == "CHECKOUT":
            action = "checkout"
        else:
            action = "data_exchange"
    else:
        action = raw_action

    # Health / ping from Meta
    if action == "ping":
        return {"data": {"status": "active"}}

    if action in ("INIT", "INIT_NEXT"):
        # Pass through to VIN / diagram entry screen in the Flow JSON
        return _screen(
            "VIN_SEARCH",
            {
                "welcome_text": "Enter your VIN or continue to diagram parts.",
            },
        )

    if action == "calculate_cart":
        part_ids = data.get("part_ids") or data.get("selected_parts") or []
        if isinstance(part_ids, str):
            part_ids = [p.strip() for p in part_ids.split(",") if p.strip()]
        if not isinstance(part_ids, list):
            raise HTTPException(status_code=422, detail="part_ids must be an array")
        delivery_raw = str(data.get("delivery_method") or "counter_collect")
        delivery: DeliveryMethod
        if delivery_raw in ("harare", "nationwide", "counter_collect"):
            delivery = delivery_raw  # type: ignore[assignment]
        else:
            delivery = "counter_collect"
        quote = calculate_cart([str(p) for p in part_ids], delivery_method=delivery)
        return _screen(
            "CART_REVIEW",
            {
                "cart_summary_text": quote.summary_text(),
                "cart_total": quote.total,
                "currency": quote.currency,
                "part_ids": [l.oem for l in quote.lines],
                "delivery_method": delivery,
            },
        )

    if action == "checkout":
        part_ids = data.get("part_ids") or data.get("selected_parts") or []
        if isinstance(part_ids, str):
            part_ids = [p.strip() for p in part_ids.split(",") if p.strip()]
        delivery_raw = str(data.get("delivery_method") or "counter_collect")
        delivery: DeliveryMethod = (
            delivery_raw  # type: ignore[assignment]
            if delivery_raw in ("harare", "nationwide", "counter_collect")
            else "counter_collect"
        )
        quote = calculate_cart([str(p) for p in part_ids], delivery_method=delivery)
        if not quote.lines:
            return _screen(
                "CART_REVIEW",
                {
                    "cart_summary_text": quote.summary_text(),
                    "error_message": "No priced parts in cart.",
                },
            )
        wa_id = (
            data.get("wa_id")
            or data.get("user_id")
            or data.get("phone")
            or decrypted.get("wa_id")
        )
        # Prefer embedding customer MSISDN in flow_token when opening the Flow.
        flow_token = decrypted.get("flow_token")
        if not wa_id and isinstance(flow_token, str) and flow_token.isdigit():
            wa_id = flow_token

        settings = get_settings()
        pay_raw = str(
            data.get("payment_method")
            or data.get("payment_provider")
            or settings.default_whatsapp_payment_provider
            or "ecocash",
        ).lower()
        payment_provider: PaymentProvider = (
            pay_raw  # type: ignore[assignment]
            if pay_raw in ("paynow", "contipay", "ecocash", "stub")
            else "ecocash"
        )

        payer_mode: str | None = None
        payer_msisdn: str | None = None
        if payment_provider == "ecocash":
            from app.services.ecocash_client import EcoCashError
            from app.services.ecocash_payer import resolve_ecocash_payer

            try:
                payer_mode, payer_msisdn = resolve_ecocash_payer(
                    mode=str(data.get("ecocash_payer_mode") or ""),
                    wa_id=str(wa_id) if wa_id else None,
                    entered_msisdn=str(data.get("ecocash_msisdn") or "") or None,
                )
            except (ValueError, EcoCashError) as exc:
                return _screen(
                    "ECOCASH_PAYER",
                    {
                        "part_ids": [str(p) for p in part_ids],
                        "delivery_method": delivery,
                        "delivery_notes": str(data.get("delivery_notes") or ""),
                        "payment_method": "ecocash",
                        "error_message": str(exc),
                        "hint_text": (
                            "Use this WhatsApp number for speed, or enter the EcoCash "
                            "number that will approve the PIN."
                        ),
                    },
                )

        order = create_pending_order(
            quote=quote,
            wa_id=str(wa_id) if wa_id else None,
            delivery_method=delivery,
            delivery_notes=str(data.get("delivery_notes") or "") or None,
            payment_provider=payment_provider,
            ecocash_payer_mode=payer_mode,
            ecocash_payer_msisdn=payer_msisdn,
        )
        amount_label = f"{order['currency']} {float(order['total']):.2f}"
        side: dict[str, Any]
        if payment_provider == "ecocash":
            side = {
                "send_ecocash_push": True,
                "order_id": order["id"],
                "wa_id": order.get("wa_id"),
            }
        else:
            side = {
                "send_payment_cta": True,
                "order_id": order["id"],
                "payment_link": order.get("payment_link") or "",
                "amount_label": amount_label,
                "wa_id": order.get("wa_id"),
            }
        # Meta SUCCESS closes the Flow; payment follow-up is sent as a WhatsApp message.
        return {
            "screen": "SUCCESS",
            "data": {
                "extension_message_response": {
                    "params": {
                        "flow_token": str(order["id"]),
                        "order_id": order["id"],
                        "status": "PENDING_PAYMENT",
                        "payment_provider": payment_provider,
                    },
                },
            },
            "_gtr_side_effects": side,
        }

    # Unknown action — bounce to current screen if provided
    if isinstance(screen, str) and screen:
        return _screen(screen, {"error_message": f"Unknown action: {action}"})
    return _screen("VIN_SEARCH", {"error_message": f"Unknown action: {action}"})


@router.post("/flow")
async def whatsapp_flow_exchange(
    body: EncryptedFlowBody,
    background_tasks: BackgroundTasks,
) -> Response:
    try:
        crypto = _crypto()
        decrypted, aes_key, iv = crypto.decrypt_request(
            encrypted_aes_key_b64=body.encrypted_aes_key,
            encrypted_flow_data_b64=body.encrypted_flow_data,
            initial_vector_b64=body.initial_vector,
        )
    except FlowCryptoError as exc:
        logger.warning("flow decrypt failed: %s", exc)
        raise HTTPException(status_code=421, detail="decryption failed") from exc
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        logger.exception("flow crypto init failed")
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    try:
        response_obj = handle_flow_action(decrypted)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        logger.exception("flow action failed")
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    side = response_obj.pop("_gtr_side_effects", None)
    if isinstance(side, dict):
        if side.get("send_ecocash_push") and side.get("order_id"):
            background_tasks.add_task(_send_ecocash_push_bg, str(side["order_id"]))
        elif side.get("send_payment_cta") and side.get("wa_id") and side.get("payment_link"):
            background_tasks.add_task(
                _send_payment_cta_bg,
                str(side["wa_id"]),
                str(side["payment_link"]),
                str(side["order_id"]),
                str(side["amount_label"]),
            )

    try:
        encrypted = crypto.encrypt_response(response_obj, aes_key=aes_key, request_iv=iv)
    except FlowCryptoError as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    # Meta expects raw base64 string as body
    return Response(content=encrypted, media_type="text/plain")


@router.get("/flow/health")
async def flow_health() -> dict[str, str]:
    return {"status": "ok", "service": "whatsapp-flows"}
