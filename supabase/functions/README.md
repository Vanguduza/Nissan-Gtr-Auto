# Edge Functions

## Worker AuthZ (`process-sms-outbox`, `process-customer-receipts`, `demand-forecast`)

These use `service_role` internally and **must not** be publicly callable without a shared secret.

| Env | Header | Purpose |
|-----|--------|---------|
| `WORKER_SHARED_SECRET` | `x-worker-secret` | Required in prod/staging. Constant-time compared; missing/wrong → **401**. |
| `WORKER_ALLOW_UNVERIFIED_LOCAL=1` | — | Local stub only when `WORKER_SHARED_SECRET` is unset. Never enable in production. |

Never commit real secret values. Set via Supabase Edge secrets / local Deno env.

Gateway JWT: see `[functions.*] verify_jwt` in `supabase/config.toml`. Workers keep `verify_jwt = true` **and** still require `x-worker-secret`.

## Manager SMS (`process-sms-outbox`)

| Secrets | Behaviour |
|---------|-----------|
| `SMS_GATEWAY_API_KEY` **present** | `claim_sms_outbox_batch` → HTTP send → `complete_sms_outbox`. Response always `stub: false`. Failed sends mark `failed` (never fake success). |
| **Absent** + `WORKER_ALLOW_UNVERIFIED_LOCAL=1` (secret unset) | Local stub via `drain_sms_outbox_batch` (`stub: true`) |
| **Absent** otherwise | **503** with clear error |

### SMS gateway shape (assumed)

```
POST {SMS_GATEWAY_BASE_URL}/messages
Authorization: Bearer {SMS_GATEWAY_API_KEY}
Content-Type: application/json

{ "to": "+263…", "from": "<SMS_GATEWAY_SENDER optional>", "text": "…" }
```

2xx JSON may include `id` / `message_id` / `messageId` / `sid` as provider message id.

| Env | Notes |
|-----|--------|
| `SMS_GATEWAY_API_KEY` | Required for real send |
| `SMS_GATEWAY_BASE_URL` | Default `https://sms.nissangtrauto.co.zw/v1` |
| `SMS_GATEWAY_SENDER` | Optional from / short code |

Shared client: `_shared/sms_gateway.ts`.

## Customer receipts (`process-customer-receipts`)

Tax-agnostic PDF (no ZIMRA/FDMS/fiscal QR). Public download host: `https://nissangtrauto.co.zw/receipts/{token}`.

### Flow

1. Auth via worker secret.
2. Generate PDF for `document_id` (body) and/or documents from `list_receipt_documents_needing_pdf`.
3. Upload bytes to Storage bucket `customer-receipts` → `mark_receipt_pdf_ready` (path, size, sha256; outbox SMS/WA bodies get company-domain URL).
4. Channel drain:
   - **Local stub** (no channel secrets + `WORKER_ALLOW_UNVERIFIED_LOCAL=1` + secret unset): `process_receipt_outbox_batch(stub_success)`.
   - **Non-local, no secrets**: PDF may succeed; channel drain refused (clear error, no fake sent).
   - **Secrets present**: `claim_receipt_outbox_batch` → real SMS / email / WhatsApp → `complete_receipt_outbox`.

| Channel | Secrets | Payload |
|---------|---------|---------|
| SMS | `SMS_GATEWAY_*` | Summary + PDF link (already in `summary_body`) |
| Email | `EMAIL_API_KEY` + `EMAIL_FROM` (or `RESEND_API_KEY` / `RECEIPT_FROM_EMAIL`) | Body + PDF attachment when Storage download works |
| WhatsApp | `WHATSAPP_ACCESS_TOKEN` + `WHATSAPP_PHONE_NUMBER_ID` | Document message with company-domain PDF link (caption = summary) |

Per-channel missing secret (when not local stub) → that row marked **failed**, not sent.

### Email provider shape (Resend-compatible)

```
POST {EMAIL_API_BASE}/emails   # default https://api.resend.com
Authorization: Bearer {EMAIL_API_KEY}
{ from, to, subject, text, attachments?: [{ filename, content: base64 }] }
```

| Env | Notes |
|-----|--------|
| `EMAIL_API_KEY` / `RESEND_API_KEY` | API key |
| `EMAIL_FROM` / `RECEIPT_FROM_EMAIL` | e.g. `receipts@nissangtrauto.co.zw` |
| `EMAIL_API_BASE` | Optional; default Resend |

Shared client: `_shared/email_send.ts`. PDF builder: `_shared/receipt_pdf.ts` (pdf-lib).

## WhatsApp Cloud (`_shared/whatsapp_cloud.ts`)

Outbound Cloud API client shared by **receipt delivery** (`process-customer-receipts`) and the **parts-finder bot** (`whatsapp-webhook`). Do not mix receipt PDF sends into bot dialog turns. **Not** a browser QR / WebView bridge.

| Env | Notes |
|-----|--------|
| `WHATSAPP_ACCESS_TOKEN` | System user / permanent token |
| `WHATSAPP_PHONE_NUMBER_ID` | Cloud API phone number id |
| `WHATSAPP_API_VERSION` | Optional; default `v21.0` |

Exports: `sendWhatsAppText`, `sendWhatsAppDocument` (link or media id), `uploadWhatsAppMediaPdf`.

