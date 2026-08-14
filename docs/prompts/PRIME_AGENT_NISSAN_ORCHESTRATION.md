# Prime Agent — Nissan GTR Auto orchestration (immutable mission brief)

**Runtime:** [PrimeIntellect-ai/prime-agent](https://github.com/PrimeIntellect-ai/prime-agent) as **orchestrator** (RLM + Continual Harness + `rlm(...)` subagents + persistent `/goal` + bounded `/autonomous` with quality gates).  
**Workspace CWD:** this repository root (`nissan-gtr-auto-erp` / local `nissan gtr`).  
**Not in scope as product SoR:** the Dial-a-Spare (DIAL) marketplace repo — DIAL is **pattern authority only** where this brief adopts it.

You execute end-to-end. Do **not** emit checklists, migrate commands, or “next steps for the human.” Apply migrations, regenerate types, run tests, fix failures, update living docs, and advance epics yourself. Ask the human **only** for irreversible product/counsel decisions that reopen a hard exclusion (e.g. ZIMRA). Everything else is your job.

---

## A. Mandatory reading order (load the meat — do not rely on this brief alone)

Read **in full** before mutating code. This brief is an **index + enforcement layer**; the named docs are source of truth for process, DoD, and architecture.

| Order | Path | Meat |
| ---: | --- | --- |
| 1 | `AGENTS.md` | Hard exclusions, DIAL engineering adoption, agent lanes, PR checklist |
| 2 | `docs/decisions/2026-08-12-principal-vs-dial-agency.md` | Principal ≠ agency ADR (what to adopt vs refuse from DIAL) |
| 3 | `docs/DIAL_SPARE_ADOPTION_PLAN.md` | Full adoption: stack inventory, domain targets §3, replace/adapt/integrate/defer §6, tracer E1–E6 §7, epic DoD §8, risks §9, citation index §11 |
| 4 | `docs/PROCUREMENT_WAREHOUSE_POS_SECURITY_PLAN.md` | Founder process, OSS picks/rejects, happy path, module DoD E-Proc/E-WH/E-POS/E-Del/E-Sec, implementation status, optional follow-ups |
| 5 | `docs/HARDENING.md` | Secrets, RLS, CI vs local smoke, ledger laws, exclusions, **§7 DIAL-aligned AppSec baseline** |
| 6 | `docs/plans/2026-08-12-pos-dial-ux-redesign.md` | POS Dial UX donors, done vs next, QA before Done |
| 7 | `docs/plans/2026-07-23-master-erp-development.md` | Master ERP roadmap / standing laws (process SoR) |
| 8 | `docs/AGENT_TEAM.md` + `rufler.yaml` | Specialist sequencing + path-routed lanes |
| 9 | `CHANGELOG.md`, `ENHANCEMENTS.md`, `README.md`, `BUGS.md` | Living status; known gaps (B-MONEY-1, B-MAP-1, B-OSRM-1, …) |
| 10 | `docs/decisions/README.md`, `docs/plans/README.md` | Decision/plan indexes — open any ADR that touches money, POS, delivery, offline |
| 11 | Migrations below + package `src/` + Edge functions listed in §E | Concrete schema/API SoR |

If DIAL pattern text is needed for money/maps/dispatch/security habits only, prefer Nissan’s adoption plan citations over inventing locks. **Never** import DIAL D-49/D-58 agency product locks.

Conflict order: (1) `AGENTS.md` hard exclusions + ADRs → (2) procurement/WH/POS plan + adoption plan → (3) master ERP plan → (4) this brief’s enforcement rules → (5) DIAL engineering habits explicitly adopted.

---

## B. Hard exclusions & locked solutions (verbatim intent)

### B.1 Standing exclusions (`AGENTS.md` / `README.md` / `HARDENING.md` §6)

- **NO ZIMRA** — no FDMS, fiscalisation, mTLS fiscal devices, tax-authority payloads, fiscal QR in checkout/invoicing/receipts.
- **NO payroll tax** — no PAYE, NSSA, statutory remittance forms; gross + manual deductions only.
- **NO RFQ-win as supplier SoR** — preferred supplier roster authorizes replenishment POs; RFQ = optional spot-buy only.
- **AI never writes payable amounts / never auto-creates POs** — forecasts suggest only.
- **Bridge-First** — no HTML5/browser/WebView QR or hardware APIs; use `bridges/`.
- **Ledger** — append-only journal entries; corrections via reversing entries; offline clients queue RPC intents — never upload `journal_entries` mutations.

### B.2 Principal vs agency (ADR `2026-08-12-principal-vs-dial-agency`)

**Adopt (engineering):** integer money (`amountMinor`), MapLibre + OSRM(/VROOM) delivery SoR, Temporal dispatch contracts, Resend/Brevo split, AI-never-writes-money, D-57 FX display habits, D-47/D-48 AppSec habits.

**Refuse (product):** D-49 informal→B2B hide, “Sold by {Supplier}”, SUPPLIER_COOP, Mercur multi-vendor as primary UX, `DIAL_OWNED` / marketplace-wide principal flip, agency FDMS receipt model (D-40a/D-59).

**Pattern-copy** into `@gtr/*` — no fourth money/delivery stack; do not submodule unfinished DIAL packages as runtime SoR.

### B.3 Locked technical solutions (do not reopen)

| Domain | Locked solution |
| --- | --- |
| Commercial | Principal distributor; owned + consignment; Postgres stock/WMS/ledger SoR |
| Procurement | Preferred-supplier roster + quoted figures; InvenTree/ERPNext = **vocabulary/UX patterns only** (MIT/GPL reference — never runtime DB) |
| Warehouses | `WH1` receiving (alias MAIN); `WH2` storefloor; WH1→WH2 transfers approval-tracked |
| Money | `amountMinor: bigint` + `currency` (`USD`\|`ZIG`); dual-write → cutover; never float SoR; never Medusa/Formance |
| FX (D-57 habit) | Browse/cart **USD**; ZiG only at checkout from ops daily rate + rate id |
| Payments | PspAdapter registry in `@gtr/payments`; webhook-as-truth + idempotency; ContiPay/Paynow/EcoCash/COD |
| Delivery jobs | Keep `delivery_jobs` tables; `@gtr/delivery` SM + FIFO/offer; Edge/SQL bridge; Temporal workflow **name** `DeliveryDispatchWorkflow` / `DELIVERY_DISPATCH_WORKFLOW` |
| Maps | MapLibre render SoR; OSRM distance SoR when configured; Google tiles = deprecated fallback only |
| Search | Postgres FTS SoR; Meili dual-read via `searchCatalog` (`preferMeili` default) |
| Email | Resend transactional; Brevo promo/CRM |
| WA | Official Cloud API only — Semgrep bans Baileys / whatsapp-web.js |
| POS UX | CoolMall/Nimara/Medusa DTC **patterns** + `@gtr/ui` / `packages/android-ui` tokens; native Android tablet (no Expo) |
| Security | JWT/`auth.uid()` only for identity; `has_staff_role` / module_access; worker `assertWorkerSecret`; Semgrep+Checkov CI |

**Reject as SoR/runtime:** ERPNext/InvenTree DB, Fleetbase, Baileys, Google/Mapbox as distance SoR, Medusa/Formance money SoR, RFQ-win mandatory PO path.

---

## C. Thin verticals — sequencing only; full feature required to proceed

### C.1 Law (D-52 adapted)

1. Plan diligence first — near-complete ACs/DoD before Build.
2. Thin vertical = **build order** against that complete DoD — **not** a license to ship stubs.
3. Sequence: **Plan → Build (thin vertical) → Expand in-ticket → Done**.
4. Hard ban: “MVP/tracer/scaffold = done.”

### C.2 Machine-enforceable advancement gate

Maintain an in-session structure (IPython/REPL or file under `docs/planning/` if useful):

```text
may_start_epic(N+1) :=
  epic[N].dod_items.all_checked
  AND epic[N].channel_matrix.no_blanks_for_required_channels
  AND epic[N].evidence_complete
  AND verifier_subagent_pass(epic[N])
  AND living_docs_updated
  AND no_hard_exclusion_violations
```

If false: **expand in-ticket**. Do not open the next epic’s Build. Do not mark `/goal` complete.

`/autonomous` quality gates (package tests, typecheck) prove **only** what they run. They never substitute for DoD 100%.

### C.3 Evidence required before Done (per epic)

- Automated tests green for touched `@gtr/*` (commands in §G — **you** run them).
- UI epics: Playwright or recon notes / screenshots stored or linked in the epic evidence block.
- Money/webhook: idempotent replay test or documented no-op on duplicate.
- AI: Promptfoo eval (or linked config path) + human-promote path cited — no self-certify.
- Migrations: present, RLS considered; applied in the environment you control; types regenerated when RPCs change.
- Living docs in the **same** change set: `CHANGELOG.md`, `ENHANCEMENTS.md`, `README.md` as needed; plan DoD boxes only when evidence exists.

### C.4 Anti-patterns (immediate rewind)

Stub-as-MVP; closing after first green path while DoD open; Phase-2 dump of locked scope; horizontal-only tickets without integration seam; reopening exclusions; AI payable writes; auto-PO; claiming Done because autonomous gates passed.

---

## D. Founder happy path (process SoR)

From `docs/PROCUREMENT_WAREHOUSE_POS_SECURITY_PLAN.md` §2 — implement and verify this end-to-end:

```text
AI forecast / min-stock → suggestion only
  → PO from preferred supplier roster + quoted unit costs
  → submit → finance/admin approve
  → auto procurement_fund_releases under created_by + domain events
  → GRN: OEM+qty (resolve_stock_item_by_oem); invoice attach; QR→OEM
  → stock WH1 → transfer approve → WH2
  → master stock total/WH1/WH2
  → delivery offer/FIFO/OSRM/MapLibre
```

---

## E. Technical index (code SoR map)

### E.1 Migrations (must remain coherent)

| File | Creates / changes |
| --- | --- |
| `supabase/migrations/20260812010000_relationship_procurement_dual_wh.sql` | `warehouses.role_code`; preferred supplier columns; `supplier_preferred_skus`; `procurement_fund_releases`; `purchase_orders.funds_released_at` / `progress_step`; WH1/WH2 seed; `v_master_stock`; RPCs `upsert_preferred_supplier`, `deactivate_preferred_supplier`, `list_master_stock`, `resolve_stock_item_by_oem`, `approve_purchase_order` (+ fund release), `_po_quoted_total` |
| `supabase/migrations/20260812020000_grn_invoice_dual_write.sql` | `goods_receipts.supplier_invoice_*`; `purchase_order_lines.unit_price_minor`; `procurement_fund_releases.amount_minor`; bucket `procurement-invoices` + RLS; `attach_goods_receipt_invoice`; trigger `purchase_orders_progress_submit` → `_po_set_progress_on_submit` |
| `supabase/migrations/20260812030000_amount_minor_dual_write.sql` | `_major_to_minor`, `_po_line_dual_write_minor`, trigger on PO lines; `approve_purchase_order` dual-writes `amount_minor`; backfills |

### E.2 RPC / function names (wire UI and Edge to these)

| Concern | Names |
| --- | --- |
| PO lifecycle | `create_purchase_order`, `submit_purchase_order`, `cancel_purchase_order`, `reject_purchase_order`, `approve_purchase_order` |
| Preferred suppliers | `upsert_preferred_supplier`, `deactivate_preferred_supplier` |
| Progress helpers | `_po_set_progress_on_submit`, `_po_quoted_total` |
| Money dual-write | `_major_to_minor`, `_po_line_dual_write_minor` |
| Master stock | `list_master_stock`, view `v_master_stock`, `resolve_stock_item_by_oem` |
| GRN | `create_goods_receipt`, `submit_goods_receipt`, `attach_goods_receipt_invoice` |
| Transfers (existing) | `create_stock_transfer`, `approve_stock_transfer` |
| Domain events | `emit_domain_event` (`po_approved`, `procurement_funds_released`) |
| Dispatch | `suggest_delivery_assignees`, `assign_delivery_job`, `_try_auto_assign_delivery_job` |
| RFQ (optional only) | `create_rfq`, `award_quotation_to_po` — never gate preferred POs |
| Catalog search | RPC `search_catalog`; Edge `catalog-search-meili` |

### E.3 Packages

| Package | Path | Key exports / modules |
| --- | --- | --- |
| `@gtr/procurement` | `packages/procurement/` | `PROCUREMENT_TRACKER_STEPS`, `PROCUREMENT_STEP_LABELS`, `WAREHOUSE_ROLE`, `resolveProcurementProgress`, `trackerIndex`, `completedTrackerCount`, types `PreferredSupplierInput`, `GrnFastLine`, `MasterStockRow`, `ProcurementProgressStep` — tests `src/types.test.ts` |
| `@gtr/payments` | `packages/payments/` | `PspAdapter`, `PspRegistry`, `defaultPspRegistry`, `createStubPspAdapter`, `buildCheckoutDisplay`, `CheckoutDisplay` — tests `src/psp.test.ts` |
| `@gtr/delivery` | `packages/delivery/` | `DELIVERY_DISPATCH_WORKFLOW`, `fetchOsrmRoute`, `parseOsrmRouteJson`, `applyOfferDecision`, `selectNextCourierOffer`, `runDeliveryDispatchCycle`, `createSqlDispatchActivities`, `runSqlDeliveryDispatchCycle`, `trySqlAutoAssign`, `candidatesFromSuggestRows`, `preferRoutingProvider` — modules `osrm.ts`, `dispatch.ts`, `temporal.ts`, `assign-bridge.ts` — tests `*.test.ts` |
| `@gtr/shared` money | `packages/shared/src/money.ts` | `MoneyMinor`, `toAmountMinor`, `fromAmountMinor`, `moneyToMinor`, `minorToMoney`, `dualWriteMoney`, `moneyMinorToJson` / `FromJson`, `majorToMinorNumber` — tests `money.test.ts` |
| `@gtr/supabase-client` | `packages/supabase-client/src/catalog-search.ts` | `searchCatalog`, `searchCatalogMeili`, `searchCatalogFts`, `CATALOG_SEARCH_MEILI_FN`, `SEARCH_CATALOG_RPC` |

### E.4 Web surfaces

| Route | Page | Lib / components |
| --- | --- | --- |
| `/procurement` | `apps/web/app/(b2b)/procurement/page.tsx` | `procurement-progress-tracker.tsx`, `procurement-nav.tsx` |
| `/procurement/suppliers` | `.../suppliers/page.tsx` | `preferred-suppliers-panel.tsx` |
| `/procurement/orders/new` | `.../orders/new/page.tsx` | `lib/preferred-po.ts` |
| `/procurement/grn` | `.../grn/page.tsx` | `goods-receipt-panel.tsx` |
| `/procurement/approvals` | `.../approvals/page.tsx` | `staff-procurement-approvals-panel.tsx`, `lib/procurement-approvals.ts` |
| `/staff/warehouse/master-stock` | `apps/web/app/(staff)/staff/warehouse/master-stock/page.tsx` | `master-stock-panel.tsx` |
| `/staff/warehouse/receive` | `.../receive/page.tsx` | linked from hub |
| `/staff/pos` | `.../pos/page.tsx` | `staff-pos-shell.tsx`, `staff-pos-panel.tsx`, `lib/staff-pos.ts` |
| `/cart` | `apps/web/app/(storefront)/cart/page.tsx` | `cart-checkout.tsx` → `buildCheckoutDisplay` |
| `/procurement/rfqs*` | RFQ optional | must remain secondary in copy |

### E.5 Android

| Item | Path |
| --- | --- |
| MapLibre map | `apps/android-delivery/feature/tracking/.../MapLibreJobMap.kt` |
| Job detail wiring | `apps/android-delivery/feature/jobs/.../JobsScreen.kt` (`JobDetailScreen`) |
| Delivery host | `apps/android-delivery/app/.../MainActivity.kt` |
| RpcNames | `apps/android-delivery/core/rpc/.../RpcNames.kt` |
| POS | `apps/android-management/feature/pos/.../PosScreen.kt` |
| Theme | `packages/android-ui/.../GtrTheme.kt` (+ Colors/Typography/Shapes/Chrome) |
| Procurement module | `apps/android-management/feature/procurement/...` — preferred PO currently **web-first** per plan |

### E.6 Edge / worker auth

| Function | Path | Auth |
| --- | --- | --- |
| `delivery-dispatch-cycle` | `supabase/functions/delivery-dispatch-cycle/index.ts` | `assertWorkerSecret` → `suggest_delivery_assignees` / `assign_delivery_job` or `sql_auto` → `_try_auto_assign_delivery_job` |
| `catalog-search-meili` | `supabase/functions/catalog-search-meili/index.ts` | JWT; Meili server-side |
| `process-ai-reports`, `process-crm-promos` | respective dirs | `assertWorkerSecret`; Promptfoo targets |
| `paynow-*`, `contipay-*`, `ecocash-*` | respective dirs | HMAC/signature before mutate |
| Shared | `supabase/functions/_shared/worker_auth.ts` | header `x-worker-secret` vs `WORKER_SHARED_SECRET`; fail-closed unless `WORKER_ALLOW_UNVERIFIED_LOCAL=1` |

### E.7 AppSec / eval CI

- `semgrep.yml` + `semgrep/rules/{no-body-identity,no-client-secrets,no-raw-sql-concat,no-unofficial-whatsapp,webhook-signature}.yaml`
- `.github/workflows/semgrep.yml` (`semgrep-gtr` hard-fail), `checkov.yml` (HIGH+ hard-fail), `ci.yml`
- `promptfoo/promptfoo.config.yaml` + `promptfoo/README.md`

---

## F. Epics to drive to DoD 100% (order fixed)

Candidate code may already exist — **verify, harden, evidence, then Done**. DoD text is owned by the plans in §A; summarize here for gatekeeping.

### Epic A — E-Proc + E-WH (procurement / dual WH / GRN)

**DoD source:** `PROCUREMENT_WAREHOUSE_POS_SECURITY_PLAN.md` §3 E-Proc + E-WH.  
**Includes:** preferred CRUD; manual PO; tracker steps; approve→fund release; RFQ secondary; WH1/WH2; transfers; master stock; GRN OEM+qty + invoice attach; `amount_minor` dual-write on PO paths.  
**Channels required:** web staff/b2b; packages; migrations. Android preferred-PO is **web-first** unless a new epic DoD adds native parity.  
**Done only when:** plan checkboxes true **with evidence**, not theater.

### Epic B — E-Del (dispatch + maps)

**DoD source:** same plan §3 E-Del + adoption plan Epic Delivery §8.  
**Includes:** offer/FIFO; OSRM SoR when configured; MapLibre on job detail (not orphaned); assign-bridge + Edge cycle; no Fleetbase.  
**Full Temporal worker binary:** optional follow-up epic (see §H) — only required here if you expand DoD to demand it; default DoD = package + Edge bridge + MapLibre wiring.

### Epic C — E3 payments + D-57 checkout

**DoD source:** adoption plan E3 + Epic Finance §8.  
**Includes:** `@gtr/payments` registry; `buildCheckoutDisplay` on cart; webhook habits; AI cannot write payable fields.

### Epic D — E6 Meili dual-read

**DoD source:** adoption plan E6.  
**Includes:** `searchCatalog` preferMeili + FTS fallback; call-site choke point; Meili never invents qty.

### Epic E — E4 AI Promptfoo

**DoD source:** adoption plan E4 + Epic AI/CRM §8.  
**Includes:** promptfoo config on CRM/report edges; no invented prices; human promote. Real provider CI = §H unless DoD upgraded.

### Epic F — E-Sec AppSec CI

**DoD source:** plan §3 E-Sec + `HARDENING.md` §7.  
**Includes:** Semgrep hard-fail, Checkov HIGH+, no body identity, no client secrets, worker fail-closed, RLS smokes.

### Epic G — E-POS Dial UX

**DoD source:** plan §3 E-POS + `docs/plans/2026-08-12-pos-dial-ux-redesign.md` **including its QA checkboxes**.  
**Includes:** web tokens/responsive; Android `GtrTheme` on PosScreen; tablet dual-pane/touch targets; WH2 as POS pick source (WH1 receiving only).  
**Done only when redesign plan QA boxes are evidenced.**

---

## G. Orchestrator loop (Prime Agent — you perform all of this)

1. Load §A docs + inventory git/packages/migrations.
2. Set persistent goal to: complete Epics A→G to DoD 100% with evidence under locked solutions; then §H if budget remains.
3. Spawn lane subagents via `rlm(...)` per `rufler.yaml` / `AGENTS.md` (backend, web, android-delivery, android-management, packages, security, verifier). Verifier never implements features.
4. For each epic A→G: freeze DoD from plan → thin vertical → expand → evidence → verifier → living docs → **only then** next epic.
5. Run (yourself):

```bash
pnpm --filter @gtr/shared test
pnpm --filter @gtr/payments test
pnpm --filter @gtr/delivery test
pnpm --filter @gtr/procurement test
# plus epic-appropriate web/android checks
npx promptfoo eval -c promptfoo/promptfoo.config.yaml
```

6. Apply pending `20260812*` migrations in the dev DB you control; regenerate `packages/supabase-client/src/database.types.ts` when RPCs drift; fix compile breaks.
7. Use heartbeats to catch false Done (DoD open / missing evidence). `/refine` only for evidence-backed lock lessons — never to weaken exclusions.
8. On budget exhaustion: leave goal incomplete with precise open DoD lines — never declare success.

---

## H. Deferred / next epics (separate DoD; do not smuggle into A–G Done)

Owned by adoption plan §6 Defer, procurement plan §5 optional, `ENHANCEMENTS.md`, `BUGS.md`:

- Full Temporal worker binary hosting `DeliveryDispatchWorkflow`
- Android native preferred-supplier PO screen (parity epic)
- Promptfoo CI with real model provider
- Broader ledger `amount_minor` cutover (B-MONEY-1) — dual-read → backfill → cutover; never big-bang
- Full MapLibre Compose skin; remove Google map SoR leftovers (B-MAP-1)
- OSRM compose/data online (B-OSRM-1)
- PowerSync live SDK (B-PS-1)
- Chatwoot / Metabase / ZIMRA (ZIMRA stays excluded until counsel ADR)

---

## I. Session success criteria

`/goal` completes only when Epics **A–G** each satisfy §C.2, living docs match reality, no exclusion violations, and a final orchestrator report lists: verified artifacts, evidence pointers, any §H items still open (with explicit “not Done”). No human action list.

---

*End of brief. Begin by reading §A documents in order, then execute §G.*
