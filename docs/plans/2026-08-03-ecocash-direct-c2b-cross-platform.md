# EcoCash direct C2B — cross-platform payment rail

- Status: in progress (Phase A done; Phase B SoR + Edge + client wiring landed 2026-08-03)
- Date: 2026-08-03
- Lane(s): `@backend_agent` (schema / Edge / RLS); WhatsApp Flows satellite (`services/whatsapp-flows/`); `@web_agent` (storefront); `@management_app_agent` (POS); `@ios_agent` / `@android_agent` (customer apps); `@finance_agent` (Payment Entry settle review)
- Skills needed: `/accounting-ledger` (Payment Entry / JE on settle); `/token-discipline`
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 13; [`2026-07-24-phase13-payments-receipts-sms.md`](./2026-07-24-phase13-payments-receipts-sms.md)
- Decisions: [`ecocash-direct-c2b`](../decisions/2026-08-03-ecocash-direct-c2b.md) (extend to cross-platform), [`paynow-payment-rail`](../decisions/2026-07-24-paynow-payment-rail.md), [`customer-self-pay`](../decisions/2026-07-24-customer-self-pay.md)
- Prior: FastAPI EcoCash C2B in `services/whatsapp-flows/` (`ecocash_client`, `/payments/ecocash/{push,lookup,callback}`); ContiPay + Paynow Edge (`contipay-*` / `paynow-*`); `payment_entries` + `*_payment_intents`

## Goal

Make **EcoCash Instant Payments (merchant HTTP C2B)** a first-class, cross-platform tender alongside ContiPay and Paynow: one Supabase intent/settle model, explicit payer MSISDN (WhatsApp number ≠ EcoCash number), and consistent UX on WhatsApp Flow, web storefront, customer mobile, and Android management POS — no ZIMRA / payroll tax.

## Problem / gap

Today EcoCash direct is WhatsApp-scoped ([ADR](../decisions/2026-08-03-ecocash-direct-c2b.md)): push defaults to `whatsapp_flow_orders.wa_id`, settle marks the Flow order `PAID`, and there is no `ecocash_payment_intents` mirror of Phase 13 ContiPay/Paynow. ERP surfaces still use aggregators only for digital tender. Operators need customers to pay from a **different EcoCash MSISDN** than the WhatsApp / account phone, with two fast choices: (1) WhatsApp / default / saved number, (2) enter another EcoCash MSISDN.

## Acceptance criteria

- [ ] Payer MSISDN is always explicit on initiate (normalized `263…`); never silently assume WhatsApp `wa_id` equals EcoCash wallet without a user/staff choice
- [ ] WhatsApp Flow offers **Use this WhatsApp number** vs **Pay with a different EcoCash number** before C2B push; chosen MSISDN persists on the order (and later on the intent)
- [ ] Shared SoR: `ecocash_payment_intents` (+ webhook idempotency) mirrors `paynow_payment_intents` / `contipay_payment_intents`; RLS in same migration; secrets only in server env
- [ ] `payment_tender` gains `ecocash`; settle posts append-only Payment Entry / JE via `mark_ecocash_settled` (corrections via reverse only), with `USD`|`ZIG` + `exchange_rate_applied`
- [ ] Channel settle: invoice-backed rails → Payment Entry; WhatsApp Flow channel ledger → `whatsapp_flow_orders` PAID (+ optional later invoice link — not required in Phase A)
- [ ] One initiate/settle ownership model (see § Ownership): Edge + RPCs are SoR for ERP; WhatsApp satellite adapts Flow UX / Meta receipts without a second merchant-settle truth
- [ ] Web, customer mobile, and POS can initiate EcoCash C2B against unpaid invoices (staff / customer AuthZ parallel to ContiPay/Paynow self-pay)
- [ ] Exclusion grep clean: no ZIMRA / FDMS / fiscal QR / payroll tax; no HTML5/browser QR for payments

## Ownership recommendation (Edge vs FastAPI)

