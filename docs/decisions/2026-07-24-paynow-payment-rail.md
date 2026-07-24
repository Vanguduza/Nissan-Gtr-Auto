# Paynow as accepted payment rail (alongside ContiPay)

- Date: 2026-07-24
- Lane: `@backend_agent` (schema/edge); `@web_agent` (storefront tender choice); Phase 13 ownership
- Status: accepted

## Decision

**Paynow** is an accepted customer payment rail for Nissan GTR Auto ERP, **in addition to ContiPay** (not a replacement). Both rails settle into Payment Entry with tender `paynow` | `contipay`, explicit `USD` | `ZIG`, and `exchange_rate_applied` stored at transaction time.

| Rail | Typical channels (provider-supported) | Tender enum | Phase |
|------|----------------------------------------|-------------|-------|
| ContiPay | EcoCash, Visa 3DS, ZimSwitch | `contipay` | 13 |
| Paynow | ZW mobile money (EcoCash / OneMoney / InnBucks) and card as Paynow supports | `paynow` | 13 |

Secrets (`PAYNOW_INTEGRATION_ID`, `PAYNOW_INTEGRATION_KEY`, webhook/hash verify material) live **only** in Edge Function env — never in client apps, migrations, or this decision. Domain for return/result URLs: `nissangtrauto.co.zw` ([company-domain](./2026-07-23-company-domain.md)).

Receipts remain tax-agnostic documents: **no ZIMRA**, no FDMS, no fiscalisation QR, no tax-authority payloads.

## Why

Operators and customers in Zimbabwe commonly use more than one PSP. ContiPay is already the Phase 13 primary digital rail; Paynow is widely used for mobile money and card and must appear as a first-class selectable method without inventing a second Payment Entry model.

## Consequences

- Do not remove or subsume ContiPay when wiring Paynow.
- Schema: `payment_tender` includes `paynow`; `paynow_payment_intents` + webhook idempotency mirror ContiPay patterns; settle posts append-only Payment Entry / JE (corrections via reverse only).
- Edge stubs: `paynow-initiate`, `paynow-webhook` — real Paynow API hash/status polling in a follow-on Phase 13 slice once merchant keys exist.
- Agents must not add ZIMRA/fiscal fields to Paynow settle or customer receipts.
- Storefront/POS show ContiPay **and** Paynow as options when digital tender UI is present.
