# Autonomous agent prompt — Standalone adaptive POS (green gates to Done)

> **Paste / `@`-reference this document as the primary instruction set** for an autonomous Cursor agent (or multi-session epic). Execute **phase by phase**. Do **not** skip a green gate. Do **not** start the next phase until the current gate is green.

- **Date:** 2026-08-15
- **Status:** ready for development
- **SoT (read first, cite, do not re-litigate):**
  - Plan: [`docs/plans/2026-08-15-standalone-adaptive-pos.md`](./2026-08-15-standalone-adaptive-pos.md) — especially **§15–§16**
  - ADR: [`docs/decisions/2026-08-15-standalone-adaptive-pos.md`](../decisions/2026-08-15-standalone-adaptive-pos.md)
  - Till rule: [`.cursor/rules/android-pos-till.mdc`](../../.cursor/rules/android-pos-till.mdc)
  - Golden mock: `docs/plans/assets/2026-08-15-gtr-pos-till-golden.png` (or project assets `gtr-pos-till-proposed-look.png`)
- **Skills:** `/token-discipline` always; `/nissan-fast-parser` + `/parts-catalog-ingestion` when wiring fitment; `/qr-inventory-workflow` for scan/print/drawer; `/accounting-ledger` only if till-float/refund JE; **do not** auto-invoke `/ui-ux-pro-max`
- **Hard exclusions:** No ZIMRA · no tax line · no payroll tax · no HTML5/browser camera · no Flutter/Expo · no CoolMall/management POS Compose port · no Meili qty · no second cart/ledger

---

## Mission

Ship a **dedicated greenfield Android POS** at `apps/android-pos/` that:

1. Matches the **golden till chrome** (cafe 3-pane shell, GTR steel/red) on tablet and reflows on phone.
2. Finds **correct Nissan spares** via vehicle latch + 4-way lookup (scan/OEM, shop stock, EPC, VIN/PNC) with fitment chips and WH2 stock honesty.
3. Adds to **cart (sellable)** or **quote-intent** per §16.3; Pay uses **TenderAllocator** (integer cents) → existing checkout/tender RPCs.
4. Works **offline** (SQLCipher catalog/stock + cash outbox) with **PosSyncManager** (replay → then pull).

**Keep:** Supabase sales SoR, RLS, existing cart/checkout/park/quote/refund/search/diagram/offline replay RPCs, `bridges/`.  
**Discard:** All current POS UI (management `feature/pos`, web `staff-pos-shell` look). Do not extract or restyle it.

---

## Operating rules (always on)

1. **Lane:** Until `@pos_app_agent` exists in `rufler.yaml`, use `@management_app_agent` with path override `apps/android-pos/**`. Backend additive migration → `@backend_agent`. Scan/print/drawer → `@hardware_mobile_agent`.
2. **One phase at a time.** After each phase: run that phase’s green-gate checklist; write a short `## Phase Pn status` note at the bottom of this file or in the PR body (GREEN / BLOCKED + evidence).
3. **Stop and report** if a green gate fails twice after a focused fix — do not invent a second SoR or skip the gate.
4. **Targeted diffs.** Full files only when new. Match monorepo Gradle/Kotlin style from `apps/android-delivery` / `apps/android-management` (modules, Fake/Live), **not** their POS screens.
5. **Integer money** in `TenderAllocator` (minor units). Convert at `:pos-api` edge.
6. **Qty honesty:** Postgres `stock_levels` / snapshot only — never Meili/`search_catalog` hit qty.
7. After non-trivial work: `/verifier`. After migrations: `/supabase-rls-auditor`. Before epic close: `/security-reviewer` on the POS diff.

---

## Architecture (do not deviate)

```
apps/android-pos/
  app/                 login, WindowSizeClass shell, optional kiosk (P4)
  :pos-api/            TillItem DTOs + Fake/Live RPC client (slim — not management RpcClient god)
  feature-till/        TillScaffold + ticket + rail
  feature-lookup/      latch, modes, FitmentRules, hit router, SpareTile
  feature-pay/         TenderAllocator + Pay sheet
  feature-orders/      parked + quotations
  feature-customer/    select / bind
  sync/                SQLCipher + PosSyncManager (P3)
```

**TillScaffold** is the only till chrome. Sheets (Pay, Quote, Customer, Fitment info, Price-check) overlay it. No hub, no ShopKit bottom nav, no customer PDP navigation from the till.

