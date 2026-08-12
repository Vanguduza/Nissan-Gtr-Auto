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
| **H5** | B-MAP-1 MapLibre SoR | Courier already MapLibre primary. Customer Android (+ bridges) MapLibre render SoR; Google = deprecated fallback only. **H5-iOS** (MapKit → MapLibre Native) remains open / PARTIAL | — |
| **H6** | B-OSRM-1 compose/data | OSRM service runnable when map data present; documented in `infra/satellites`; clients already prefer `OSRM_URL` | H5 helpful |
| **H1** | Temporal worker binary | Host process runs `DeliveryDispatchWorkflow` / `DELIVERY_DISPATCH_WORKFLOW` calling existing activities; Edge bridge remains; no Fleetbase | Package SM Done (A–G) |
| **H3** | Promptfoo real-provider CI | CI job with real model provider + secrets; offline gate remains default locally; human-promote unchanged | Epic E Done |
| **H7** | B-PS-1 PowerSync live SDK | Mobile SDK wired to existing rules; offline queue intents only (no journal upload) | — |
| **H9** | Chatwoot / Metabase | Tier-2 satellites — **defer** unless explicit ticket | — |
| **H-ZIMRA** | ZIMRA / FDMS | **Excluded** until counsel ADR — never implement | — |

---

## 2. ERP platform app matrix

Legend: **N** = need apply/adapt · **—** = N/A · **OK** = already meets DoD · **DEF** = defer · **COND** = code ready, runtime evidence blocked

| Item | web | android-management | android-delivery | android-customer | ios | packages/* | bridges/ | supabase/ |
| --- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| H8 fund-release insert-once | — | — | — | — | — | — | — | **COND** |
| H-PARITY-WH2 POS | OK | **OK** | — | — | — | — | — | — |
| H2 preferred-PO | OK | **OK** | — | — | — | OK vocab | — | OK RPCs |
| H4 B-MONEY-1 | **N** | **N** | **N** | **N** | **N** | **N** shared/payments | — | **N** |
| H5 B-MAP-1 | OK track | — | OK primary | **OK** MapLibre address pick | **N** H5-iOS MapKit remain | — | **OK** maps-nav MapLibre | — |
| H6 B-OSRM-1 | — | — | prefer OK | prefer if maps | — | OK osrm | OK fetcher | — + **N** infra |
| H1 Temporal worker | — | — | — | — | — | **N** host | — | Edge OK |
| H3 Promptfoo CI | — | — | — | — | — | — | — | — + **OK** `.github`/promptfoo (offline default) |
| H7 B-PS-1 | — | **N** | maybe | maybe | maybe | — | — | powersync rules OK |
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
| H8 | **conditional** | Migration `20260812080000_*` + smoke; security PASS. **Blocked:** Docker Desktop daemon down — cannot `db:reset` / run smoke. |
| H-PARITY-WH2 | **Done** | Gradle `PosSaleableWarehouseTest` PASS; `PosViewModel` → `listSaleableWarehouses`; living docs updated. |
| H2 | **Done** | Native `PreferredPoScreen` + `listPreferredSuppliers` / `create_purchase_order` / `submit_purchase_order`; hub → Preferred supplier PO; Fake stubs; not RFQ-gated; Bridge QR. **Verifier 2026-08-12:** `:core:rpc:testDebugUnitTest --tests …PreferredPoHelpersTest` + `:feature:procurement:compileDebugKotlin` → BUILD SUCCESSFUL. |
| H4 … H-ZIMRA | open | — |

---

*Owned by Manager orchestration. Product code via lane agents only.*
