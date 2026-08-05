# EcoCash direct C2B alongside Paynow/ContiPay

- Date: 2026-08-03
- Lane: WhatsApp Flows FastAPI satellite (`services/whatsapp-flows/`); SoR remains Supabase; cross-platform Edge follow-on `@backend_agent`
- Status: accepted (WhatsApp); **cross-platform extension planned** — see [`docs/plans/2026-08-03-ecocash-direct-c2b-cross-platform.md`](../plans/2026-08-03-ecocash-direct-c2b-cross-platform.md)
- Related: [`paynow-payment-rail`](./2026-07-24-paynow-payment-rail.md), [`customer-self-pay`](./2026-07-24-customer-self-pay.md)

## Decision

**EcoCash Instant Payments (merchant HTTP C2B)** is an accepted **direct** rail for WhatsApp Flow checkout, in addition to Paynow and ContiPay aggregators. Customer authorises on-handset (PIN); the backend does not dial USSD.

**Follow-on (landed 2026-08-03):** EcoCash direct is a **cross-platform** tender (`payment_tender = ecocash`) with `ecocash_payment_intents`, Edge `ecocash-initiate` / `ecocash-webhook`, `create_customer_ecocash_intent`, and client options on web storefront, iOS/Android customer Pay, Android POS, and WhatsApp Flow. ContiPay and Paynow remain available; EcoCash direct does **not** route through them. Payer MSISDN is always explicit (saved/profile vs other / POS-entered).

| Rail | Role |
|------|------|
| EcoCash direct | Default WhatsApp Flow tender when `payment_method=ecocash` / `DEFAULT_WHATSAPP_PAYMENT_PROVIDER=ecocash`; later first-class tender on web/POS/mobile |
| Paynow | Aggregator CTA (EcoCash + OneMoney + InnBucks + card) |
| ContiPay | Existing Edge storefront/POS rail (unchanged) |

Secrets: `ECOCASH_API_KEY` (and optional `ECOCASH_WEBHOOK_SECRET`) only in service / Edge env — never client apps or migrations.

## Why

Operators asked for a plug-in path after EcoCash online-merchant registration without requiring Paynow for EcoCash-only WhatsApp sales. Aggregators remain for multi-wallet checkout. Cross-platform extension avoids a second Payment Entry model and fixes the requirement that the EcoCash wallet number need not match the WhatsApp number.

## Consequences

- `whatsapp_flow_orders.payment_provider` / `payment_source_reference` columns
- Endpoints (current): `/api/v1/payments/ecocash/{push,lookup,callback}` on the FastAPI satellite
- Path/auth env overrides so portal API shape can be adjusted without code changes
- No ZIMRA / fiscal fields on EcoCash settle or receipts
- Agents must not treat EcoCash direct as WhatsApp-only forever — implement cross-platform via the dated plan (Edge + intents SoR); do not invent a parallel settle ledger
- Do not remove ContiPay or Paynow when wiring EcoCash direct on other surfaces
