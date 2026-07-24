# Storefront PSP return / cancel URLs

- Date: 2026-07-24
- Lane: `@web_agent` (routes + client redirect); `@backend_agent` (edge provider wiring)
- Status: accepted (storefront slice)

## Storefront routes

| Path | Role |
|------|------|
| `/checkout/return?invoice=<uuid>` | Browser return after ContiPay / Paynow hosted checkout |
| `/checkout/cancel?invoice=<uuid>` | Cancel / abort from provider UI |

These pages **do not** mark invoices paid. Settlement stays on `contipay-webhook` / `paynow-webhook`. Copy explains pending webhook confirmation.

Absolute URLs are built from `window.location.origin` in the browser, else `NEXT_PUBLIC_SITE_URL` (default `https://nissangtrauto.co.zw`).

## Client → edge contract

`createCustomerContipayIntent` / `createCustomerPaynowIntent` in `apps/web/lib/customer-storefront.ts`:

1. Prefer `functions.invoke('contipay-initiate' | 'paynow-initiate')` with body fields:
   - `sales_invoice_id`, `method`
   - `return_url`, `cancel_url` (Paynow also `result_url`)
   - `metadata` mirroring those URLs + `channel: storefront`
2. If the edge response includes a non-null `checkout_url` (or Paynow `poll_url`), the cart / order UI **redirects** the browser there.
3. Otherwise keep the intent-created message and stay on the order page (current stub: edge returns `checkout_url: null`).
4. RPC fallback `create_customer_*_intent` stores the same URLs in `p_metadata` when edge is unavailable.

## Secrets (never commit values)

| Env | Where | Purpose |
|-----|--------|---------|
| `CONTIPAY_API_KEY`, `CONTIPAY_MERCHANT_ID`, ContiPay HMAC secret | Edge Function env only | Real initiate + webhook verify |
| `PAYNOW_INTEGRATION_ID`, `PAYNOW_INTEGRATION_KEY` | Edge Function env only | Real initiate + hash verify |
| `CONTIPAY_ALLOW_UNVERIFIED_LOCAL=1` / `PAYNOW_ALLOW_UNVERIFIED_LOCAL=1` | Local edge only | Stub without secrets |
| `NEXT_PUBLIC_SITE_URL` | Web app | Public origin for return/cancel links — **not** a secret |

Do not put ContiPay / Paynow keys in `NEXT_PUBLIC_*`, migrations, or this doc.

## Backend follow-up (`@backend_agent`)

Edge stubs today accept `metadata` but **do not** read top-level `return_url` / `cancel_url` / `result_url`, and always return `checkout_url: null` / `poll_url: null`. When merchant keys land:

1. Destructure `return_url`, `cancel_url`, `result_url` in `contipay-initiate` / `paynow-initiate`.
2. Pass them to the provider create-session API; echo the hosted `checkout_url` (or Paynow redirect/poll URL) in the JSON response.
3. Persist return URLs on intent `metadata` (already merged by customer RPCs) — no new secrets in DB.
4. Optional: dedicated `return_url` column only if webhook reconciliation needs indexed lookup; metadata is enough for the storefront slice.

## Exclusions

No ZIMRA / fiscal QR; no HTML5 QR; no client-side HMAC with merchant keys.
