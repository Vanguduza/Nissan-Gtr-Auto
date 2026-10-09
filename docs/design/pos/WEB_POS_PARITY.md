# Web POS parity contract

**Owner decision:** D7, 2026-10-01 (`docs/decisions/2026-10-01-pos-owner-decisions.md`)
**Surface:** `apps/web` route `/staff/pos`
**Rule:** the web POS looks and functions **exactly like the tablet POS**. It is the same product on
a different runtime, not a separate design.

## 1. One canon, two runtimes

| Concern | Single source | Tablet (Android) | Web |
|---|---|---|---|
| Visual target | `reference/benchmark-home-expanded-2026-09-07.jpg` + `APPROVED_VISUAL_DELTAS.md` | `:feature:pos-ui` | `apps/web/components/pos/` |
| Tokens | `packages/ui/brand-tokens.json` → `build-tokens.mjs` | `PosTokens.kt` | `packages/ui/src/tokens.css` / `tokens.ts` (already generated from the same file) |
| Geometry | `VisualReferenceSpec.json` (ratios, window classes) | `PosScaffold`, `PosWindowClass` | CSS grid with the same ratios and the same four window-class breakpoints |
| Anatomy, copy, motion, states | `POS_FRONTEND_BLUEPRINT_REV_1_5.md` §3–§9 | Compose | React; same component names (`NavRail`, `VehicleCascade`, `PopularItemsRow`, `CurrentSalePane`, …) |
| Scope | `FEATURE_REGISTER.md` | per-row status | per-row **Web** status (WEB section) |
| Owner decisions | D1–D6 | apply | apply identically |

A screen is not done on either runtime until it is done on both, or until a web delta below covers
the difference.

## 2. Approved web deltas (platform, not design)

Only these differences are allowed. Anything else visible or behavioural is a regression.

| ID | Area | Tablet | Web | Why |
|---|---|---|---|---|
| W-001 | Offline | SQLCipher cache, offline cash sale, replay | **Online-only.** Offline shows the same amber status surface, and sale actions are disabled with the reason stated | No encrypted local store in a browser; `AGENTS.md` ledger and stock truth stay server-side |
| W-002 | Scanning | CameraX via `bridges/` | **Companion phone pairing** (existing `create_pos_scan_session`) and **USB/Bluetooth keyboard-wedge scanners** typed into the search field | Bridge-First law: no HTML5/browser camera QR scanning |
| W-003 | Receipt printing | ESC/POS (Bluetooth SPP, TCP 9100) and Android Print | **Browser print** of the same receipt document model (§6.6) at 80 mm and A4 profiles, plus PDF/SMS/WhatsApp receipt delivery | Browsers cannot reach ESC/POS printers; the document model stays shared |
| W-004 | Cash drawer | ESC/POS drawer kick | Not available; Settings states it | Hardware |
| W-005 | Kiosk / Device Owner / Lock Task / boot splash | Yes (D3 splash from hero image) | No kiosk. A short GT-R reveal plays from the same hero image on first POS load per session | Browser has no device ownership |
| W-006 | Staff portal (D4) | POS → Settings → Staff portal, second login | **Same flow:** POS → Settings → Staff portal requires re-entering credentials before management modules render | Kept identical by decision D4 |

## 3. Capability gap (web today versus tablet behaviour)

The tablet behaviour lives in `PosViewModel.kt` (86 operations, about 55 RPCs). The web POS today
(`lib/staff-pos.ts`, `staff-pos-panel.tsx`, July 2026) calls **8**.

| Capability | RPCs (tablet) | Web today |
|---|---|---|
| Cart: create (warehouse/currency/fulfilment setup), add, park, resume | `create_pos_cart`, `add_cart_line`, `park_pos_cart`, `resume_pos_cart` | ✅ |
| Cart: qty, remove line, void | `pos_cart_lines` update/delete (RLS table writes), `void_pos_cart` | ✅ (new POS) |
| Split tender checkout | `checkout_pos_cart_with_tenders` | ✅ |
| EcoCash direct charge | `create_ecocash_intent` | ✅ after checkout (customer approves with PIN) |
| Companion scan pairing | `create/claim/revoke_pos_scan_session` | ✅ |
| Catalogue search (Part/OEM, VIN, Model, PNC) | `search_catalog` | ✅ part search; vehicle-filtered when a vehicle is chosen |
| Vehicle cascade, fitment-filtered search, cart vehicle, multi-vehicle sale | `list_catalog_models` / `list_catalog_variants`, `search_pos_vehicle_spares`, `set_pos_cart_vehicle` (+ cart vehicle read) | ✅ active vehicle + every vehicle shopped for on the sale |
| Popular Items: best sellers + pins, remove/add (D1) | `list_pos_popular_spares`, `list/upsert/delete_pos_popular_pin`, `list/hide/unhide_pos_bestseller(s)` | ✅ part, vehicle, category and subcategory pins |
| Customer search/create/edit, bind to cart | `list_pos_customers`, `create_pos_customer`, `update_pos_customer`, `set_pos_cart_customer` | ✅ |
| Customer garage | `list_pos_customer_garage`, `upsert_pos_customer_garage_vehicle` | ✅ 0 manual / 1 auto / many chooser; save current vehicle |
| Manager-gated discount / price override / void / refund | `apply_pos_cart_discount`, `apply_pos_line_price_override`, `void_pos_cart`, `post_pos_refund` | ✅ manager signs in on an isolated, non-persisted session |
| Quotations: create, list, send, convert | `create_pos_quotation_from_cart`, `list_pos_quotations`, `send_pos_quotation`, `convert_pos_quotation_to_cart` | ✅ |
| Returns from recent invoices | `list_pos_recent_invoices`, `post_pos_refund` | ✅ |
| EPC Browse (maker → model → variant → section → diagram) | `list_catalog_*`, `get_catalog_diagram_by_slug` | ✅ Nissan model → variant → section → diagram with hotspots, add and pin |
| Receipt contacts and receipt document | checkout args + receipt artifacts | ✅ email/WhatsApp contacts; browser print 80 mm / A4 |

## 4. Build rules

1. New web POS code lives only in `apps/web/components/pos/` (and the `/staff/pos` route). It must
   not import `staff-pos-panel`, `staff-pos-shell`, `account.module.css` or `staff-nav`. Enforced by
   `scripts/design-lint.py` DL-12.
2. Data access goes through typed functions in `apps/web/lib/pos/`, one per RPC above. These mirror
   the tablet's typed gateways (§10.2). `lib/staff-pos.ts` is kept as the starting point and grows.
3. Visual verification: Playwright screenshots of `/staff/pos` at the benchmark's 1536×1024 and at
   each window class, compared against the benchmark by the same ratio tolerances as the Android
   goldens (§11).
4. The pre-benchmark web POS UI (`staff-pos-panel.tsx`, `staff-pos-shell.tsx`) is **retired**. It is
   deleted either now (same as D6 for the tablet) or when the new `/staff/pos` replaces it; this is
   the owner's call (see D7). It must never be restyled into the new POS.