| Concern | System of record | Rationale |
|---------|------------------|-----------|
| Intent row + idempotent settle + Payment Entry | **Supabase** (`ecocash_payment_intents`, `create_ecocash_intent` / `create_customer_ecocash_intent`, `mark_ecocash_settled`) | Same pattern as Phase 13 Paynow/ContiPay ([paynow ADR](../decisions/2026-07-24-paynow-payment-rail.md), Phase 13 plan) |
| Merchant HTTP C2B initiate / lookup / webhook verify (ERP + long-term) | **Edge** `ecocash-initiate`, `ecocash-lookup` (optional poll worker), `ecocash-webhook` | Secrets in Edge env; AuthZ via staff JWT or customer self-pay RPCs; one callback URL for EcoCash portal |
| WhatsApp Flow UX, Meta crypto, WA receipt PDF | **FastAPI satellite** | Already owns Flow `data_exchange` + Meta send; keep as **channel adapter** |
| Duplicate EcoCash API clients long-term | **Avoid** | Phase B: move merchant HTTP into Edge (port `ecocash_client` logic); satellite calls Edge or service_role RPCs + shared client only if Edge invoke is impractical — do **not** leave two independent settle writers |

**Phase A exception:** keep FastAPI push/lookup/callback writing `whatsapp_flow_orders` only (no Payment Entry yet) so number-choice ships immediately. Phase B cutover: callback/lookup must settle intents (and Flow order as side effect), not a parallel paid flag without an intent when ERP SoR exists.

**Rejected:** FastAPI as permanent SoR for all surfaces (POS/web would depend on a Meta-oriented satellite). **Rejected:** Edge-only with no Flow adapter (Meta Flow crypto stays in FastAPI).

## Shared SoR (Supabase) — sketch

Mirror [`20260724095000_paynow_payment_intents.sql`](../../supabase/migrations/20260724095000_paynow_payment_intents.sql) / ContiPay sibling:

| Object | Notes |
|--------|--------|
| `payment_tender` | `ALTER … ADD VALUE 'ecocash'` (distinct from Paynow/ContiPay method enum value `ecocash`) |
| `ecocash_intent_status` | `pending` \| `authorized` \| `settled` \| `failed` \| `cancelled` |
| `ecocash_payment_intents` | `external_ref` UNIQUE (merchant `sourceReference`); `payer_msisdn` NOT NULL (normalized); `amount`, `currency`, `exchange_rate_applied`; optional `settlement_*`; `customer_id`; `sales_invoice_id` (nullable metadata or FK via metadata/allocations); `payment_entry_id`; `provider_ref`; `webhook_payload_hash`; `channel` (`whatsapp_flow` \| `web` \| `pos` \| `ios` \| `android_customer`); `whatsapp_flow_order_id` nullable; `metadata` JSONB; **no API keys** |
| `ecocash_webhook_events` | payload_hash UNIQUE idempotency (same as Paynow) |
| RPCs | `create_ecocash_intent` (staff); `create_customer_ecocash_intent` (customer-owned unpaid invoice — [customer-self-pay](../decisions/2026-07-24-customer-self-pay.md)); `mark_ecocash_settled` (service_role / Edge only) |
| RLS | Staff finance/payments SELECT; customers SELECT own intents; INSERT/UPDATE via SECURITY DEFINER RPCs only; webhook table service-only |
| WhatsApp columns (Phase A) | `whatsapp_flow_orders.ecocash_payer_msisdn` (or reuse metadata); keep `payment_source_reference` |

Settle rules:

1. **Invoice path (Phase B+):** webhook/lookup success → `mark_ecocash_settled` → create+post Payment Entry tender `ecocash` + allocations (idempotent on payload hash) — same guarantees as `mark_paynow_settled`.
2. **WhatsApp Flow path:** mark order `PAID` + receipt; Phase B preferably creates/settles an intent with `channel=whatsapp_flow` even when no sales invoice yet (Payment Entry optional until Flow order is bound to ERP invoice — document as follow-on, do not block Phase A).

## Client UX per surface

### WhatsApp Flow (Phase A primary)

