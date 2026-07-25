# POS scan session pairing (optional companion) + standalone sales POS

- Date: 2026-07-25
- Amended: 2026-07-25 (INTERRUPT — standalone required)
- Lane: `@backend_agent` (schema/RPC); `@management_app_agent` (sales POS + optional companion); `@web_agent` (sales `/staff/pos` workspace; pairing display only)
- Status: accepted

## Decision

### Standalone POS required (primary)

When a **sales** role logs into **android-management** or web **`/staff/pos`**:

1. **Default home = POS-dedicated workspace** (not the full management hub).
2. That workspace is a **full POS client** on one device: parts **search**, **catalog browse**, **direct add** to an open `pos_carts` row, and **checkout** (receipt email/WhatsApp + customer bind via extended `checkout_pos_cart` — same RPCs as companion path).
3. **No tablet and no phone pairing** are required. Single-device sales must complete a sale without `pos_scan_sessions`.

**Admin / warehouse** roles keep the **hub** as home; POS remains available but is not their default landing.

Line adds without QR use existing / catalog search add-line paths. Bridge-First QR applies **only when scanning** (companion or native POS with scanner).

### Companion pairing (optional enhancement)

Dual-device shop-floor may use **`pos_scan_sessions`** scoped to an open cart:

1. POS device (Android or web) owns the cart and **may** call `create_pos_scan_session` → pairing code (optional QR of that code — **not** inventory QR).
2. Phone runs Android management **Scan companion**: claim session, scan labels via **`bridges/` only**, `add_cart_line_from_qr`.
3. POS refreshes via Realtime or poll.

**Web never scans inventory QR** (no HTML5/browser camera). Scanner is always native when used.

Claim rules unchanged: staff sales/admin; prefer same `owner_user_id`; one open/claimed session per cart; revoke/expire closes scanner rights. Cart remains fully usable with **zero** sessions.

## Why

Sales reps often have only a phone or only one device. Companion scanning is valuable on the floor but must not gate the product. Role-based home keeps sales in till mode; hub stays for ops roles.

## Consequences

- Implement standalone search/catalog/cart/checkout **before or alongside** pairing UI; pairing must not be the only line-add path.
- New `pos_scan_sessions` + RLS still ship for the optional path; do not invent a second cart schema.
- No multi-scanner, no cross-rep claim, no iOS companion in v1.
- Plan: `2026-07-25-shop-floor-pos-companion-otp.md`.
