"""Cart pricing for WhatsApp Flow — USD catalog prices + delivery fees (no ZIMRA tax)."""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from typing import Any, Literal

from app.core.config import Settings, get_settings
from app.core.supabase_client import get_supabase

DeliveryMethod = Literal["counter_collect", "harare", "nationwide"]
PaymentProvider = Literal["paynow", "contipay", "ecocash", "stub"]


@dataclass
class CartLine:
    oem: str
    description: str
    unit_price: float
    currency: str
    qty: int = 1
    stock_item_id: str | None = None


@dataclass
class CartQuote:
    lines: list[CartLine] = field(default_factory=list)
    delivery_method: DeliveryMethod = "counter_collect"
    delivery_fee: float = 0.0
    currency: str = "USD"
    missing_oems: list[str] = field(default_factory=list)

    @property
    def subtotal(self) -> float:
        return round(sum(l.unit_price * l.qty for l in self.lines), 2)

    @property
    def total(self) -> float:
        return round(self.subtotal + self.delivery_fee, 2)

    def summary_text(self) -> str:
        if not self.lines and not self.missing_oems:
            return "Cart is empty."
        rows: list[str] = []
        for line in self.lines:
            rows.append(
                f"{line.qty}x {line.description or line.oem} — "
                f"{line.currency} {line.unit_price:.2f}",
            )
        if self.missing_oems:
            rows.append("Unavailable: " + ", ".join(self.missing_oems))
        rows.append(f"Subtotal: {self.currency} {self.subtotal:.2f}")
        rows.append(
            f"Delivery ({self.delivery_method}): {self.currency} {self.delivery_fee:.2f}",
        )
        rows.append(f"Total: {self.currency} {self.total:.2f}")
        # No statutory tax lines — invoices remain tax-agnostic (no ZIMRA).
        return "\n".join(rows)


def _delivery_fee(method: DeliveryMethod, settings: Settings) -> float:
    if method == "harare":
        return float(settings.delivery_fee_harare)
    if method == "nationwide":
        return float(settings.delivery_fee_nationwide)
    return float(settings.delivery_fee_counter_collect)


def calculate_cart(
    part_ids: list[str],
    *,
    delivery_method: DeliveryMethod = "counter_collect",
    settings: Settings | None = None,
) -> CartQuote:
    """Resolve OEMs against retail price list + stock_items."""
    settings = settings or get_settings()
    oems = [p.strip().upper() for p in part_ids if p and str(p).strip()]
    quote = CartQuote(
        delivery_method=delivery_method,
        delivery_fee=_delivery_fee(delivery_method, settings),
        currency=settings.default_currency,
    )
    if not oems:
        return quote

    sb = get_supabase()
    # Resolve default RETAIL price list
    pl = (
        sb.table("price_lists")
        .select("id,code")
        .eq("code", settings.retail_price_list_code)
        .limit(1)
        .execute()
    )
    price_list_id = pl.data[0]["id"] if pl.data else None

    items = (
        sb.table("stock_items")
        .select("id,oem_part_number,description")
        .in_("oem_part_number", oems)
        .execute()
    )
    by_oem: dict[str, dict[str, Any]] = {
        str(r["oem_part_number"]).upper(): r for r in (items.data or [])
    }

    prices_by_stock: dict[str, float] = {}
    if price_list_id and by_oem:
        stock_ids = [r["id"] for r in by_oem.values()]
        pli = (
            sb.table("price_list_items")
            .select("stock_item_id,unit_price")
            .eq("price_list_id", price_list_id)
            .in_("stock_item_id", stock_ids)
            .execute()
        )
        for row in pli.data or []:
            sid = str(row["stock_item_id"])
            prices_by_stock[sid] = float(row["unit_price"])

    for oem in oems:
        row = by_oem.get(oem)
        if not row:
            quote.missing_oems.append(oem)
            continue
        sid = str(row["id"])
        price = prices_by_stock.get(sid)
        if price is None:
            quote.missing_oems.append(oem)
            continue
        quote.lines.append(
            CartLine(
                oem=oem,
                description=str(row.get("description") or oem),
                unit_price=price,
                currency=quote.currency,
                stock_item_id=sid,
            ),
        )
    return quote


def generate_payment_link(
    order_id: str,
    amount: float,
    currency: str,
    *,
    provider: PaymentProvider = "paynow",
) -> str:
    """Hosted checkout URL for aggregator rails (Paynow/ContiPay). EcoCash uses C2B push instead."""
    settings = get_settings()
    base = settings.public_base_url.rstrip("/")
    path = settings.site_checkout_path
    stub = "1" if settings.allow_payment_stub else "0"
    return (
        f"{base}{path}?order_id={order_id}"
        f"&amount={amount:.2f}&currency={currency}&psp={provider}&stub={stub}"
    )


def create_pending_order(
    *,
    quote: CartQuote,
    wa_id: str | None,
    delivery_method: DeliveryMethod,
    delivery_notes: str | None = None,
    payment_provider: PaymentProvider | None = None,
    ecocash_payer_mode: str | None = None,
    ecocash_payer_msisdn: str | None = None,
) -> dict[str, Any]:
    """Persist WhatsApp Flow order as PENDING in Supabase."""
    settings = get_settings()
    order_id = str(uuid.uuid4())
    sb = get_supabase()
    quote.delivery_method = delivery_method
    quote.delivery_fee = _delivery_fee(delivery_method, settings)
    provider_raw = (payment_provider or settings.default_whatsapp_payment_provider or "paynow").lower()
    provider: PaymentProvider
    if provider_raw in ("paynow", "contipay", "ecocash", "stub"):
        provider = provider_raw  # type: ignore[assignment]
    else:
        provider = "paynow"

    link = (
        None
        if provider == "ecocash"
        else generate_payment_link(
            order_id,
            quote.total,
            quote.currency,
            provider=provider,
        )
    )
    source_ref = str(uuid.uuid4()) if provider == "ecocash" else None
    payload = {
        "id": order_id,
        "status": "PENDING",
        "currency": quote.currency,
        "subtotal": quote.subtotal,
        "delivery_fee": quote.delivery_fee,
        "total": quote.total,
        "delivery_method": delivery_method,
        "delivery_notes": delivery_notes,
        "wa_id": wa_id,
        "lines": [
            {
                "oem": l.oem,
                "description": l.description,
                "unit_price": l.unit_price,
                "qty": l.qty,
                "stock_item_id": l.stock_item_id,
            }
            for l in quote.lines
        ],
        "payment_link": link,
        "payment_provider": provider,
        "payment_source_reference": source_ref,
        "ecocash_payer_mode": ecocash_payer_mode,
        "ecocash_payer_msisdn": ecocash_payer_msisdn,
        "channel": "whatsapp_flow",
    }

    sb.table("whatsapp_flow_orders").insert(payload).execute()
    return payload

def attach_payment_reference(
    order_id: str,
    *,
    payment_reference: str | None,
    payment_source_reference: str | None = None,
) -> None:
    sb = get_supabase()
    patch: dict[str, Any] = {}
    if payment_reference is not None:
        patch["payment_reference"] = payment_reference
    if payment_source_reference is not None:
        patch["payment_source_reference"] = payment_source_reference
    if patch:
        sb.table("whatsapp_flow_orders").update(patch).eq("id", order_id).execute()
