# Edge Functions

## Worker AuthZ (`process-sms-outbox`, `process-customer-receipts`, `demand-forecast`)

These use `service_role` internally and **must not** be publicly callable without a shared secret.

| Env | Header | Purpose |
|-----|--------|---------|
| `WORKER_SHARED_SECRET` | `x-worker-secret` | Required in prod/staging. Constant-time compared; missing/wrong → **401**. |
| `WORKER_ALLOW_UNVERIFIED_LOCAL=1` | — | Local stub only when `WORKER_SHARED_SECRET` is unset. Never enable in production. |

Never commit real secret values. Set via Supabase Edge secrets / local Deno env.

Gateway JWT: see `[functions.*] verify_jwt` in `supabase/config.toml`. Workers keep `verify_jwt = true` **and** still require `x-worker-secret`.

## Webhooks (`contipay-webhook`, `paynow-webhook`)

`verify_jwt = false` — AuthZ is provider HMAC / stub signature (`CONTIPAY_WEBHOOK_HMAC_SECRET`, `PAYNOW_INTEGRATION_KEY`), not JWT.
