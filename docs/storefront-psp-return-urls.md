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
3. Otherwise keep the intent-created message and stay on the order page.
4. RPC fallback `create_customer_*_intent` stores the same URLs in `p_metadata` when edge is unavailable.

### Local stub `checkout_url` (secrets unset)

With `CONTIPAY_ALLOW_UNVERIFIED_LOCAL=1` / `PAYNOW_ALLOW_UNVERIFIED_LOCAL=1` and no merchant keys, initiate still creates the intent, merges redirect URLs into `p_metadata`, echoes `return_url` / `cancel_url` / `result_url` in the JSON response, and — when `return_url` is present — sets:

`checkout_url = {return_url}&psp=contipay|paynow&stub=1&intent_id={uuid}`

(existing `?invoice=` query preserved). Web can redirect to `/checkout/return` without real PSP keys. Settlement remains webhook-only; stub redirect does **not** mark paid.

When merchant keys land: pass the same URLs to the provider create-session API and replace stub `checkout_url` with the hosted session URL. Optional dedicated `return_url` column only if webhook reconciliation needs indexed lookup; metadata is enough for the storefront slice.

## Exclusions

No ZIMRA / fiscal QR; no HTML5 QR; no client-side HMAC with merchant keys.
