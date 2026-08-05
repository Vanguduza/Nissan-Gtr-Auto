# Edge Functions

## Worker AuthZ (`process-sms-outbox`, `process-customer-receipts`, `demand-forecast`, `process-ai-reports`, `process-crm-promos`, `chat-notify-on-message`)

These use `service_role` internally and **must not** be publicly callable without a shared secret.

| Env | Header | Purpose |
|-----|--------|---------|
| `WORKER_SHARED_SECRET` | `x-worker-secret` | Required in prod/staging. Constant-time compared; missing/wrong → **401**. |
| `WORKER_ALLOW_UNVERIFIED_LOCAL=1` | — | Local stub only when `WORKER_SHARED_SECRET` is unset. Never enable in production. |

Never commit real secret values. Set via Supabase Edge secrets / local Deno env.

Gateway JWT: see `[functions.*] verify_jwt` in `supabase/config.toml`. Workers keep `verify_jwt = true` **and** still require `x-worker-secret`.

## Chat notify (`chat-notify-on-message`)

Optional worker for in-app live chat. Resolves a `message_id` with `service_role` and returns notify targets (assigned staff / open queue / customer). Delivery is **stubbed** (`stub: true`) until push/email channels are wired — no secrets in git.

| Item | Detail |
|------|--------|
| Method | `POST /functions/v1/chat-notify-on-message` |
| Auth | `x-worker-secret` + JWT (`verify_jwt = true`) |
| Body | `{ "message_id": "<uuid>" }` |
| Success | `{ ok, stub, message_id, thread_id, sender_kind, targets, preview }` |

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

## Receipt download (`receipt-download`)

Public PDF fetch by opaque download token. **No service_role in the browser** — Next.js `GET /receipts/[token]` POSTs to this function with the anon key; the function uses `service_role` only in Deno to resolve the artifact and mint a Storage signed URL.

| Item | Detail |
|------|--------|
| Method | `POST /functions/v1/receipt-download` |
| Body | `{ "token": "<download_token>" }` |
| Success | `{ "signed_url": "https://…", "expires_in": 300 }` (also accepts `signedUrl` / `download_url` aliases on the web proxy) |
| Fail closed | Invalid/missing token, unknown artifact, bad path, or Storage error → **400/404** (no path leakage) |
| Bucket | Private `customer-receipts` |
| JWT | `verify_jwt = true` (anon Bearer from web is enough) |
| Exclusions | No ZIMRA / FDMS / fiscal QR; rejects storage paths matching `zimra\|fdms\|fiscal` |

```bash
curl -sS -X POST "$SUPABASE_URL/functions/v1/receipt-download" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"token":"<download_token>"}'
```

## Customer receipts (`process-customer-receipts`)

Tax-agnostic PDF (no ZIMRA/FDMS/fiscal QR). Public download host: `https://nissangtrauto.co.zw/receipts/{token}` (resolved via `receipt-download`).

## Branded docs (`render-branded-doc`)

Staff JWT. Body `{ "kind": "statement"|"payslip"|"id_card"|"business_card", …payload }` → `application/pdf`.
Shared renderer: `_shared/branded_docs_pdf.ts` (pdf-lib). No fiscal QR. ID = CR80; business card = 90×50mm.

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

## Auth OTP (`auth-otp`)

Customer signup/login OTP for **email and/or phone**. Fail-closed without gateway secrets.

**Server gate:** `verify` mints a short-lived HMAC `proof_token` (row in `auth_otp_proofs`).
`complete_signup` / `complete_login` consume that proof before creating a user or minting a session.
Public GoTrue signup is **disabled** (`enable_signup = false` in `config.toml`).

| Env | Behaviour |
|-----|-----------|
| `SMS_GATEWAY_API_KEY` / `EMAIL_API_KEY` + From | Real send via SMS / email helpers |
| **Absent** + `AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1` + non-prod | Local stub only; code **`000000`** |
| **Absent** otherwise / production heuristic | **503** clear error (never fake verified) |
| `AUTH_OTP_PROOF_SECRET` (optional) | HMAC for proof tokens; else `SUPABASE_SERVICE_ROLE_KEY` |

