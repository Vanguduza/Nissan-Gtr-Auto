# Standalone adaptive POS — Nissan GTR counter till

- Status: **accepted** → **EPIC GREEN** 2026-08-15 (P0–P4 Must) → **Post-EPIC GREEN** 2026-08-15 — former P4 Should + Before Live Done (see §13)
- Date: 2026-08-15
- Lane(s): **`@pos_app_agent`** (new app) → `@backend_agent` (snapshot additive) → `@hardware_mobile_agent` (scan / ESC/POS / drawer) → `@management_app_agent` (drop till chrome / deep-link follow-on) → `@web_agent` (web `/staff/pos` fallback **Later**)
- Skills: `/token-discipline`; `/nissan-fast-parser` + `/parts-catalog-ingestion` when wiring fitment/EPC; `/qr-inventory-workflow` if scan/print/drawer; `/accounting-ledger` if till-float / refund JE; **no** auto `/ui-ux-pro-max`
- Pointer ADR: [`docs/decisions/2026-08-15-standalone-adaptive-pos.md`](../decisions/2026-08-15-standalone-adaptive-pos.md)
- **Autonomous build prompt (green gates):** [`2026-08-15-standalone-adaptive-pos-autonomous-prompt.md`](./2026-08-15-standalone-adaptive-pos-autonomous-prompt.md)
- Extends (do not duplicate): [offline SQLCipher](../decisions/2026-08-03-offline-sqlcipher-pos-cache.md), [EPC/shop stock context](../decisions/2026-08-13-customer-epc-shop-stock-context.md), [scan pairing](../decisions/2026-07-25-pos-scan-session-pairing.md), [receipt bind](../decisions/2026-07-25-pos-receipt-contact-customer-bind.md), [kiosk catalog](./2026-08-03-tablet-kiosk-pos-full-action-plan.md) (Lock Task **moves** to this APK; UI does not)
- Supersedes **UI only**: [Dial POS redesign](./2026-08-12-pos-dial-ux-redesign.md) Android till chrome; management `feature/pos` Compose; CoolMall POS skeleton. **Does not** supersede cart/checkout RPCs or kiosk ops intent.

## 1. Goal

Ship a **dedicated counter POS** (`apps/android-pos/`) that:

1. Looks like a hospitality till (dense tiles, ticket, huge Pay, hardware rail) on tablet; reflows to a pocket till on phone.
2. Finds the **correct Nissan spare** (VIN/chassis latch, OEM/OE/PNC, shop-stock honesty, supersession) in as few taps as a scan.
3. Splits tenders with **real remaining/change math**, then posts the existing `checkout_pos_cart_with_tenders` payload.
4. Works **offline** from an encrypted catalog+stock copy, then **auto-syncs** (replay outbox → pull snapshot) when the network returns.

**Keep:** Supabase sales SoR, RLS, catalog/fitment tables, payment rails, bridges.  
**Discard:** Every current POS screen. Do not extract or restyle the management till.

## 2. Locked principles

| Principle | Meaning |
|-----------|---------|
| One APK, window-size layout | Compact / medium / expanded — not two POS packages. Kiosk is **device policy** on the counter tablet. |
| Cafe **shell**, FAST **finder** | Right pane = ticket + Pay. Left pane = vehicle-latched spare lookup. |
| Server validates, client allocates | Tender sum must equal due. Cash change is local; RPC gets **applied** amounts only. |
| Cache ≠ ledger | SQLCipher snapshot + outbox. Journals only via checkout/replay RPCs. |
| Shared OEM context | Same identity as customer EPC/shop: OEM → `stock_items` + `part_fitment`. Qty never from Meili. |
| WH2 sells | Storefloor is the pick source. WH1 is “in receiving” info only. Quarantine never saleable. |
| Bridge-First | Scan, print, drawer → `bridges/` only. |
| Hard exclusions | No ZIMRA / tax line / payroll tax / HTML5 camera / Flutter-Expo till / second cart. |

## 3. Product split

```
apps/android-pos/          till only (this epic)
apps/android-management/   WH / HR / finance / dispatch — no till chrome (follow-on)
apps/android-delivery/     already split
apps/web /staff/pos        management fallback, not kiosk SoR (Later restyle)
```

Staff login reuses `resolve_staff_login_email` + GoTrue. Sales land on the till. No warehouse/HR/finance modules in this APK.

Follow-on (same epic close-out, not a blocker for first till): add `@pos_app_agent` to `rufler.yaml` + `AGENTS.md`; management “Open POS” deep link.

## 4. Screen architecture

### 4.1 Expanded (tablet landscape) — target chrome