Expanded weights: finder `0.58` · ticket `0.37` · rail `0.05`.  
Theme: `GtrTheme` **dark** — `GtrColors` / `packages/ui/brand-tokens.json` only.

---

## Frozen backend (consume)

| Concern | RPC / asset |
|---------|-------------|
| Auth | `resolve_staff_login_email` + GoTrue |
| Cart | `create_pos_cart`, `add_cart_line`, `add_cart_line_from_qr`, `park_pos_cart`, `resume_pos_cart` |
| Pay | `checkout_pos_cart`, `checkout_pos_cart_with_tenders`, `settle_invoice_tenders` |
| Quote | `create_pos_quotation_from_cart`, `send_pos_quotation`, `convert_pos_quotation_to_cart` |
| Search | `search_catalog` (`part`\|`vin`\|`model`\|`pnc`) |
| EPC tree | `list_catalog_makers/models/variants/sections`, `get_catalog_diagram` |
| Offline | `pull_pos_offline_snapshot`, `replay_offline_pos_sale` |
| WH | WH2 storefloor only for sell; quarantine never |

### Backend additive (required for P1 live — same migration)

1. **`list_pos_till_items(...)`** — staff SECURITY DEFINER; sources `shop_stock` \| `oems` \| `section`; returns `TillItem[]` (§16.1).
2. **Extend `pull_pos_offline_snapshot` items** to the **same** `TillItem` shape (`pnc_code`, `category_name`, `superseded_by`, `bin_code`, `chassis_codes[]`, `engine_codes[]`).
3. RLS/grants in the **same** migration. Smoke SQL test. No second ledger.

---

## §16 contracts (restate — binding)

### Hit routing

| `search_catalog` type | Action |
|-----------------------|--------|
| `part` | Hydrate `list_pos_till_items(oems)` → tiles |
| `vehicle` | **Latch** → switch SHOP STOCK — never add |
| `pnc` | Open EPC section → `list_pos_till_items(section)` |

### Fitment (client SoT)

No latch / empty `chassis_codes` → **VERIFY**. Chassis mismatch → **NO_FIT**. Chassis match (+ engine if both present) → **FITS**. Year not in v1.

### Quote-intent vs Pay

| Class | Rule |
|-------|------|
| sellable | priced ∧ WH2 ≥ qty ∧ (FITS \| confirmed VERIFY) → PAY counts |
| quote-only | priced ∧ (OOS ∨ force Quote) → may `add_cart_line`; CTA = **QUOTE** |
| rejected | unpriced ∨ NO_FIT → no line |

QUOTE → `create_pos_quotation_from_cart` → send sheet → `park_pos_cart` + new `create_pos_cart`. Never checkout with quote-only lines. Offline: cash sellable only.

---

## Phase machine (execute in order)

```text
P0 chrome Fake → P0b backend TillItem RPC → P1 lookup live → P2 pay → P3 offline → P4 polish → Follow-on → EPIC GREEN
```

---

### P0 — App shell + golden Fake till

**Owner:** POS lane (`apps/android-pos/**`)  
**Goal:** Visual product exists. Fake data looks like the golden mock. No live backend required.

**Do**

1. Scaffold Gradle multi-module app under `apps/android-pos/` (applicationId e.g. `co.zw.nissangtr.pos`).
2. Wire `GtrTheme` dark + `packages/android-ui` tokens.
3. Implement `TillScaffold` with **all** expanded slots (§15.1).
4. Fake `:pos-api`: R35 latch, tile `40206-JF00A` FITS, core-charge child, ticket total **USD 212.00**, Online status.
5. Compact reflow: finder full + sticky ticket bar; 2-col grid OK.
6. Staff login shell (Fake auth OK for P0): land on till.
7. `@Preview` `Till_Expanded_1280x800` and `Till_Compact_412x915`.

**Do not**

- Port management/CoolMall POS screens.
- Implement live RPCs yet (stubs/Fake only).
- Skip lookup chrome for a “Pay-only” stub — finder modes + tiles must be visible.

#### GREEN GATE P0

