# Edge Functions

## Worker AuthZ (`process-sms-outbox`, `process-customer-receipts`, `demand-forecast`)

These use `service_role` internally and **must not** be publicly callable without a shared secret.

| Env | Header | Purpose |
|-----|--------|---------|
| `WORKER_SHARED_SECRET` | `x-worker-secret` | Required in prod/staging. Constant-time compared; missing/wrong → **401**. |
| `WORKER_ALLOW_UNVERIFIED_LOCAL=1` | — | Local stub only when `WORKER_SHARED_SECRET` is unset. Never enable in production. |

Never commit real secret values. Set via Supabase Edge secrets / local Deno env.

Gateway JWT: see `[functions.*] verify_jwt` in `supabase/config.toml`. Workers keep `verify_jwt = true` **and** still require `x-worker-secret`.

## ContiPay / Paynow (`*-initiate`, `*-webhook`)

### Initiate (`contipay-initiate`, `paynow-initiate`)

Caller JWT (staff or customer). Creates intent via RPC, then:

| Secrets | Behaviour |
|---------|-----------|
| **Present** | Calls real PSP; returns hosted `checkout_url` (`stub: false`). Paynow also returns `poll_url`. |
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
