# WhatsApp commerce audit — Batch 1 §1.7

- Date: **2026-08-03**
- Status: **audit only** (no greenfield shop rebuild)
- Surfaces: `supabase/functions/whatsapp-webhook`, `services/whatsapp-flows/`

## Checklist vs brief

| Requirement | Finding |
|-------------|---------|
| Catalog browse via interactive list/buttons | **Partial** — parts-finder / catalog search in webhook; full Flow cart JSON in `services/whatsapp-flows/flow/sample_cart_flow.json` |
| Cart on same `create_customer_cart` / `add_customer_cart_line` RPCs | **Flows path** uses FastAPI cart service + order insert; confirm alignment with shared customer cart RPCs before treating as SoR parity |
| Checkout / payment | **Present** — Meta payment CTA + Paynow-style webhook; **EcoCash direct C2B** reachable (`/api/v1/payments/ecocash/*`) |
| `fulfillment_method: delivery \| pickup` | **Gap** — capture in Flow/cart vs web/Android/iOS `fulfillment_mode` needs explicit parity pass |
| Receipt via multi-channel outbox | **Partial** — PDF receipt via Meta client; wire to `process-customer-receipts` / outbox where not already shared |

## Gaps to close (later phases — do not invent a second cart SoR)

1. Prefer shared customer cart RPCs from WhatsApp adapters over a forked order table if still diverged.
2. Add explicit fulfillment choice matching web/mobile.
3. Route status/receipt through existing receipt outbox where possible.

## Hard exclusions confirmed

- No ZIMRA / fiscal QR in WhatsApp receipts.
- EcoCash is direct C2B, not ContiPay/Paynow-derived.