- [ ] `./gradlew :apps:android-pos:assembleDebug` (or module-equivalent) succeeds
- [ ] Expanded preview shows: header latch · 4 modes · search · facets · grid tiles with OEM+fitment+WH2 · ticket with core-charge · PARK/VOID · red PAY · rail · status — **no tax row**
- [ ] Compact preview: sticky ticket, no missing primary CTA
- [ ] Colors only from `GtrColors` (manual greps: no Material purple seed, no cafe teal/orange tile palette)
- [ ] No imports from `apps/android-management/.../pos` Compose UI
- [ ] Unit test: window-size → expanded vs compact layout flag

**Exit:** P0 GREEN → proceed to P0b (may parallelize with P0 finish if Fake `TillItem` JSON already matches §16.1).

---

### P0b — Backend `TillItem` SoR

**Owner:** `@backend_agent`  
**Goal:** One tile JSON for online grid and offline snapshot.

**Do**

1. Migration: `list_pos_till_items` + extend `pull_pos_offline_snapshot` item shape (§16.1).
2. Grants + RLS consistent with other POS staff RPCs.
3. `supabase/tests/..._pos_till_items_smoke.sql` (or extend offline smoke).
4. Document response example in migration COMMENT.

#### GREEN GATE P0b

- [ ] Migration applies on local/reset
- [ ] Smoke: `shop_stock` returns priced WH2 rows; `oems` hydrates known OEM; qty from `stock_levels` only
- [ ] Snapshot items include `chassis_codes` / `engine_codes` (arrays, possibly empty)
- [ ] `/supabase-rls-auditor` clean for new objects
- [ ] No Meili / FTS qty fields in payload

**Exit:** P0b GREEN → P1.

---

### P1 — Lookup, fitment, add / quote-intent

**Owner:** POS lane + consume P0b  
**Goal:** Finder is real. Wrong-part and wrong-stock paths blocked per §16.

**Do**

1. `FitmentRules` + unit matrix (§16.4 / §16.7).
2. Hit router: part / vehicle / pnc (§16.2).
3. Live (or Fake→Live switch): `search_catalog`, `list_pos_till_items`, EPC tree RPCs.
4. Latch sticky; re-badge tiles without refetch.
5. Add rules: sellable / quote-only / Needs price / NO_FIT block; supersession swap banner.
6. Scan debounce (~400ms) + OEM normalize; Bridge scan stub OK if hardware later.
7. Ticket CTA: all sellable → **PAY**; any quote-only → **QUOTE** (wire quote RPCs; send sheet can be minimal).
8. Core-charge child visible after add.

#### GREEN GATE P1

- [ ] Unit: FitmentRules matrix (FITS / NO_FIT / VERIFY cases)
- [ ] Unit: hit router (vehicle→latch, pnc→section, part→oems)
- [ ] Unit: CTA (sellable→PAY, quote-only→QUOTE, unpriced→no add)
- [ ] Manual/Fake: latch R35 filters shop stock; NO_FIT cannot silent-add
- [ ] Manual: priced OOS adds quote-only; QUOTE creates QT- and parks cart
- [ ] Grep gate: no `saleable_qty` / `qty` taken from Meili hit mapping
- [ ] Till layout **unchanged** from P0 (no new destinations)

**Exit:** P1 GREEN → P2. **Do not** polish Pay chrome before this gate.

---

### P2 — Ticket ops + TenderAllocator + checkout

**Owner:** POS lane; hardware bridge for print/scan if not stubbed  
**Goal:** Real money path. Split tenders correct. Receipts.

**Do**

1. Pure Kotlin `TenderAllocator` (minor units): fill rest, split equal (remainder on last), cash change not posted.
2. Pay sheet UI + `@Preview` `PaySheet_SplitCashEcoCash`.
3. `checkout_pos_cart_with_tenders` with applied amounts only.
4. Live rails (EcoCash/Paynow/ContiPay): intent **per slice** → settle → single checkout; failure does not checkout.
5. Park / resume / void / discount (manager reauth RPCs).
6. Receipt WhatsApp/email fields on checkout.
7. Customer select / bind sheet.
8. Offline mode disables non-cash on Pay sheet (“needs connection”).
9. Bridge: scan + ESC/POS print path (Fake printer OK in unit tests).

#### GREEN GATE P2