| Item | Detail |
|------|--------|
| Method | `POST /functions/v1/auth-otp` |
| JWT | `verify_jwt = false` (request before session) |
| Body | `{ "action": "request"\|"verify"\|"complete_signup"\|"complete_login", … }` |
| Stub gate test | `deno test --allow-env supabase/functions/auth-otp/smoke_test.ts` |

Never set `AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1` on production Edge.

## HR onboarding auth create (`hr-onboarding-create-auth`)

After `complete_hr_onboarding` leaves `user_id` null, staff HR/admin invokes this Edge to
Admin-create (or link) a GoTrue user, set `must_change_password`, and deliver the temp
password via `hr_credential_outbox` + existing email / SMS / WhatsApp gateways.

| Item | Detail |
|------|--------|
| Method | `POST /functions/v1/hr-onboarding-create-auth` |
| JWT | `verify_jwt = true` + `has_staff_role(['admin','hr'])` |
| Body | `{ "employee_id": "<uuid>" }` |
| Success | `{ ok, employee_id, user_id, created, must_change_password, channels }` — **no password** |
| Gateways | Same as auth-otp / receipts (`EMAIL_*`, `SMS_GATEWAY_*`, `WHATSAPP_*`); local stub via `AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1` |
| Table | `hr_credential_outbox` (RLS: HR/admin SELECT metadata only — **no `body`**; writes via SECURITY DEFINER RPCs) |
| Locks | `hr_auth_provision_locks` + `claim_hr_auth_provision` / `release_hr_auth_provision` (service_role) |
| Orphans | On `link_employee_auth_user` failure after `createUser`, Edge deletes the Auth user |
| Body TTL | Enqueue marks `sending`; `complete_*` redacts; `scrub_hr_credential_outbox_bodies(90)` clears stale plaintext |

Never return or log the temp password to the browser client.

## WhatsApp Cloud (`_shared/whatsapp_cloud.ts`)

Outbound Cloud API client shared by **receipt delivery** (`process-customer-receipts`) and the **parts-finder bot** (`whatsapp-webhook`). Do not mix receipt PDF sends into bot dialog turns. **Not** a browser QR / WebView bridge.

| Env | Notes |
|-----|--------|
| `WHATSAPP_ACCESS_TOKEN` | System user / permanent token |
| `WHATSAPP_PHONE_NUMBER_ID` | Cloud API phone number id |
| `WHATSAPP_API_VERSION` | Optional; default `v21.0` |

Exports: `sendWhatsAppText`, `sendWhatsAppDocument` (link or media id), `uploadWhatsAppMediaPdf`.

## AI staff analytics (`analytics-insights`, `process-ai-reports`)

Aggregates-only KPIs (sales, returns/CN, top SKUs, inventory/low-stock/stockouts, AR aging, credit holds, open DNs/jobs). **No** customer PII or journal dumps to Gemini. See `docs/decisions/2026-07-25-ai-report-schema-privacy.md`.

### Interactive (`analytics-insights`)

Staff JWT (`admin` | `finance` | `sales`). Body:

```json
{ "from": "ISO", "to": "ISO", "kpi_set": "ops_sales_v1|finance_performance_v1", "include_narrative": true }
```

Response: `{ "kpis", "narrative", "gemini_used", "error" }`.  
If `GEMINI_API_KEY` missing and narrative requested → **422** with KPIs still present, `error: "gemini_unavailable"`.

`finance_performance_v1` requires `admin|finance` (P&L account aggregates + margin; no Text-to-SQL / journal dumps).

### Cron worker (`process-ai-reports`)

`x-worker-secret` + optional gateway JWT. Due subscriptions for `daily` | `weekly` | `monthly` → `kpi_ops_sales_v1` or `kpi_finance_performance_v1` → optional Gemini → email / WhatsApp via `_shared/email_send.ts` + `_shared/whatsapp_cloud.ts`. Missing Gemini → **numeric-only** delivery (`gemini_used=false`).