1. CHECKOUT (or new `ECOCASH_PAYER` screen after `payment_method=ecocash`):
   - Radio: **Use this WhatsApp number** (prefill / display masked `wa_id`) vs **Different EcoCash number**.
   - If different: required TextInput MSISDN (local `07…` or `+263…`); server normalizes via existing `normalize_ecocash_msisdn`.
2. Persist chosen MSISDN on order; background push uses **payer MSISDN**, not blindly `wa_id`.
3. PIN prompt SMS/WA copy: send confirm text to **WhatsApp `wa_id`**; EcoCash PIN goes to the **payer handset**.
4. Lookup/callback must use stored payer MSISDN for EcoCash lookup API (fix current bug: lookup still uses `wa_id` only).
5. SUCCESS copy: clarify “approve on the EcoCash phone you chose.”

### Web storefront (Phase C)

- Tender choice: ContiPay | Paynow | **EcoCash direct** (when digital tender UI present — same bar as [paynow ADR](../decisions/2026-07-24-paynow-payment-rail.md)).
- EcoCash: two options — **Saved / profile phone** (`customers.phone_e164` or checkout contact) vs **Other EcoCash number** (inline E.164 field).
- Initiate via `ecocash-initiate` (customer JWT + `sales_invoice_id`); UI polls status or waits for webhook; return pages stay non-settling (same as ContiPay/Paynow return-url discipline in `docs/storefront-psp-return-urls.md`).

### Android / iOS customer (Phase C)

- Same saved-vs-other pattern; invoke Edge; no merchant secrets in apps; no browser QR.

### Android management POS (Phase C)

- Staff selects EcoCash direct on unpaid invoice / cart checkout path.
- **Required field: customer EcoCash phone** (prefill from bound customer / receipt phone if present; always editable).
- Staff JWT → `ecocash-initiate`; show “waiting for PIN” + refresh/lookup; settle is webhook-only for money truth.

## Phased delivery

### Phase A — WhatsApp number choice (immediate)

**Lane:** WhatsApp Flows satellite (+ tiny migration if column added).

- Flow JSON: payer choice UI + payload fields (`ecocash_payer_mode`, `ecocash_msisdn`).
- `push_ecocash_for_order` / lookup: prefer stored payer MSISDN; `phone_override` remains for retry API.
- Persist MSISDN on `whatsapp_flow_orders`; tests for normalize + “other number” path.
- **No** Edge intents yet; settle remains Flow-order PAID.

### Phase B — Shared Edge + intents SoR

**Lane:** `@backend_agent` (+ satellite adapter cutover).

- Migration: tender `ecocash`, intents, webhook events, RPCs, RLS, smoke SQL sibling to Phase 13.
- Edge: `ecocash-initiate`, `ecocash-webhook`, optional `ecocash-lookup`.
- Port / share C2B client behavior from `ecocash_client.py` (Deno port or call-out — prefer Edge-native to match ContiPay/Paynow).
- FastAPI: on EcoCash checkout, create intent (service role) **or** invoke Edge; callback/lookup delegates settle to `mark_ecocash_settled` + order PAID.
- Single EcoCash portal webhook URL → Edge (recommended); FastAPI callback becomes thin proxy or deprecated after cutover.
- `/supabase-rls-auditor` → `/security-reviewer` on webhook + MSISDN PII.

### Phase C — Wire web / POS / mobile

**Lanes:** `@web_agent`, `@management_app_agent`, `@ios_agent`, `@android_agent` (one lane per PR).

- Storefront + POS + customer apps: tender + MSISDN UX; initiate Edge; pending/settled UI.
- Align copy with multi-currency settlement display already expected for ContiPay/Paynow.
- Regenerate `packages/supabase-client` types; shared tender enum in `packages/shared/`.

## Out of scope / non-goals

- ZIMRA / FDMS / fiscalisation / payroll tax
- Replacing ContiPay or Paynow (aggregators remain for multi-wallet / card)
- USSD dial-from-server; EcoCash remains on-handset PIN C2B
- Binding every WhatsApp Flow order to a full `sales_invoices` row in Phase A/B (explicit follow-on)
- Saving multiple EcoCash wallets per customer as a full wallet vault (optional `metadata.last_ecocash_msisdn` only if cheap)
- Browser/WebView HTML5 QR or any non-bridge hardware
- Dual-running Ruflo swarm + Cursor manager on one dirty tree
- Committing live `ECOCASH_API_KEY` / webhook secrets

