# §H + cross-app continuation

**Autonomy (user binding, 2026-08-12):** When §H completes, move on to next roadmap steps **without waiting for human confirmation**. After each item’s DoD + verifier + living docs, start the next immediately. After actionable §H items are Done or honestly deferred, continue from master ERP plan / ENHANCEMENTS / BUGS / procurement optional follow-ups. Apply changes across **all ERP platform apps** as needed (web, android-management, android-delivery, android-customer, ios, packages, bridges, supabase). Stop only for irreversible exclusion reopen (e.g. ZIMRA counsel) or missing secrets that cannot be invented.

# §H + cross-app ERP parity (post A–G)

**Status:** Active — A–G verified Done under `docs/prompts/PRIME_AGENT_NISSAN_ORCHESTRATION.md`.  
**Authority:** Orchestration §H; procurement plan §5 optional; `BUGS.md`; `ENHANCEMENTS.md` deferred rows.  
**Laws:** NO ZIMRA; NO payroll tax; Bridge-First; AI never writes money/auto-POs; principal ≠ agency; thin vertical ≠ stubs; `may_start(N+1)` only after verifier PASS + living docs.

---

## 1. Frozen §H backlog (dependency order)

| ID | Item | Freeze DoD (Done only with evidence) | Depends on |
| --- | --- | --- | --- |
| **H8** | Fund-release insert-once | `approve_purchase_order` inserts `procurement_fund_releases` once; **no** `ON CONFLICT … DO UPDATE` rewriting `amount` / `amount_minor`; smoke proves second approve is no-op / non-mutating on money | — |
| **H-PARITY-WH2** | Android POS WH2 pick (A–G gap) | `PosViewModel` / warehouse list only saleable **WH2** (`role_code` storefloor); WH1 not pickable; copy matches web | H8 optional |
| **H2** | Android preferred-supplier PO | Native create/submit preferred PO on `android-management` (roster + quoted lines); not web-only hub deep-link; Bridge-First; no RFQ-win gate | H-PARITY-WH2 optional |
| **H4** | B-MONEY-1 dual-read → cutover | Dual-write then cutover plan per money surface; never big-bang; new APIs `amountMinor`+currency; PO path already dual-write | **H8** |
| **H5** | B-MAP-1 MapLibre SoR | Courier already MapLibre primary. Customer Android (+ bridges) MapLibre render SoR; Google = deprecated fallback only. **H5-iOS** MapLibre Native SoR; MapKit deprecated fallback | — |
| **H6** | B-OSRM-1 compose/data | `routing` profile + `infra/satellites/osrm/prepare.sh`; runnable when graph present; documented; clients prefer `OSRM_URL` | H5 helpful |
| **H1** | Temporal worker binary | Host process runs `DeliveryDispatchWorkflow` / `DELIVERY_DISPATCH_WORKFLOW` calling existing activities; Edge bridge remains; no Fleetbase | Package SM Done (A–G) |
| **H3** | Promptfoo real-provider CI | Offline safe-narrative CI job always; optional real model when secrets present; human-promote unchanged | Epic E Done |
| **H7** | B-PS-1 PowerSync live SDK | Mobile SDK wired to existing rules; offline queue intents only (no journal upload) | **Done (Android mgmt)** |
| **H9** | Chatwoot / Metabase | Tier-2 satellites — **defer** unless explicit ticket | — |
| **H-ZIMRA** | ZIMRA / FDMS | **Excluded** until counsel ADR — never implement | — |

---

## 2. ERP platform app matrix

Legend: **N** = need apply/adapt · **—** = N/A · **OK** = already meets DoD · **DEF** = defer · **COND** = code ready, runtime evidence blocked

