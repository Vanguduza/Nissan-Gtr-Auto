# Nissan GTR Auto — WhatsApp Flows FastAPI satellite

Encrypted **Meta WhatsApp Flow** data-exchange backend for diagram multi-select cart →
dynamic totals → Paynow-style checkout CTA (EcoCash / OneMoney / InnBucks / card) →
PAID webhook → **PDF receipt on WhatsApp**.

Supabase remains the system of record. This service is a thin FastAPI adapter
(Meta crypto + Cloud API + order row). Receipts are **tax-agnostic** (no ZIMRA).

## Endpoints

| Method | Path | Role |
|--------|------|------|
| `POST` | `/api/v1/whatsapp/flow` | Meta Flow encrypted data exchange |
| `GET` | `/api/v1/whatsapp/flow/health` | Liveness |
| `POST` | `/api/v1/payments/callback` | Paynow / PSP settle → receipt PDF |
| `GET` | `/health` | Service health |
| `POST` | `/api/v1/payments/ecocash/push` | Direct EcoCash C2B push (PIN prompt) |
| `POST` | `/api/v1/payments/ecocash/lookup` | Poll EcoCash status → PAID + PDF |
| `POST` | `/api/v1/payments/ecocash/callback` | EcoCash server webhook settle |

**Cross-platform SoR (Phase B):** Edge `ecocash-initiate` / `ecocash-webhook` + table `ecocash_payment_intents` — see `docs/plans/2026-08-03-ecocash-direct-c2b-cross-platform.md`.

WhatsApp EcoCash checkout requires an explicit payer choice: **WhatsApp number** | **saved/profile** | **different EcoCash number**.

### Flow actions

| `action` | Behaviour |
|----------|-----------|
| `ping` | `{ "data": { "status": "active" } }` |
| `INIT` | Next screen `VIN_SEARCH` |
| `calculate_cart` | Price OEMs from `stock_items` + RETAIL `price_list_items`; return `cart_summary_text` |
| `checkout` | D-57: browse USD; EcoCash → ZiG settle via `build_checkout_display` + ops `fx_rate_id` (fail closed); insert PENDING; SUCCESS + EcoCash push or payment CTA |

Meta usually posts top-level `action: "data_exchange"`. Put the intent in `data.action`
(`calculate_cart` / `checkout`), or rely on screen fallback (`PARTS_SELECT` → cart,
`CHECKOUT` → checkout). Sample Flow JSON: `flow/sample_cart_flow.json`.

### D-57 checkout display (parity with `@gtr/payments`)

Python reimplementation in `app/services/checkout_display.py` (no TS import):

- Cart/browse totals stay **USD** (`currency` / `total` on `whatsapp_flow_orders`).
- **EcoCash** pay converts to **ZiG** MoneyMinor using ops `daily_exchange_rates` + stores `fx_rate_id` / `settle_*`.
- Missing/invalid daily rate → **fail closed** (CHECKOUT error; no invented payable).
- C2B push charges `settle_total` in ZiG, not browse USD.

Pass the customer WhatsApp MSISDN as `flow_token` (digits only) or `data.wa_id` so the
payment CTA and PDF receipt can be delivered.

## Layout

```
app/
  api/v1/flow_endpoint.py
  api/v1/payment_webhook.py
  core/flow_crypto.py      # RSA-OAEP + AES-128-GCM, IV XOR 0xFF (Meta v3)
  core/config.py
  services/cart_service.py
  services/meta_client.py
  services/receipt_pdf.py
  main.py
```

## Setup

```bash
cd services/whatsapp-flows
python -m venv .venv
# Windows: .venv\Scripts\activate
pip install -e ".[dev]"
cp .env.example .env
# Fill SUPABASE_SERVICE_ROLE_KEY from `npx supabase status`
```

### RSA key for Flows

```bash
openssl genrsa -out keys/private.pem 2048
openssl rsa -in keys/private.pem -pubout -out keys/public.pem
```

Upload `public.pem` to Meta (`whatsapp_business_encryption`). Keep `private.pem` local only (gitignored).

### Run

```bash
uvicorn app.main:app --reload --port 8088
```

Expose with ngrok/cloudflare tunnel for Meta:

`https://<tunnel>/api/v1/whatsapp/flow`

Apply migration:

```bash
# from repo root
npx supabase db push --local   # or migration up
```

### Tests

```bash
pytest -q
```

## Payment rails

### EcoCash direct (default for WhatsApp Flow)

1. Register at [developers.ecocash.co.zw](https://developers.ecocash.co.zw/) (merchant + online merchant).
2. Put the API key in `.env` as `ECOCASH_API_KEY`, set `ECOCASH_ENVIRONMENT=live`, `ECOCASH_ALLOW_STUB=false`.
3. Checkout with `payment_method=ecocash` → C2B push to customer MSISDN → PIN on phone.
4. Settle via `POST /api/v1/payments/ecocash/callback` or poll `POST /api/v1/payments/ecocash/lookup` with header `X-EcoCash-Signature` / `X-Payments-Webhook-Secret` (fail closed without `ECOCASH_WEBHOOK_SECRET` or `PAYMENTS_WEBHOOK_SECRET` unless `ECOCASH_ALLOW_UNVERIFIED_LOCAL=1`).
5. If EcoCash’s live path/auth header differs from the defaults in `.env.example`, override `ECOCASH_*_PATH_*` / `ECOCASH_AUTH_HEADER` only — no code change.

Without a key, C2B runs in **stub** mode (WhatsApp still tells the customer to approve).

### Paynow / ContiPay (aggregators)

- Stub link: `generate_payment_link` → storefront-style URL with `psp=paynow&stub=1`
- Production: swap stub for Edge `paynow-initiate` / ContiPay using service credentials
- Callback: `POST /api/v1/payments/callback` — require `X-Payments-Webhook-Secret` (or valid Paynow form hash with `PAYNOW_INTEGRATION_KEY`); local stub only behind `PAYMENTS_ALLOW_UNVERIFIED_LOCAL=1`
- Flow `payment_method=paynow` sends the CTA URL button instead of EcoCash push

## Delivery

`delivery_method`: `counter_collect` | `harare` | `nationwide` — fees from env; logistics handoff can read `whatsapp_flow_orders` after PAID.

## Discovery note

Built as a **satellite FastAPI service** (Meta Flow crypto has no drop-in OSS for GTR SoR). Reuses existing Supabase prices, Paynow decision, and WhatsApp Cloud patterns from Edge `_shared/whatsapp_cloud.ts`. EcoCash direct uses the official merchant HTTP API (not raw USSD).
