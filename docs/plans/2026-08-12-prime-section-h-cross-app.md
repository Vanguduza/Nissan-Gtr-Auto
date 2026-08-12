# §H + cross-app continuation

**Autonomy (user binding, 2026-08-12):** When §H completes, move on to next roadmap steps **without waiting for human confirmation**. After each item’s DoD + verifier + living docs, start the next immediately. After actionable §H items are Done or honestly deferred, continue from master ERP plan / ENHANCEMENTS / BUGS / procurement optional follow-ups. Apply changes across **all ERP platform apps** as needed (web, android-management, android-delivery, android-customer, ios, packages, bridges, supabase). Stop only for irreversible exclusion reopen (e.g. ZIMRA counsel) or missing secrets that cannot be invented.

# Â§H + cross-app ERP parity (post Aâ€“G)

**Status:** Active â€” Aâ€“G verified Done under `docs/prompts/PRIME_AGENT_NISSAN_ORCHESTRATION.md`.  
**Authority:** Orchestration Â§H; procurement plan Â§5 optional; `BUGS.md`; `ENHANCEMENTS.md` deferred rows.  
**Laws:** NO ZIMRA; NO payroll tax; Bridge-First; AI never writes money/auto-POs; principal â‰  agency; thin vertical â‰  stubs; `may_start(N+1)` only after verifier PASS + living docs.

---

## 1. Frozen Â§H backlog (dependency order)

| ID | Item | Freeze DoD (Done only with evidence) | Depends on |
| --- | --- | --- | --- |
| **H8** | Fund-release insert-once | `approve_purchase_order` inserts `procurement_fund_releases` once; **no** `ON CONFLICT â€¦ DO UPDATE` rewriting `amount` / `amount_minor`; smoke proves second approve is no-op / non-mutating on money | â€” |
| **H-PARITY-WH2** | Android POS WH2 pick (Aâ€“G gap) | `PosViewModel` / warehouse list only saleable **WH2** (`role_code` storefloor); WH1 not pickable; copy matches web | H8 optional |
| **H2** | Android preferred-supplier PO | Native create/submit preferred PO on `android-management` (roster + quoted lines); not web-only hub deep-link; Bridge-First; no RFQ-win gate | H-PARITY-WH2 optional |
| **H4** | B-MONEY-1 dual-read â†’ cutover | Dual-write then cutover plan per money surface; never big-bang; new APIs `amountMinor`+currency; PO path already dual-write | **H8** |
| **H5** | B-MAP-1 MapLibre SoR | Courier already MapLibre primary. Customer Android (+ iOS if maps) MapLibre render SoR; Google/MapKit = deprecated fallback only â€” do not reintroduce Google-as-SoR | â€” |
| **H6** | B-OSRM-1 compose/data | OSRM service runnable when map data present; documented in `infra/satellites`; clients already prefer `OSRM_URL` | H5 helpful |
| **H1** | Temporal worker binary | Host process runs `DeliveryDispatchWorkflow` / `DELIVERY_DISPATCH_WORKFLOW` calling existing activities; Edge bridge remains; no Fleetbase | Package SM Done (Aâ€“G) |
| **H3** | Promptfoo real-provider CI | CI job with real model provider + secrets; offline gate remains default locally; human-promote unchanged | Epic E Done |
| **H7** | B-PS-1 PowerSync live SDK | Mobile SDK wired to existing rules; offline queue intents only (no journal upload) | â€” |
| **H9** | Chatwoot / Metabase | Tier-2 satellites â€” **defer** unless explicit ticket | â€” |
| **H-ZIMRA** | ZIMRA / FDMS | **Excluded** until counsel ADR â€” never implement | â€” |

---

## 2. ERP platform app matrix

Legend: **N** = need apply/adapt Â· **â€”** = N/A Â· **OK** = already meets DoD Â· **DEF** = defer

| Item | web | android-management | android-delivery | android-customer | ios | packages/* | bridges/ | supabase/ |
| --- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| H8 fund-release insert-once | â€” | â€” | â€” | â€” | â€” | â€” | â€” | **N** |
| H-PARITY-WH2 POS | OK | **N** | â€” | â€” | â€” | â€” | â€” | â€” |
| H2 preferred-PO | OK | **N** | â€” | â€” | â€” | OK vocab | â€” | OK RPCs |
| H4 B-MONEY-1 | **N** | **N** | **N** | **N** | **N** | **N** shared/payments | â€” | **N** |
| H5 B-MAP-1 | OK track | â€” | OK primary | **N** | **N** | â€” | **N** maps-nav | â€” |
| H6 B-OSRM-1 | â€” | â€” | prefer OK | prefer if maps | â€” | OK osrm | OK fetcher | â€” + **N** infra |
| H1 Temporal worker | â€” | â€” | â€” | â€” | â€” | **N** host | â€” | Edge OK |
| H3 Promptfoo CI | â€” | â€” | â€” | â€” | â€” | â€” | â€” | â€” + **N** `.github`/promptfoo |
| H7 B-PS-1 | â€” | **N** | maybe | maybe | maybe | â€” | â€” | powersync rules OK |
| H9 Chatwoot/Metabase | DEF | DEF | DEF | DEF | DEF | DEF | DEF | DEF |
| H-ZIMRA | **never** | **never** | **never** | **never** | **never** | **never** | **never** | **never** |
| D-57 checkout parity | OK | â€” | â€” | **N** | **N** | OK payments | â€” | â€” |
| Meili call-sites | OK | optional | â€” | OK | OK | OK client | â€” | Edge OK |

---

## 3. Execution rules

1. One coding lane at a time (`rufler.yaml`).
2. After each item: `/security-reviewer` (if auth/RLS/money/maps/secrets) â†’ `/verifier` â†’ living docs (`CHANGELOG`/`ENHANCEMENTS`/`BUGS` as needed).
3. Do not mark DoD `[x]` without evidence.
4. Cross-app: when web landed a habit in Aâ€“G, apply to other ERP apps in matrix before calling parity Done.

---

## 4. Progress log

| ID | Status | Evidence |
| --- | --- | --- |
| H8 | **conditional** | Migration `20260812080000_fund_release_insert_once.sql` + smoke file; security PASS; payload uses stored money on conflict. **Smoke not run** (Docker daemon down). Full Done blocked on local `db:reset` + smoke. |
| H-PARITY-WH2 | **implemented** | Android: `listSaleableWarehouses` + `isPosSaleableWarehouse` (role_code/code WH2); `PosViewModel.loadWarehouses` uses saleable only. **Verifier:** `.\gradlew :core:rpc:testDebugUnitTest --tests co.zw.nissangtr.management.rpc.PosSaleableWarehouseTest`; code path `PosViewModel.loadWarehouses` â†’ `rpc.listSaleableWarehouses()` (not `listWarehouses`). |
| H2 â€¦ H-ZIMRA | open | â€” |

---

*Owned by Manager orchestration. Product code via lane agents only.*