| Item | web | android-management | android-delivery | android-customer | ios | packages/* | bridges/ | supabase/ |
| --- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| H8 fund-release insert-once | — | — | — | — | — | — | — | **OK** |
| H-PARITY-WH2 POS | OK | **OK** | — | — | — | — | — | — |
| H2 preferred-PO | OK | **OK** | — | — | — | OK vocab | — | OK RPCs |
| H4 B-MONEY-1 | **OK** dual-read + API cutover habit | **OK** POS dual-read | **N** | **OK** cart dual-read | **OK** dual-read | **OK** ApiMoney + dual-write helpers | — | **OK** cart/invoice + JE/payment + PO/fund dual-write |
| H5 B-MAP-1 | OK track | — | OK primary | **OK** MapLibre address pick | **OK** MapLibre SoR (MapKit fallback) | — | **OK** maps-nav + iOS MapsNav | — |
| H6 B-OSRM-1 | **OK** docs/compose | — | prefer OK | prefer if maps | — | OK osrm | OK fetcher | — + **OK** infra prepare |
| H1 Temporal worker | — | — | — | — | — | **OK** host | — | Edge OK |
| H3 Promptfoo CI | — | — | — | — | — | — | — | — + **OK** `.github`/promptfoo (offline default) |
| H7 B-PS-1 | — | **OK** Fake/Live SDK | maybe | maybe | maybe | — | — | powersync rules OK |
| H9 Chatwoot/Metabase | DEF | DEF | DEF | DEF | DEF | DEF | DEF | DEF |
| H-ZIMRA | **never** | **never** | **never** | **never** | **never** | **never** | **never** | **never** |
| D-57 checkout parity | OK | — | — | **N** | **N** | OK payments | — | — |
| Meili call-sites | OK | optional | — | OK | OK | OK client | — | Edge OK |

---

## 3. Execution rules

1. One coding lane at a time (`rufler.yaml`).
2. After each item: `/security-reviewer` (if auth/RLS/money/maps/secrets) → `/verifier` → living docs (`CHANGELOG`/`ENHANCEMENTS`/`BUGS` as needed).
3. Do not mark DoD `[x]` without evidence.
4. Cross-app: when web landed a habit in A–G, apply to other ERP apps in matrix before calling parity Done.

---

## 4. Progress log

| ID | Status | Evidence |
| --- | --- | --- |
| H8 | **Done** | Migration `20260812080000_*` + `supabase/tests/fund_release_insert_once_smoke.sql` — NOTICE `H8 fund-release insert-once smoke OK` after `supabase start` (2026-08-13). Second approve / conflict does not mutate `amount_minor`. Epic A smoke also OK. |
| H-PARITY-WH2 | **Done** | Gradle `PosSaleableWarehouseTest` PASS; `PosViewModel` → `listSaleableWarehouses`; living docs updated. |
| H2 | **Done** | Native `PreferredPoScreen` + `listPreferredSuppliers` / `create_purchase_order` / `submit_purchase_order`; hub → Preferred supplier PO; Fake stubs; not RFQ-gated; Bridge QR. **Verifier 2026-08-12:** `:core:rpc:testDebugUnitTest --tests …PreferredPoHelpersTest` + `:feature:procurement:compileDebugKotlin` → BUILD SUCCESSFUL. |
| H5 | **Done (Android + iOS)** | Android: Customer `AddressPickMap` / `MapLibreAddressPickMap` `useMapLibre=true` default; Google deprecated. **H5-iOS:** `bridges/ios/MapsNav` MapLibre SoR; Address + DeliveryTrack; MapKit deprecated (`USE_MAPLIBRE=false` / load fail). Tests: `AddressPickMapCaptionTests`. Mac: `xcodebuild` MapsNav test + GTRCustomer build (Windows: code + docs only). Exclusions clean. |
| H3 | **Done** | `.github/workflows/promptfoo.yml` — `npm run gate` (safe-narrative asserts) + `promptfoo eval` offline default (Epic E); optional real-provider job when `OPENAI_API_KEY` / `GEMINI_API_KEY` / `PROMPTFOO_PROVIDER` secrets present. Human-promote unchanged. Local verify: `cd promptfoo && npm run gate` (3/3 PASS). |
| H6 | **Done** | Compose `osrm` under `--profile routing`; prepare script (Git Bash `MSYS_NO_PATHCONV`); Zimbabwe graph built; `gtr-osrm` Up; route smoke `code=Ok` Harare sample (2026-08-13). Clients already prefer `OSRM_URL`. |
| H1 | **Done** | `@gtr/delivery-dispatch-worker` — Temporal host for `DeliveryDispatchWorkflow`; SQL activities via assign-bridge; Edge `delivery-dispatch-cycle` unchanged; no Fleetbase. Verifier: `pnpm --filter @gtr/delivery-dispatch-worker test` (2/2) + workflow `tsc` PASS. Live run needs `TEMPORAL_ADDRESS` + Supabase service role. |
| H4 | **Done (cutover habit)** | Slices 1–7: dual-write (PO/fund + cart/invoice + JE/payment) + dual-read (web, Android POS, iOS) + shared API contracts prefer/require `amountMinor` (`ApiMoney`/`LegacyMoney`, settlement/allocation/PO dual-write RPC fields). Verifier: `packages/shared` 37/37 + `packages/payments` 11/11 PASS (2026-08-14). **Follow-on:** Android customer cart dual-read (`MoneyDualRead` + `getOpenCart` minors) — see progress below. **Deferred:** drop NUMERIC columns; SQL RPCs still read major (clients dual-write minors). See `docs/plans/2026-08-13-h4-money-dual-read-cutover.md`. |
| H4-cust | **Done (Android customer cart)** | Customer `MoneyDualRead` + `CartLineSummary` minors; `getOpenCart` selects `unit_price_minor`/`line_total_minor`; Fake dual-seeds; cart UI display helpers. Unit: `:core:rpc:testDebugUnitTest --tests …MoneyDualReadTest`. Delivery dual-read still **N**. |
| H7 | **Done (Android management)** | `com.powersync:core` + `GtrPowerSyncSchema` / `GtrPowerSyncConnector` (no JE upload; RPC intents via OfflinePos); Fake when `POWERSYNC_URL` unset; openDatabase when set. Unit: `PowerSyncOfflineContractTest` PASS (2026-08-14). Cloud E2E needs secrets. Plan: `docs/plans/2026-08-14-h7-powersync-live-sdk.md`. |
| H9, H-ZIMRA | deferred / excluded | H9 Chatwoot/Metabase deferred. H-ZIMRA never. Remaining follow-ups outside actionable §H: Mac `xcodebuild` H5-iOS compile evidence; PowerSync cloud E2E secrets; NUMERIC column drop (H4 deferred). |

---

*Owned by Manager orchestration. Product code via lane agents only.*