- [ ] Unit: equal split 1000¢ / 3 → 334+333+333 (or documented remainder-on-last)
- [ ] Unit: cash tendered > due → applied = due, change = excess; RPC payload applied only
- [ ] Unit: remaining ≠ 0 → confirm disabled
- [ ] Preview Pay sheet shows due / remaining / change / mode chips
- [ ] Integration/Fake: split cash+bank posts; sum equals due
- [ ] Credit-hold / on_hold → not treated as paid
- [ ] No tax row; currency label on Pay (`USD`\|`ZIG`)
- [ ] Bridge-First: no HTML5 camera / Web Bluetooth

**Exit:** P2 GREEN → P3.

---

### P3 — Offline catalog + PosSyncManager

**Owner:** POS lane  
**Goal:** Counter sells cash offline; reconnect is safe and automatic.

**Do**

1. SQLCipher DB (Keystore-wrapped passphrase): catalog/stock, meta, outbox.
2. Persist extended snapshot `TillItem`s.
3. Offline sell: snapshot price, local qty decrement, cash only, `client_sale_id` UUID.
4. `PosSyncManager`: **replay outbox first** → then `pull_pos_offline_snapshot`.
5. Triggers: connectivity (validated), till open, WorkManager, rail Sync now.
6. Conflict UI (`offline_price_conflict` / stock short); restore local qty on fail; never invent JE.
7. Status banner: Online · Offline · snapshot time · Syncing N · Conflict N.
8. Offline: disable VIN/EPC browse unless fitment index present; disable EcoCash/etc.

#### GREEN GATE P3

- [ ] Unit: sync order enforced (replay before pull) — test double / fake client call order
- [ ] Unit: idempotent replay (duplicate `client_sale_id` safe)
- [ ] Manual/Fake: offline cash sale → reconnect → invoice exists; snapshot refreshed after
- [ ] Offline Pay: only cash enabled
- [ ] Conflict row stays in outbox; attendant can see it
- [ ] No manager tokens / password hashes in SQLCipher

**Exit:** P3 GREEN → P4.

---

### P4 — Counter-complete (Should items that finish the product)

**Owner:** POS + hardware + light management follow-on  
**Goal:** Shopfloor-ready till.

**Do (Must of P4)**

1. Price-check mode (rail) — no cart mutation.
2. Bin on tile from `TillItem.bin_code` / pick hints.
3. Orders list: parked + quotations; resume/convert paths.
4. Idle lock + in-app reauth.
5. Supersession + Info fitment drawer (diagram thumbnail online).

**Do (Should — include if time; else leave checked as deferred with ADR note)**

6. Kiosk Lock Task / HOME / Device Owner moved onto this APK (from management tablet plan).
7. Till open/close float via existing `open_account_period` / `close_account_period` (cash sales; 1110 ≠ 1120).
8. Returns → quarantine via `post_pos_refund` (no direct exchange).
9. Chassis shortcut chips from `vehicle_master`.

#### GREEN GATE P4

- [ ] Price-check does not call `add_cart_line`
- [ ] Orders can resume park and open QT-
- [ ] Idle lock returns to login chrome without process death
- [ ] Acceptance checklist in plan §13 mostly checked (Should items marked Done or explicitly Deferred)
- [ ] `/verifier` pass on `apps/android-pos/**` (+ migration if touched)

**Exit:** P4 GREEN → Follow-on.

---

### Follow-on — Isolation + lane hygiene

**Do**

1. Add `@pos_app_agent` paths to `rufler.yaml` + `AGENTS.md`.
2. Management: remove/hide till chrome; optional “Open POS” deep link / package.
3. Web `/staff/pos` remains fallback — **do not** restyle in this epic unless explicitly asked.

#### GREEN GATE Follow-on

- [ ] `rufler.yaml` lists `apps/android-pos/**`
- [ ] Management no longer ships the old dual-pane POS as primary sales UI (deep link OK)
- [ ] `/security-reviewer` on POS + migration diff — no secrets, Bridge-First intact, RLS OK

---

## EPIC GREEN (definition of Done)

All of the following:

1. P0 → P4 gates GREEN (Should items Done or Deferred with reason).
2. Plan §13 acceptance boxes checked or Deferred.
3. Golden visual contract: expanded till still matches §15 slots (human overlay vs mock).
4. Live path: latch → shop stock FITS → add → split tender checkout → receipt fields.
5. Offline path: cash sale → reconnect → replay → pull.
6. No ZIMRA/tax/HTML5/Meili-qty/management-POS-port regressions (grep + `/verifier`).
7. Branch ready for PR: focused commits; no unrelated catalog-apk / FlareSolverr noise.