Hospitality 3-pane, GTR steel / chalk / red (not cafe purple):

| Region | Role |
|--------|------|
| Header | Branch, cashier, `terminal_id`, **vehicle latch** (center — replaces “TABLE 1”), Select customer, Orders (parked + quotations) |
| Left ~55–60% | Spare finder (modes below) |
| Right ~35% | Ticket: lines, expand-in-place Note / Discount / Info, Park / Void, subtotal + item count, **Pay** |
| Rail ~5% | Reprint, drawer kick, scanner, sync-now, till settings / Device Admin |

No tax row. Currency always explicit (`USD` \| `ZIG`). Core-charge children visible on the ticket.

### 4.2 Medium / compact

| Window | Layout |
|--------|--------|
| Medium (portrait tablet / fold) | Finder + ticket; rail in overflow |
| Compact (phone) | Finder full-bleed; sticky `N items · $total` → ticket; Pay on ticket; scan FAB; rail → overflow |

Same ViewModel / same `pos_carts` id. Use Compose Material 3 Adaptive (`currentWindowAdaptiveInfo`, `SupportingPaneScaffold`). Donors: [android/adaptive-apps-samples](https://github.com/android/adaptive-apps-samples), [Reply](https://github.com/android/compose-samples/tree/main/Reply) (Apache-2.0). Replace any 700dp `BoxWithConstraints` thinking — this app never inherits that code.

### 4.3 Touch / speed

- Hit targets ≥48dp.
- **Known OEM + latched vehicle + WH2 qty:** scan → Fits → in ticket in **one action** (<1s online; local snapshot <200ms).
- Debounce duplicate scans of the same OEM (~400ms) → increment qty, do not double-line.
- Price-check mode: lookup **without** mutating the cart (utility rail).

### 4.4 Visual contract (golden)

**Pixel SoT:** generated expanded till mock (`gtr-pos-till-proposed-look.png` / `docs/plans/assets/2026-08-15-gtr-pos-till-golden.png` when checked in).  
**Agent lock:** `.cursor/rules/android-pos-till.mdc` (globs `apps/android-pos/**`).

The till **is** that screen. Function is wired *into* those slots — extra pages are sheets (Pay, Quote, Customer), not a second IA. Full enforcement: **§15**.

## 5. Nissan GTR lookup (left pane)

This is the differentiator. Do **not** default to generic Drink/Pizza-style SKU tiles.

### 5.1 Vehicle latch (sticky)

Latch is the sale’s vehicle context (stored on the cart session locally; optional follow-on column Later):

1. Scan or type VIN → `search_catalog('vin', …)` / `vehicle_master.vin_prefix` (11-char prefix).
2. Or cascade maker → model → variant → engine (`list_catalog_makers` / `_models` / `_variants`).
3. Banner example: `GT-R R35 · VR38DETT · 2012`. Clear / change vehicle is explicit (does not wipe the cart; re-badges lines).

**No latch:** selling still allowed; every row is **Verify fitment**; Add requires confirm.  
**GTR bias (Should):** shortcut chips for high-volume chassis in this shop (e.g. R35 / Y62 / D40) from `vehicle_master` — data-driven, not hard-coded marketing.

Normalize Nissan OEM on input: uppercase, trim, `XXXXX-XXXXX` with or without hyphen.

### 5.2 Four modes (hit routing is locked — §16.2)

| Tab (golden) | Query | Hits become |
|--------------|--------|-------------|
| **SCAN / OEM** | Bridge scan or `search_catalog('part')` | **Tiles** only (`type: part`). Hydrate via `list_pos_till_items` `oems`. Scan with known OEM may skip search and hydrate one OEM. |
| **SHOP STOCK** | `list_pos_till_items` `shop_stock` | **Tiles**. Default `p_in_stock_only = true`. Latch filters to **Fits** (plus Verify if no fitment rows). |
| **EPC** | `list_catalog_makers` → models → variants → `list_catalog_sections` | Tree navigation. **Leaf** → `list_pos_till_items` `section`. Diagram thumbnail `get_catalog_diagram` (online). |
| **VIN / PNC** | `search_catalog('vin'\|'model'\|'pnc')` | **Never add a vehicle/PNC row to the cart.** `type: vehicle` → **latch**. `type: pnc` → open that EPC section. `type: part` (if any) → tiles. |

Facet chips (Brakes, Filters, …) are `p_category` on `shop_stock` after a latch — not a restaurant SKU menu.

**In stock** toggle: `p_in_stock_only` true/false. False = priced+unpriced catalog for that filter; unpriced still **cannot** add (§16.3).

### 5.3 Result row (must answer four questions)

1. OEM + short name  
2. Fitment chip: **Fits** / **Verify** / **Does not fit** (`part_fitment` vs latched chassis/engine)  
3. **WH2 qty + bin** (`get_pick_path_hints` / bin_code). Optional WH1 “in receiving” — not sellable  
4. Price + core-charge pip; currency  

**Add rules** (full ticket/CTA rules: §16.3)

| Condition | Action |
|-----------|--------|
| Fits + priced + WH2 covers qty | One-tap `add_cart_line` — line **sellable** |
| Does not fit | Block; show chassis mismatch. Manager override **Later** |
| Verify / no latch | Confirm sheet, then add as sellable if priced+stock else quote-only |
| Priced + WH2 shortfall (OOS) | `add_cart_line` allowed (RPC does **not** check qty at add) — line **quote-only** |
| Unpriced / `resolve_item_price` null or ≤ 0 | **Do not** `add_cart_line`. Tile CTA = Needs price |
| Supersession | Banner “use `{superseded_by}` instead” → hydrate that OEM, then add rules apply |

### 5.4 Line Info (spares referencing)

Expand-in-place (cafe NOTE/DISCOUNT/INFO pattern) maps to:

| Control | Counter meaning |
|---------|-----------------|
| Note | Pick instruction / customer wait |
| Discount | Manager reauth + existing discount RPC |
| Info | Fitment drawer: chassis/engine/PNC, diagram thumbnail (online), supersession, same-PNC alternatives with stock, OE xrefs |

Pipeline owns fitment/diagrams (read-only). Staff merch photos optional on Info if present.

### 5.5 Offline lookup

Snapshot today: OEM, description, price, core charge, WH2 qty. Enough for **scan/OEM sell**. Not enough for VIN/EPC.

**Must (additive RPC, same function):** extend `pull_pos_offline_snapshot` items with `pnc_code`, `category_name`, `superseded_by`, `bin_code` (nullable), and compact fitment keys (`chassis_code` / `engine_code` — array or sidecar index). RLS/staff gate unchanged.

**Should:** when a vehicle is latched **online**, prefetch that chassis’s OEM set into a hot cache for faster offline.

**Offline UX:** Scan/OEM + shop-stock-from-snapshot enabled. VIN/EPC browse disabled with “needs online / last sync {time}” unless fitment index is present. Diagrams always online-only.

## 6. Ticket, pay, split tenders

### 6.1 Ticket

- One open `create_pos_cart` (WH2, `USD`\|`ZIG`, customer optional).
- Lines from `add_cart_line` / `_from_qr`. Core-charge parent + child both shown.
- Qty ±, park (`park_pos_cart` / `resume_pos_cart`), void (manager RPC).
- Select customer; credit hold/limit is server-side (on_hold → “needs finance”, not paid).

### 6.2 TenderAllocator (new; pure Kotlin, integer minor units)

`settle_invoice_tenders` requires `Σ applied == open balance` (±0.01), same currency, amount > 0. Modes: `cash`, `bank`, `store_credit`, `ecocash`, `paynow`, `contipay`.

```
due        = ticket total (incl. core-charge children)
applied[i] = amount posted for mode i
tendered[i]= cash handed over (may exceed applied)
remaining  = due − Σ applied
change     = cash tendered − cash applied
```

| Mode | Overpay | Posted |
|------|---------|--------|
| Cash | Yes | `applied = min(tendered, remaining)`; show change; **do not** send extra |
| Bank / store credit | No | Exact slice |
| EcoCash / Paynow / ContiPay | No | Exact slice **and** live intent settled before checkout |

**Shared amounts**

- **Fill rest** — next field = `remaining`.
- **Split equally** — `due / n` in minor units; leftover cents on the **last** line so the sum is exact.
- Edit any line → remaining/change recompute.
- Pay enabled iff `remaining == 0` and every live-rail slice is settled.

Pay sheet: due / remaining / change, mode chips, Exact / Fill rest / Split equally, allocated list, receipt WhatsApp/email, confirm → `checkout_pos_cart_with_tenders`.

Live rails: intent **per slice** → settle → one checkout with the full tender array. Failure on one rail does not checkout. Store credit requires named customer.

**Offline:** cash only (+ local change). Other modes disabled (“needs connection”). Recording “EcoCash later” is **park**, not an offline completed sale (replay rejects non-cash).

## 7. Offline store + sync manager

### 7.1 Local DB (SQLCipher, Keystore-wrapped passphrase)

| Table | Contents |
|-------|----------|
| catalog/stock | Snapshot rows (incl. additive fitment/bin fields) |
| meta | `warehouse_id`, `pulled_at`, price list, currency |
| outbox | `client_sale_id`, lines, tenders, contacts, `sold_at`, status |
| vehicle_hot (Should) | Last-latched chassis OEM subset |

Never cache manager approval tokens or password hashes (offline login #58 stays Later).

Local add: snapshot list price, decrement local qty, refuse qty > local saleable. Replay still calls `add_cart_line` (server splits core charge).

### 7.2 PosSyncManager

**Order is locked:** (1) drain outbox `replay_offline_pos_sale` (idempotent on `client_sale_id`) → (2) `pull_pos_offline_snapshot` replace local catalog/stock.

Triggers: `ConnectivityManager` (validated internet), till open if online, WorkManager periodic, rail **Sync now**.

Conflicts (`offline_price_conflict`, stock short): keep outbox row `conflict`; attendant resolves; **never** invent a journal on device. Restore local qty if replay fails.

Banner: Online · Offline · snapshot `{time}` · Syncing N · Conflict N.

Local receipt print allowed for queued cash sales; reprint after sync uses server invoice number when present.

## 8. POS best-practice catalog (counter)

### Must (this epic)

| # | Practice | Notes |
|---|---------|--------|
| 1 | Vehicle latch + fitment chips | §5 |
| 2 | 4-way lookup + shop-stock gate | §5.2 |
| 3 | OEM normalize + scan debounce + supersession | §5 |
| 4 | WH2 qty + bin on row | `get_pick_path_hints` |
| 5 | Split-tender math + cash change | §6.2 |
| 6 | Offline snapshot + auto sync | §7 |
| 7 | Park / void / discount reauth | Existing RPCs |
| 8 | Receipt contacts + bind | Existing checkout args |
| 9 | Dual currency + core charge | Ticket display |
| 10 | Price-check without sale | Rail |
| 11 | Quote from lookup when OOS | `create_pos_quotation_from_cart` |
| 12 | Idle lock + in-app login | Move kiosk session from tablet management plan |
| 13 | Bridge scan / ESC/POS print | No HTML5 |
| 14 | Honest stock | Never Meili qty |

### Should (same app, after Must)

| # | Practice | Notes |
|---|---------|--------|
| 15 | Till open/close float | Map to existing cash-sales `open_account_period` / `close_account_period` (1110 ≠ 1120); do not invent a second cashbook |
| 16 | Returns from till → quarantine | `post_pos_refund` + quarantine WH; never direct exchange |
| 17 | Related / same-PNC attach strip | After add |
| 18 | Customer last invoices / garage VIN | Repeat GT-R owners |
| 19 | Chassis shortcut chips | Data-driven from `vehicle_master` |
| 20 | Companion scan pairing | Optional; phone can **be** the till now |
| 21 | Kiosk Lock Task / HOME / Device Owner | Move from management tablet flavor onto this package |
| 22 | Snapshot fitment index + vehicle hot cache | §5.5 |

### Later

Employee PIN/NFC/biometric login; iOS staff kiosk; mixed-currency tenders; offline live rails; Casbin; training mode; full diagram canvas as the default counter path (thumbnail + list is enough).

### Never

Restaurant tables/courses/KDS, UK plate lookup, ZIMRA/tax QR, browser camera, selling quarantine/WH1, editing fitment on the till.

## 9. Adopt-first (UI donors only)

| Repo | License | Use |
|------|---------|-----|
| android/adaptive-apps-samples + Reply | Apache-2.0 | Pane reflow |
| tio-res / flutter-pos-system | MIT / Apache-2.0 | **Layout recipe** (grid + bill) — not Dart, not their DB |
| In-tree `@gtr/ui` / `GtrColors` | — | Brand tokens |
| CoolMall | vendored MIT | **Do not** use as till chrome |

**Reject:** SaleFlex.mPOS (AGPL/commercial), TailPOS/Odoo/ERPNext POS (GPL + second SoR), OmniCart (no license), new Flutter/Expo app.

Hardware: existing `QrScannerBridge`, `EscPosPrinterBridge`; drawer kick via ESC/POS pulse if the printer supports it.

## 10. App modules (greenfield)

```
apps/android-pos/
  app/                 login, WindowSizeClass shell, optional kiosk
  :pos-api/            RPC DTOs + calls (names match backend; Fake/Live)
  feature-till/        finder + ticket + rail
  feature-lookup/      latch, 4-way, fitment badges, shop-stock filter
  feature-pay/         TenderAllocator + pay sheet
  feature-orders/      parked + quotations
  feature-customer/    select / bind
  sync/                SQLCipher store + PosSyncManager
```

Share **RPC contracts**, not management Compose. Prefer a slim `:pos-api` over importing the 100-method management `RpcClient`.

## 11. Backend additive (keep structure)

| Change | Why | Lane |
|--------|-----|------|
| **`list_pos_till_items`** (new, staff-only) | One tile DTO for shop stock / OEM hydrate / EPC section — §16.1 | `@backend_agent` + RLS in same migration |
| Extend `pull_pos_offline_snapshot` to **same item shape** | Online grid and offline cache share `TillItem` | `@backend_agent` |
| Optional cart vehicle latch column | Persist VIN/chassis on `pos_carts` | Later if local session is insufficient |
| None of: new cart, new ledger, tax, second tender RPC, Meili qty | Frozen SoR | — |

Replay, checkout, search, diagram, park, quote, refund RPCs **stay**.

## 12. Phases

| Phase | Scope | Done when |
|-------|--------|-----------|
| **P0** | App shell, auth, adaptive 3-pane, Fake RPC, GTR theme | Phone + tablet preview; no management UI copied |
| **P1** | Lookup: latch, hit routing, `list_pos_till_items`, fitment predicate, add/quote-intent CTAs | §16 contracts green; scan/OEM + shop stock against live/Fake |
| **P2** | Ticket + TenderAllocator + live rails + park/void/discount + receipts | Split cash+EcoCash posts; change not sent to RPC |
| **P3** | SQLCipher snapshot (extended) + PosSyncManager + conflict UI | Offline cash sale survives reconnect; replay then pull |
| **P4** | Quotes from OOS, price-check, bin on row, kiosk move, till float (Should) | Counter-complete |
| **Follow-on** | `rufler.yaml` / `AGENTS.md` lane; management drops till; web POS restyle Later | Isolation complete |

Do not start P2 chrome before P1 lookup — a pretty cafe grid without fitment fails the product.

## 13. Acceptance

- [x] Tablet landscape matches 3-pane shell; phone uses sticky ticket; rotate preserves cart + latch
- [x] Latched R35 VIN filters shop-stock to fitting OEMs; Does-not-fit cannot silent-add
- [x] OEM / OE / VIN / PNC / EPC section paths all reach Add or Quote
- [x] Supersession offers swap; core-charge child appears on ticket
- [x] WH2 qty + bin shown; WH1/quarantine not sold
- [x] Split equally + fill rest + cash change; RPC tenders sum to due
- [x] Offline: local OEM lookup + cash sale queued; EcoCash disabled; reconnect replays then refreshes snapshot
- [x] Conflicts stay in outbox; no client-invented JE
- [x] Print/scan via bridges only; no tax line; no ZIMRA
- [x] Unit tests: `TenderAllocator` (equal split remainder cents, cash change); window classes; **§16.7** fitment/hit-router/CTA; sync order replay-then-pull
- [x] `list_pos_till_items` + snapshot share `TillItem`; Meili hits never show qty
- [x] VIN hit latches; PNC hit opens EPC; OOS priced → quote-only; unpriced → no line

### P4 Should → **Must implement now** (Post-EPIC — no longer Deferred)

Former Deferred items + Before Live gaps. **Do not ship “Deferred” again.**

#### Coding order (Manager)

1. `@hardware_mobile_agent` — drawer kick in `bridges/` + POS module deps
2. `@pos_app_agent` — auth Live, LivePosClient HTTP, SqlCipher wire, kiosk, float, refund, chassis chips
3. `@management_app_agent` — secure session handoff extras on Open POS
4. `@backend_agent` — only if ranked chassis RPC needed (else PostgREST / catalog RPCs)
5. `@web_agent` — optional one-line `/staff/pos` notice (no restyle)

#### Checklist + AC

| # | Item | Status | Lane | Acceptance |
|---|------|--------|------|------------|
| 1 | Staff auth Fake\|Live | **Done** | `@pos_app_agent` | `resolve_staff_login_email` → GoTrue (mirror management). Fake without creds; Live lands on till with staff in header. No hardcoded JWTs. |
| 2 | Bridges wired | **Done** | `@hardware_mobile_agent` → `@pos_app_agent` | Drawer kick in `bridges/`; POS depends on bridge modules; rail Scan/Print/Drawer via bridges (Fake CI / Live HW). No HTML5 camera. |
| 3 | SqlCipher store | **Done** | `@pos_app_agent` | Production `MainActivity` uses `SqlCipherOfflineStore`; `InMemoryOfflineStore` tests-only; `allowBackup=false` stays; offline cash still replay→pull. |
| 4 | LivePosClient real RPCs | **Done** | `@pos_app_agent` | With URL+anon key, RPCs hit network (not always Fake). Without creds → Fake. Consumes existing `list_pos_till_items` / cart / checkout. |
| 5 | Kiosk Lock Task | **Done** | `@pos_app_agent` | Adapt `android-management-legacy/feature/kiosk` onto POS; Lock Task + gated maintenance exit; compose with existing idle lock. |
| 6 | Till float | **Done** | `@pos_app_agent` | `open_account_period` / `close_account_period` (cash-sales; 1110≠1120). Migration: sales/warehouse may open/close **1120 only**. |
| 7 | Returns sheet | **Done** | `@pos_app_agent` | `post_pos_refund` → quarantine WH; never direct exchange / WH2 restock. |
| 8 | Chassis chips | **Done** | `@pos_app_agent` | Data-driven from `vehicle_master` / variants. Tap latches + filters shop stock. |
| 9 | Session handoff | **Done** | `@management_app_agent` + `@pos_app_agent` | Open POS passes extras (no password); POS imports session when valid; tokens never logged. |
| 10 | Web notice | **Skipped** | `@web_agent` optional | Prefer no restyle; silent `/staff/pos` → hub redirect remains. |

**Hard exclusions:** no ZIMRA/tax · no payroll tax · no HTML5 QR · no CoolMall/management POS Compose port · no Meili qty · no second cart/ledger.

**Before Live gate:** 1–9 GREEN (10 optional); `/verifier`; `/security-reviewer` on auth+handoff+bridges; `/supabase-rls-auditor` only if new migration.

## 14. Exclusions

- No port of `apps/android-management/feature/pos` or web `staff-pos-shell`
- No CoolMall till, no Expo/Flutter till
- No ZIMRA, payroll tax, HTML5 QR
- No offline manager tokens / Argon2 login
- No selling from Meili documents
- `catalog-apk` out of scope (pipeline satellite, not the till)

## 15. Design strategies to enforce look + function

Goal: the expanded till matches the golden mock **and** every visible control is live (lookup, fitment, cart/quote, tenders, sync). Do not build a pretty shell then “add features later” in other screens.

### 15.1 One scaffold, named slots

`TillScaffold` is the only till chrome. Callers fill slots; they cannot omit a slot on expanded width.

| Slot | Golden region | Required function |
|------|----------------|-------------------|
| `brand` | GT-R + NISSAN GTR AUTO \| staff \| Till · WH2 | Auth session, `terminal_id`, WH2 |
| `vehicleLatch` | Center pill | VIN/chassis latch; X clears; re-badges tiles |
| `customer` / `orders` | Header right | Bind customer; parked + quotations |
| `clock` | Header far right | Local time |
| `finderModes` | SCAN/OEM · SHOP STOCK · EPC · VIN/PNC | Four lookup modes; selected = red underline |
| `search` | OEM / OE / VIN / PNC + barcode | Typeahead + Bridge scan |
| `facets` | Brakes… + In stock | PNC/category + shop-stock gate |
| `grid` | 4-col tiles | Result rows: OEM, name, USD, FITS/VERIFY/NO FIT, WH2 qty · bin |
| `ticket` | Right pane | Lines + core-charge child; expand NOTE/DISCOUNT/INFO; ± qty |
| `ticketActions` | PARK / VOID | Existing RPCs |
| `totals` | Subtotal + item count | **No tax row** |
| `pay` | Full-width red PAY USD **or** QUOTE | PAY if all lines sellable; QUOTE if any quote-only (§16.3) |
| `rail` | Sync, scan, print, drawer, info, settings | Sync manager, bridges, Device Admin |
| `status` | Online · snapshot · FITS filter | Connectivity + last pull |

Compact width **reflows the same slots** (finder full; ticket → sticky bar). Do not invent a phone-only IA.

Layout constants (expanded): finder `0.58f`, ticket `0.37f`, rail `0.05f`. Header height ~56–64dp. Tile min 48dp. Pay min 56dp.

### 15.2 Tokens only (map the mock → GTR)

`GtrTheme` dark. Hex from `packages/ui/brand-tokens.json` / `GtrColors` — no new hues.

| Mock | Token |
|------|--------|
| Canvas / header | `Steel` `#12151C` |
| Ticket / tiles | `SteelLift` `#1E2430` |
| Pay, mode underline, selected tile border, VOID | `Primary` `#C8102E` |
| Body text | `Chalk` / `Silver` |
| FITS, Online, USD | `Accent` / `StockIn` `#0B6E4F` |
| VERIFY | `Warning` `#B45309` |
| NO FIT | `Danger` (same red; dim the tile) |
| Tile radius | `GtrShapes.small`–`medium` (8–16dp), not ShopKit 32dp pills |

Forbidden: cafe purple/orange/teal tiles, Material purple seed, £, “TABLE n”, kitchen KP, tax line.

### 15.3 Chrome-first implementation (P0 = golden Fake)

P0 ships **Fake RPC** data that *looks* like the mock (R35 latch, pad 40206-JF00A FITS, core-charge child, PAY USD 212.00). If the screenshot does not match the golden, P0 is not done — regardless of RPC wiring.

P1–P3 replace Fake with live/snapshot **without changing slot layout**.

### 15.4 Tile = lookup result (not decoration)

Each grid cell is a `SpareTile(state)`:

- Tap FITS + priced + WH2 covers → `add_cart_line` (sellable)
- Tap NO FIT → blocked (mismatch copy)
- Tap VERIFY / no latch → confirm sheet
- Long-press / INFO → fitment drawer
- Priced + OOS → `add_cart_line` as **quote-only**
- Unpriced → Needs price (no cart line)
- `In stock` facet on → shop gate; off → include OOS catalog (still no unpriced add)

Do not navigate to a customer PDP. Counter stays on this screen.

### 15.5 Sheets, not new destinations

Modals overlay the till: Pay (allocator), Quote send, Customer select, Fitment info, Price-check. Back returns to the same 3-pane. No hub, no ShopKit bottom nav.

### 15.6 Visual regression

- `@Preview` at **1280×800** (expanded) and **412×915** (compact) with Fake state matching the golden — **P0 gate**.
- Named Pay-sheet preview `PaySheet_SplitCashEcoCash` — **P2 gate** (no second PNG required).
- Roborazzi / screenshot CI — **Later**; do not block P0 on new screenshot infra.

### 15.7 Merge gates

A till PR is blocked if any of:

- Missing slot on expanded width
- Tax row, table number, or non-`GtrColors` tile palette
- Lookup results without OEM + fitment chip + WH2 qty
- Pay that does not open the allocator (or cash-only offline)
- Ported management/CoolMall POS composables
- Qty sourced from Meili
- Vehicle/PNC search hits added as cart lines
- Unpriced `add_cart_line` or checkout with quote-only lines

### 15.8 What “exactly like the image” does *not* mean

- Pixel-identical icon artwork (use simple line-art; licensed GT-R mark only)
- Fake OEMs in production (Fake is P0 only)
- Phone must show 4 columns (compact may be 2)
- Literal English sample prices — live prices from the price list, still `USD`/`ZIG` labeled like the Pay button

## 16. P1 contracts (locked 2026-08-15)

These close the gaps that would otherwise guess the finder. Fake P0 may hard-code `TillItem` JSON of this shape; live P1 must match.

### 16.1 One tile DTO + `list_pos_till_items`

**Do not** N+1 `lookupSaleableQtyByOem` per cell. **Do not** read qty from Meili/`search_catalog` hits.

New staff-only SECURITY DEFINER RPC (same migration as snapshot extend, RLS: sales|admin like other POS RPCs):

```
list_pos_till_items(
  p_warehouse_id uuid,
  p_source text,              -- shop_stock | oems | section
  p_in_stock_only boolean default true,
  p_chassis_code text default null,
  p_engine_code text default null,
  p_category text default null,   -- facet / PNC category_name
  p_oems text[] default null,     -- when source = oems
  p_section_key text default null, -- when source = section (pnc_code or section id from list_catalog_sections)
  p_limit int default 80,
  p_offset int default 0
) returns jsonb
```

Each item (`TillItem`) — **identical** to `pull_pos_offline_snapshot.items[]` after the additive extend:

| Field | Notes |
|-------|--------|
| `stock_item_id`, `oem_part_number`, `description`, `uom_id` | Identity |
| `unit_price`, `core_charge`, `currency` | Retail/default list; `unit_price` null or ≤0 = unpriced |
| `saleable_qty` | WH2 (or `p_warehouse_id`) from `stock_levels` only |
| `bin_code` | Nullable; from pick/bin if present |
| `pnc_code`, `category_name`, `superseded_by` | Nullable |
| `chassis_codes` text[], `engine_codes` text[] | Distinct codes from `part_fitment` for this OEM |

`shop_stock`: priced rows; if `p_in_stock_only` then `saleable_qty > 0`. Optional category. If chassis/engine passed, SQL **may** prefilter to Fits ∪ Verify (zero fitment rows); client still re-badges with §16.4.

`oems`: hydrate search/scan hits (preserve request order). Missing OEM → omit (do not invent).

`section`: parts in that EPC/PNC section, same qty/price join.

Online SHOP STOCK tab calls this (or reads the last snapshot if pulled this session and not stale > N min — implementation may use snapshot as cache of the same DTO). Offline uses snapshot only.

Rejected: separate shop vs offline mappers; dumping full `search_catalog` onto the grid.

### 16.2 Search / browse hit routing

`search_catalog` returns `PartHit | VehicleHit | PncHit`. Grid tiles are **only** hydrated `TillItem`s.

| `hit.type` | Action |
|------------|--------|
| `part` | Collect OEMs → `list_pos_till_items(oems)` → tiles |
| `vehicle` | **Latch** from `chassis_code`, `engine_code`, `production_year`, `model_variant`, `vin_prefix`. Switch tab to SHOP STOCK (filtered). Never `add_cart_line` |
| `pnc` | Switch tab to EPC; open section `pnc_code`; `list_pos_till_items(section)` |

VIN/PNC tab query picker: 11–17 alphanumeric starting JN/1N/… → `vin`; 4–6 digit → `pnc`; else `model`. SCAN/OEM tab always `part` (plus OE xref already inside that mode).

### 16.3 Quote-intent vs Pay (cart stays the document)

`create_pos_quotation_from_cart` **copies an open cart**. `add_cart_line` does **not** check WH2 qty (stock issues at **checkout**). Therefore:

| Line class | How it gets on the ticket | Pay? |
|------------|---------------------------|------|
| **sellable** | priced AND `saleable_qty >= line qty` AND (Fits or confirmed Verify) | Counts toward PAY |
| **quote-only** | priced AND (WH2 shortfall OR cashier chose Quote) | Blocks PAY |
| **rejected** | unpriced OR No fit | No line |

- Unpriced: no `add_cart_line` (avoid $0 invoices). Tile = Needs price.
- Mixed sellable + quote-only → primary CTA is **QUOTE** (same red slot). Secondary text: “Remove quote-only lines to take payment.”
- All sellable → **PAY** → TenderAllocator → `checkout_pos_cart_with_tenders`.
- Any quote-only → **QUOTE** → `create_pos_quotation_from_cart` → `send_pos_quotation` sheet → **`park_pos_cart` then `create_pos_cart`** (fresh till). Do not checkout. Orders lists the QT-.
- Cashier may force Quote on an all-sellable ticket (customer wants paper, not pay).
- Client must not call checkout if any quote-only line remains (checkout would consume stock / fail shortfall).

Offline: quote-only and live quotes are **online-only**. Offline ticket must be all sellable cash.

### 16.4 Fitment predicate (client SoT)

Latch fields: `chassis_code`, `engine_code` (optional `vin_prefix`, `model_variant`, `production_year`). Year is **not** in v1 match (no year range on `part_fitment` in this contract).

Normalize: trim, uppercase.

```
if latch.chassis is blank → VERIFY
codes = item.chassis_codes
engines = item.engine_codes
if codes is empty → VERIFY          // unpublished / identity-only OEM
chassisOk = any(codes) equals latch.chassis
if not chassisOk → NO_FIT
if latch.engine is blank OR engines is empty → FITS   // chassis matched; engine unspecified
if any(engines) equals latch.engine → FITS
else → NO_FIT
```

Re-evaluate on every latch change **without** refetch (use arrays on `TillItem`). SQL prefilter on `shop_stock` is an optimization, not a second truth.

Tests (Must): R35+VR38DETT vs pad with chassis R35 engine VR38DETT → FITS; same pad chassis Y62 only → NO_FIT; OEM with empty chassis_codes → VERIFY; no latch → VERIFY.

### 16.5 Money at the till

`TenderAllocator` uses integer **minor units** (cents). Convert server `numeric` at the `:pos-api` edge (`round half-up` to 2 dp → cents). RPC payloads send numeric strings/numbers at 2 dp. No float `Double` in allocator tests.

### 16.6 Visual gates (no extra PNGs)

| Gate | Artifact |
|------|----------|
| Expanded till | `@Preview` 1280×800 Fake vs golden mock |
| Compact till | `@Preview` 412×915, 2-col grid, sticky ticket — layout constants only |
| Pay sheet | `@Preview` `PaySheet_SplitCashEcoCash`: due / remaining / change, mode chips, Fill rest, equal split remainder on last line |

Roborazzi Later.

### 16.7 P1 unit tests (Must)

- `FitmentRules` matrix (§16.4)
- Hit router: vehicle → latch, pnc → section, part → oems
- Ticket CTA: all sellable → PAY; any quote-only → QUOTE; unpriced tap does not add
- `TillItem` JSON parse matches snapshot + `list_pos_till_items`