| Env | Notes |
|-----|--------|
| `GEMINI_API_KEY` | Narrative; never commit; set via Edge secrets / local `supabase/functions/.env` |
| `GEMINI_MODEL` | Optional; default `gemini-2.0-flash` |
| `REPORT_FROM_EMAIL` | Report From; falls back to `EMAIL_FROM` / `RECEIPT_FROM_EMAIL` |
| `EMAIL_API_KEY` / `RESEND_API_KEY` | Existing email helper |
| `WHATSAPP_ACCESS_TOKEN` / `WHATSAPP_PHONE_NUMBER_ID` | Existing Cloud API helper |
| `WORKER_SHARED_SECRET` | Required for cron AuthZ |

### Cron schedule (suggested)

Schedule three jobs (or one daily job that you call three times with different `cadence`):

```bash
# Daily (e.g. 06:00 Africa/Harare)
curl -sS -X POST "$SUPABASE_URL/functions/v1/process-ai-reports" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
  -H "x-worker-secret: $WORKER_SHARED_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"cadence":"daily"}'

# Weekly / monthly — same URL with cadence weekly|monthly
```

### Manual test (force daily)

```bash
curl -sS -X POST "$SUPABASE_URL/functions/v1/process-ai-reports" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
  -H "x-worker-secret: $WORKER_SHARED_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"cadence":"daily","force":true}'
```

Optional: `"subscription_id":"<uuid>"` to target one subscription. Verify `ai_report_runs` + `ai_report_deliveries`; without Gemini → numeric body, `gemini_used=false`.

Shared narrative helper: `_shared/gemini_narrative.ts`.

## CRM promotional outreach (`process-crm-promos`)

Opt-in only (`customers.marketing_opt_in`). Plan/ADR: `docs/plans/2026-08-03-ai-autonomous-erp-layer.md`.

| Item | Detail |
|------|--------|
| Method | `POST /functions/v1/process-crm-promos` |
| Auth | `x-worker-secret` |
| Body | `{ "limit": 25, "force": false }` |
| Flow | `list_crm_promo_candidates` → Gemini or template copy → email / WhatsApp (SMS fallback) → `ai_promo_*` rows + cooldown stamp |

```bash
curl -sS -X POST "$SUPABASE_URL/functions/v1/process-crm-promos" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
  -H "x-worker-secret: $WORKER_SHARED_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"limit":10,"force":false}'
```

### Cron schedule (suggested — mirrors `ai_worker_schedules`)

Rows in `public.ai_worker_schedules` document cadence + Edge path/body. Call with `x-worker-secret` (same AuthZ as `process-ai-reports`):

```bash
# Daily CRM promos (e.g. 07:00 Africa/Harare) — opt-in + cooldown
curl -sS -X POST "$SUPABASE_URL/functions/v1/process-crm-promos" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
  -H "x-worker-secret: $WORKER_SHARED_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"limit":25,"force":false}'

# Report cadences — same pattern as analytics subscriptions
curl -sS -X POST "$SUPABASE_URL/functions/v1/process-ai-reports" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
  -H "x-worker-secret: $WORKER_SHARED_SECRET" \
  -H "Content-Type: application/json" \
  -d '{"cadence":"daily"}'
# weekly / monthly: change cadence in body (see ai_worker_schedules.cron_expr)
```

Optional: after a successful cron run, `touch_ai_worker_schedule('process-crm-promos-daily')` (service_role / admin).

## Stores insights (`stores-insights`)

Staff JWT (`admin` | `warehouse` | `finance`). Runs ABC classification + forecast suggestion KPIs; optional structured Gemini directives (never auto-PO).

```json
{ "warehouse_id": "<uuid>", "include_directives": true, "run_abc": true }
```

UI: `/staff/warehouse/insights`.

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