## Risks

- **Two settle writers** during A→B cutover — mitigate with feature flag / webhook URL cutover checklist
- **MSISDN ≠ WhatsApp:** receipt still to `wa_id`; payment on other phone — UX must not mix “we texted the wrong device”
- **Lookup without stored payer MSISDN** fails or hits wrong wallet — Phase A must fix before Phase C
- **Tender enum collision in product copy:** Paynow method `ecocash` vs tender `ecocash` — UI labels “EcoCash (direct)” vs “Paynow”
- **Idempotency:** same `sourceReference` / payload hash must not double-post Payment Entry (copy Paynow webhook pattern)

## Test plan

### Phase A

1. Checkout EcoCash + “this WhatsApp number” → push uses `wa_id`; order stores payer MSISDN.
2. Checkout EcoCash + other MSISDN (`077…` / `+263…`) → normalized `263…` on order; C2B uses override; WA confirm still to `wa_id`.
3. Lookup after pay uses stored payer MSISDN; callback settles once; duplicate callback no double receipt spam.
4. Unit: `normalize_ecocash_msisdn`; stub C2B without live key (`ECOCASH_ALLOW_STUB`).

### Phase B

1. Staff `create_ecocash_intent` + Edge initiate → pending intent with `payer_msisdn`.
2. Webhook success → `mark_ecocash_settled` posts Payment Entry; duplicate hash → no second PE.
3. Failure webhook → intent `failed`; no PE.
4. Customer self-pay RPC denies non-owned / already-paid invoice.
5. WhatsApp path creates intent + marks Flow order PAID in one settle path.
6. RLS: anon cannot read/write intents; customer sees only own; webhook table locked down.
7. Exclusion grep on migration + Edge + satellite diffs.

### Phase C

1. Web: saved vs other MSISDN → initiate → pending UI → webhook settle → invoice `amount_paid`.
2. POS: cashier enters customer EcoCash phone → same settle path.
3. Mobile customer: parity with web AuthZ.
4. Multi-currency: ZIG intent stores rate; PE matches.

## Verifier checklist

- [ ] No ZIMRA / FDMS / fiscal / payroll-tax strings in touched paths
- [ ] No HTML5/browser QR payment shortcuts
- [ ] New tables have RLS in the same migration
- [ ] Secrets only in Edge / satellite env examples — not clients, not migrations
- [ ] Ledger: settle append-only; cancel/fail does not UPDATE posted PE (reverse only)
- [ ] Multi-currency explicit on intent + PE
- [ ] Lane boundaries respected (one coding lane per phase PR)
- [ ] Smoke SQL (Phase B) green locally; satellite pytest green (Phase A)
- [ ] `/supabase-rls-auditor` + `/security-reviewer` after Phase B; `/verifier` after each phase

## Handoff sequence

1. **Phase A** — WhatsApp satellite (+ optional column migration) → `/verifier`
2. **Phase B** — `@backend_agent` → RLS auditor → security-reviewer → satellite adapter → `/verifier`
3. **Phase C** — per-surface agents (separate PRs) → `/verifier`
4. Update ADR status notes when Phase B accepted in production (see decision follow-on)

## Discovery note (adopt-first)

- **Integrate:** EcoCash merchant HTTP C2B (existing satellite client + portal) — not a new PSP product.
- **Reuse in-repo:** Phase 13 intent/webhook/Payment Entry patterns; ContiPay initiate phone requirement; customer-self-pay RPCs; `normalize_ecocash_msisdn`.
- **Build:** Cross-platform tender + payer MSISDN choice UX; Edge port of initiate/settle SoR.
- **Do not:** Rebase payments onto ERPNext/Odoo; invent a second Payment Entry model; keep FastAPI as permanent multi-surface SoR.
