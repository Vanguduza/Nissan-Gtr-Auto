# POS receipt contact capture + customer bind

- Date: 2026-07-25
- Lane: `@backend_agent` (checkout RPC + enqueue); `@web_agent` / `@management_app_agent` (checkout fields + messaging)
- Status: accepted

## Decision

Extend **`checkout_pos_cart`** to accept optional **`p_receipt_email`**, **`p_receipt_whatsapp_e164`** (and optional phone). Persist onto `sales_invoices.customer_email` / `customer_whatsapp_e164` / `customer_phone_e164` so Phase 5 **`enqueue_customer_receipts`** and Phase 13 **`process-customer-receipts`** run unchanged ([customer-receipt-delivery](./2026-07-23-customer-receipt-delivery.md)).

**Customer bind:** after normalizing contacts, resolve a **unique** `customers` row where email **or** phone/WhatsApp matches, and the row is a **registered account** (`profile_id IS NOT NULL`) **and/or** trade/company-style list (`price_list` code ≠ `RETAIL`, e.g. B2B/FLEET). If unique → set invoice (and cart if still open) `customer_id`. If zero or ambiguous matches → leave unbound (walk-in); UI may show “no account match” / “multiple matches — link manually”.

**Do not** auto-insert a new `customers` row from checkout contacts in this slice. Sale **must not block** if receipt send fails — enqueue-first; channel drain fail-closed without SMS/email/WhatsApp secrets unless `WORKER_ALLOW_UNVERIFIED_LOCAL=1` (existing Phase 13 behavior).

## Why

Checkout already stores contact columns but only copied from pre-selected customer. Capturing WhatsApp/email at till enables PDF forwarding and attaches the sale to My Garage / B2B accounts when the contact already belongs to a registered customer.

## Consequences

- Prefer extending `checkout_pos_cart` over a parallel checkout RPC.
- Bind is best-effort identity link, not credit approval; credit hold/limit still apply when `customer_id` is set.
- No fiscal/ZIMRA payloads on PDF or SMS.