## WhatsApp parts-finder bot (`whatsapp-webhook`)

Meta Cloud API → verify + signed inbound → `search_catalog` via **service_role** → top hits + PDP deep-links → optional human handoff.

`verify_jwt = false` (Meta cannot send JWT). AuthZ = `X-Hub-Signature-256` with `WHATSAPP_APP_SECRET` (fail closed if secret unset, unless `WHATSAPP_ALLOW_UNVERIFIED_LOCAL=1` for local stub only).

| Env | Notes |
|-----|--------|
| `WHATSAPP_VERIFY_TOKEN` | GET `hub.verify_token` challenge |
| `WHATSAPP_APP_SECRET` | HMAC-SHA256 body → `X-Hub-Signature-256` |
| `WHATSAPP_ACCESS_TOKEN` / `WHATSAPP_PHONE_NUMBER_ID` | Outbound text via shared client |
| `WHATSAPP_HANDOFF_NUMBER` | Counter/sales MSISDN for keyword / empty-results handoff |
| `SITE_URL` | Origin for PDP links (default `https://nissangtrauto.co.zw`) |
| `WHATSAPP_ALLOW_UNVERIFIED_LOCAL` | `1` only when app secret unset (local) |
| `WHATSAPP_BOT_RATE_LIMIT_MAX` | Default `20` per window |
| `WHATSAPP_BOT_RATE_LIMIT_WINDOW_SEC` | Default `60` |

**Modes:** `part` \| `vin` \| `model` \| `pnc` (e.g. `part 16546-EA00A`). Bare text defaults to `part`. Keywords `agent` / `human` / `help` → handoff. Empty inbound → UX help text.

**PDP deep-link:** `{SITE_URL}/parts/{encodeURIComponent(oem)}` (matches `apps/web` storefront).

**Rate limit:** table `whatsapp_bot_rate_limits` + RPC `check_whatsapp_bot_rate_limit` (service_role only; RLS deny-by-default). Does **not** `GRANT EXECUTE` on `search_catalog` to `anon`.

**Smoke:**

```bash
deno test --allow-env supabase/functions/whatsapp-webhook/smoke_test.ts
# After migration applied (as elevated role / service_role session):
psql "$DATABASE_URL" -f supabase/tests/whatsapp_bot_rate_limit_smoke.sql
```

## ContiPay / Paynow (`*-initiate`, `*-webhook`)

### Initiate (`contipay-initiate`, `paynow-initiate`)

Caller JWT (staff or customer). Creates intent via RPC, then:

| Secrets | Behaviour |
|---------|-----------|
| **Present** | Calls real PSP; returns hosted `checkout_url` (`stub: false`). Always registers edge `defaultWebhookUrl` with PSP (never client `result_url`). Paynow `poll_url` stays server-side. |
| **Absent** + `*_ALLOW_UNVERIFIED_LOCAL=1` | Local bounce URL only (`stub: true`) |
| **Absent** otherwise | **503** with clear error — never fake success |

### Webhooks (`contipay-webhook`, `paynow-webhook`)

`verify_jwt = false` — AuthZ is provider HMAC / hash (`CONTIPAY_WEBHOOK_HMAC_SECRET`, `PAYNOW_INTEGRATION_KEY`), not JWT.

| Secrets | Behaviour |
|---------|-----------|
| **Present** | Verify signature/hash; settle via `mark_*_settled` (`stub: false`) |
| **Absent** + `*_ALLOW_UNVERIFIED_LOCAL=1` | Unverified local settle only |
| **Absent** otherwise | **401** |

### Env (Edge secrets only — never commit values)

| Env | Required for | Notes |
|-----|--------------|--------|
| `PAYNOW_INTEGRATION_ID` | Paynow initiate | Merchant integration id |
| `PAYNOW_INTEGRATION_KEY` | Paynow initiate + webhook | SHA512 field hash |
| `PAYNOW_ALLOW_UNVERIFIED_LOCAL` | Local only | `1` when keys unset |
| `CONTIPAY_API_KEY` | ContiPay initiate | Basic Auth username (token) |
| `CONTIPAY_API_SECRET` | ContiPay initiate | Basic Auth password; falls back to `CONTIPAY_WEBHOOK_HMAC_SECRET` if unset |
| `CONTIPAY_MERCHANT_ID` | ContiPay initiate | Merchant code |
| `CONTIPAY_WEBHOOK_HMAC_SECRET` | ContiPay webhook | HMAC-SHA256(raw body) → hex; compare `x-contipay-signature` |
| `CONTIPAY_MODE` | ContiPay initiate | `live` (default) → `api-v2.contipay.co.zw`; `dev`/`uat`/`test` → `api2-test.contipay.co.zw` |
| `CONTIPAY_API_BASE_URL` | ContiPay initiate | Optional override of base URL |
| `CONTIPAY_ALLOW_UNVERIFIED_LOCAL` | Local only | `1` when secrets unset |

**ContiPay initiate** also needs `phone` (or `metadata.phone` / `metadata.cell`) on the request body for redirect acquire.

Algorithms live in `_shared/payment_edge.ts` (Paynow SHA512 per [Paynow docs](https://developers.paynow.co.zw/docs/paynow/generating_hash/); ContiPay Basic Auth PUT per [contipay-js-client](https://github.com/njzw/contipay-js-client)).