---

## Anti-patterns (instant fail)

- Building Pay before P1 lookup gate
- Cafe category tiles as primary IA without OEM/fitment/WH2
- Dumping `search_catalog` vehicles onto the cart
- `Double` money in allocator tests
- Pulling snapshot before draining outbox
- Copying `PosDualPaneScreen` / CoolMall till
- New cart table or parallel checkout RPC
- Inventing fitment year matching in v1

---

## Session bootstrap (copy into agent chat)

```text
You are implementing the Nissan GTR standalone adaptive POS.
SoT: docs/plans/2026-08-15-standalone-adaptive-pos.md (§15–§16),
docs/decisions/2026-08-15-standalone-adaptive-pos.md,
.cursor/rules/android-pos-till.mdc,
docs/plans/2026-08-15-standalone-adaptive-pos-autonomous-prompt.md.

Execute the lowest incomplete phase (P0 → Follow-on). Stop at its GREEN GATE.
Do not re-litigate locked decisions. No ZIMRA, no tax row, no HTML5 camera,
no management POS UI port, no Meili qty.

When the gate is green, report evidence and stop for confirmation before the next phase
unless the user said “run through to EPIC GREEN without pausing”.
```

**Autonomous full-run variant:** replace the last sentence with:  
`Run through to EPIC GREEN without pausing between phases; still enforce every green gate and fix failures before continuing.`

---

## Phase status log (agent updates)

| Phase | Status | Evidence / date |
|-------|--------|-----------------|
| P0 | GREEN | 2026-08-15 — `apps/android-pos` scaffold; `assembleDebug` OK; `TillLayoutResolverTest` 3/3; greps clean (no purple/cafe palette, no management POS imports); previews `Till_Expanded_1280x800` / `Till_Compact_412x915`; Fake R35 + `40206-JF00A` FITS + USD 212.00 |
| P0b | GREEN | 2026-08-15 — `supabase/migrations/20260815200000_pos_till_items.sql` applied via `migration up --local`; smoke `pos_till_items_smoke.sql` OK; rls-auditor CLEAN; TillItem adds pnc/category/superseded/bin/chassis_codes/engine_codes |
| P1 | GREEN | 2026-08-15 — FitmentRulesTest, HitRouterTest, AddLineRulesTest/TicketCtaAndAddTest; Fake→Live PosClient; no Meili qty on CatalogHitDto; assembleDebug OK; till slots unchanged |
| P2 | GREEN | 2026-08-15 — TenderAllocator equal-split remainder-on-last; cash change applied-only RPC; PaySheet_SplitCashEcoCash preview; CheckoutFakeIntegrationTest (split cash+bank, credit-hold on_hold, rail fail no checkout, offline cash-only); Fake bridges |
| P3 | GREEN | 2026-08-15 — `:sync` PosSyncManager replay→pull; InMemory + SqlCipherOfflineStore (Keystore passphrase); PosSyncManagerTest order/idempotent/conflict; Fake pull/replay RPCs; status banner Syncing/Conflict |
| P4 | GREEN | 2026-08-15 — Price-check sheet (no add); Orders parked+QT; IdleLockController; FitmentInfoSheet supersession swap; bin on tiles; Should items Deferred in README + plan §13 |
| Follow-on | GREEN | 2026-08-15 — `@pos_app_agent` in rufler.yaml + AGENTS.md; management OpenStandalonePosScreen deep-link (no dual-pane primary) |
| EPIC GREEN | GREEN | 2026-08-15 — P0→P4 + Follow-on gates satisfied; §13 Must checked; Should Deferred with reason; verifier PASS (51 unit tests); security CLEAN; rls-auditor CLEAN; `allowBackup=false` |
| Post-EPIC completion | GREEN | 2026-08-15 — Auth Fake\|Live + LivePosClient HTTP; SqlCipher production store; CameraX/ESC-POS/drawer bridges; Lock Task kiosk; till float 1120 (+ migration sales/warehouse 1120-only); post_pos_refund sheet; chassis chips; management session handoff; web notice skipped (no restyle). Verifier: unit tests + assembleDebug; security: Bridge-First, allowBackup=false, handoff tokens not logged. |
