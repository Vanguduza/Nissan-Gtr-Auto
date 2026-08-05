# Unified Master Plan Enhancement — Master Design Plan vs Repo Reality

- Status: draft (analysis + roadmap; no code touched) — **updated 2026-08-04 (2nd pass)**: added §4.5–§4.7 (Medusa/Bagisto/Odoo/OmniCart adoption verdict, OmniCart license verification, Mobile Redesign Directive) and reprioritized §5 Now per user direction
- Lane(s): `/manager` sequences; per-gap owning lane named in §5 and §4.5; current top priority is `@management_app_agent` (POS relayout) + `@android_agent` (customer merchandising redesign) per §4.7
- Skills needed: none to read this doc; named per-gap in §5 (`/accounting-ledger`, `/qr-inventory-workflow`, `/ui-ux-pro-max` only if a gap needs them)
- Source audited: [`catalog/Nissan_GTR_Auto_Unified_ERP_Master_Design_Plan.md`](../../catalog/Nissan_GTR_Auto_Unified_ERP_Master_Design_Plan.md) ("the master plan")
- Cross-checked against: `rufler.yaml`, `AGENTS.md`, `README.md`, `.github/workflows/ci.yml`, `.cursor/hooks.json`, `docs/decisions/*`, `docs/plans/2026-08-02-cursor-oss-strategy-findings.md`, `docs/plans/2026-08-02-open-source-erp-toolkit-audit.md`, `docs/plans/2026-08-03-mobile-ui-oss-discovery.md`, `docs/plans/2026-07-27-mobile-storefront-parity.md`, `docs/plans/2026-08-03-tablet-kiosk-pos-full-action-plan.md`, `docs/decisions/2026-08-03-offline-sqlcipher-pos-cache.md`, `docs/plans/2026-07-24-phase15-parity-audit.md`, `docs/plans/2026-07-23-master-erp-development.md`, and live repo structure (`supabase/migrations/*.sql` — 103+ files, `apps/web/app/**/page.tsx` route groups, `apps/android-management/feature/*`, `bridges/android|ios/*`, `data-pipeline/data_pipeline/*.py`)
- **2026-08-04 (2nd pass) also cross-checked against**: `docs/audit/2026-08-04-master-audit/{architecture-map,domain-database-analysis,structural-critique}.md` (`security-findings.md` in that same folder is present but empty — flagged in §4.5), `docs/decisions/2026-08-04-gsf-ux-behaviour-specification.md`, live read of `apps/android-management/feature/pos/src/main/java/co/zw/nissangtr/management/pos/PosScreen.kt`, `packages/ui/brand-tokens.json`, and a live GitHub lookup of `3wiida/OmniCart`.

---

## 1. What the master design plan actually proposes (honest summary)

The doc is a **strategy/governance brief**, not a tool catalog. Its core asks:

1. **Preserve-then-audit, never rewrite blind** (§1–2): keep existing custom logic; audit before refactoring; never run Medusa/Bagisto/Odoo as parallel production systems or copy their code — reference patterns only.
2. **One platform, multiple experiences** (§3–4): a single "Unified Platform API" (authn/authz/validation/audit/rate-limit/versioning) fronting Commerce / Automotive / ERP-Operations domains over one authoritative datastore; **modular monolith first**, not microservices; an explicit `backend/core|commerce|automotive|operations|pos|finance|management/` folder taxonomy; an explicit one-entity-one-owner table (User→core identity, Product→commerce, OEM part/fitment→automotive, Price→commerce, Physical stock→operations, POS sale→POS, Financial journal→finance, Audit event→core audit).
3. **Reference-pattern extraction** (§5), explicitly *not* to be run in parallel or copied: Medusa (workflows/cart/checkout/order-lifecycle/pricing), Bagisto (storefront IA + admin UX), Odoo (inventory-as-ledger, warehouse/procurement/accounting concepts), OmniCart (Compose Android commerce architecture), GSF APK (UX/behaviour reference only, via APKLab + JADX-GUI, kept in an isolated `reference-analysis/` workspace — never merged into production).
4. **GSF-inspired customer UX** (§6): vehicle ID by reg/VIN/manual selection/OEM search with a persistent "Current vehicle" banner and fitment-status badges; Home/Shop/Deals/Orders/Account IA.
5. **Client strategy** (§7): customer Android, management Android, tablet kiosk/POS, and a shared `android/core|domain|feature` module layering. **Note: the master plan's platform vision (§1, §3) names only 5 surfaces — customer web, customer Android, management web, management Android, tablet kiosk/POS — no iOS, no dedicated delivery app.**
6. **Security & quality strategy** (§8): backend checklist (hashing, token rotation, object-level authz, rate limiting, audit logs, secrets mgmt, TLS, dependency scanning), mobile OWASP MASVS checklist, and a Definition-of-Done gate tying every feature to documented auth/authz/audit/tests.
7. **Data & workflow design** (§9): inventory as an **append-only StockMovement ledger**, not a mutable quantity field; an explicit order-lifecycle state machine (`DRAFT→PENDING_PAYMENT→PAID→CONFIRMED→PROCESSING→PARTIALLY_FULFILLED→FULFILLED→DELIVERED` + cancel/refund branches); automotive fitment as relational entities (VehicleMake/Model/Generation/Engine/Transmission/OEMPartNumber/CrossReference/FitmentRule/Diagram/DiagramHotspot) — never a text field.
8. **Audit-first governance** (§10–11): a mandatory Phase-1, code-untouched audit producing **20 files** under `docs/audit/` (repo map → tech stack → architecture → domain model → DB schema → API inventory → authn/authz → security findings → feature inventory → UI/UX audit → Android audit → code quality → test coverage → dependency review → reference-pattern mapping → target architecture → migration roadmap → risk register → decision log), driven by an embedded "Cursor AI Master Audit Prompt."
9. **Phased roadmap** (§13): Phase 0 (preserve/baseline) → 1 (audit) → 2 (target-arch approval) → 3 (security stabilization) → 4 (modular backend refactor) → 5 (web redesign) → 6 (Android foundation) → 7 (customer Android) → 8 (management Android) → 9 (tablet kiosk/POS) → 10 (EPC/fitment/diagrams) → 11 (production hardening: load/security testing, monitoring, alerting, backup-restore, incident response, training).
10. **Governance** (§14–16): Definition of Done, one running `docs/audit/20-decision-log.md`, change control requiring evidence → proposal → impact analysis → migration plan → rollback plan → approval before any broad architectural change.

---

## 2. Capability/tool mapping — master plan vs repo reality

Legend: **Done** / **Partial** / **Not started** / **Superseded** (repo made a different, recorded choice).

### 2.1 Architecture & governance

| Master plan proposal | Status | Evidence |
|---|---|---|
| Modular monolith, one DB, one SoR | **Done (different shape)** | Repo has no separate app-server "backend/" tree — Supabase IS the backend: 103+ versioned migrations + SECURITY DEFINER RPCs *are* the modular API layer. Domain separation is enforced by migration naming + `rufler.yaml` lanes, not a `core/commerce/automotive/operations/pos/finance/management/` folder tree. **Superseded-by-different-choice**: Supabase-native schema+RPC instead of a bespoke Node/Python service layer. |
| Explicit one-entity-one-owner table (§4.5) | **Done (different artifact)** | Same intent lives in `AGENTS.md` agent-lane table + `rufler.yaml` (`backend_agent` owns `supabase/**`, `finance_agent` owns ledger migrations, etc.) rather than a standalone ownership doc. |
| Audit-first, code-frozen Phase 1 with 20 `docs/audit/*.md` files | **Not started** | `docs/audit/` does not exist in the repo. **Superseded-by-different-choice**: the repo runs continuous, lightweight audits instead — `docs/plans/2026-07-24-phase15-parity-audit.md` (ERPNext-parity + exclusion grep + gap register, marked Done 2026-07-24), `docs/plans/2026-08-02-open-source-erp-toolkit-audit.md`, `docs/plans/2026-08-02-cursor-oss-strategy-findings.md`. One-decision-per-file ADRs under `docs/decisions/*.md` stand in for the master plan's single running `20-decision-log.md`. **Real gap**: none of these produce the specific consolidated artifacts the master plan wants — a full API/RPC inventory, a severity-classified security-findings register, and a risk register genuinely do not exist anywhere today (see §5 Now-3/4). |
| Change control: evidence → proposal → impact → migration → rollback → approval before broad changes | **Partial** | `.cursor/rules/session_discipline.mdc` enforces `/manager`/`/planner` → one lane → `/security-reviewer` → `/verifier`, which is a lighter version of the same discipline, already standing and working. See §4 (flagged, not a violation). |

### 2.2 Reference patterns (§5)

| Reference | Master plan's ask | Status | Evidence |
|---|---|---|---|
| **Medusa** (workflows, cart/checkout, order lifecycle, pricing/promo) | Study & adapt patterns only | **Not adopted as a pattern-study exercise; outcome achieved independently** | Cart/checkout/POS/quotations already shipped custom (batch1 commerce migrations, `20260803251000_pos_quotations.sql`). `docs/plans/2026-08-02-open-source-erp-toolkit-audit.md` marks Medusa **"Conditional — only if a true headless B2B storefront is a product gap"**; not currently needed since `apps/web/app/(b2b)/`, `(supplier)/` already exist. |
| **Bagisto** (storefront IA + admin UX) | Study & adapt patterns only | **Superseded-by-different-choice** | PHP/Laravel — explicitly marked "Poor fit / stack mismatch" in the OSS audit. Storefront IA instead modeled on AutoDoc (`docs/decisions/2026-07-23-storefront-autodoc-logo.md`, `2026-07-23-autodoc-shop-features.md`). |
| **Odoo** (inventory-as-ledger, warehouse/procurement/accounting concepts) | Study & adapt patterns only | **Superseded-by-different-choice** | Append-only ledger is a *global standing law* (`.cursorrules` Ledger Immutability), not Odoo-derived; warehouse/bins/transfers/consignment/cycle-count and RFQ/blanket-PO procurement already shipped (Phase 4/8/8b). OSS audit marks Odoo **"Conflict — do not rebase ERP onto it"** as a platform; patterns were independently re-derived via the `/erpnext-feature-parity` skill instead. |
| **OmniCart** (Compose Android commerce architecture) | Study & adapt patterns only | **Done — superseded pattern source for visuals; adopted as architecture-layering reference (2026-08-04, §4.5/§4.7)** | `docs/plans/2026-08-03-mobile-ui-oss-discovery.md` chose **Jetsnack** (Apache-2.0, official `android/compose-samples`) for customer Shop/Cart/Account IA and **Reply** (Apache-2.0) for management adaptive nav — both licensed and shipped (`docs/plans/2026-07-27-mobile-storefront-parity.md`: "Customer shell redesign Done 2026-08-03"). **Verified 2026-08-04** (see §4.6): the real repo is [`3wiida/OmniCart`](https://github.com/3wiida/OmniCart), which carries **no LICENSE file at all** — not merely "ambiguous," but all-rights-reserved by default, a harder stop than AGPL/GPL. Jetsnack remains the only code-reuse-eligible visual reference; OmniCart's Clean-Architecture (data/domain/presentation) *layering pattern* is separately adopted as the structural reference for the current Android redesign per the user's 2026-08-04 direction — pattern only, never its code. |
| **GSF APK** UX/behaviour reference (APKLab + JADX-GUI, isolated `reference-analysis/` workspace, `docs/reference-analysis/gsf/*.md`) | Reverse-engineer for UX notes only, never copy code | **Not started** | No `reference-analysis/gsf-apk/`, no APKLab/JADX-GUI pass, no `docs/reference-analysis/gsf/*.md` anywhere in the repo. Vehicle-ID-by-VIN/manual-selection UX (§6.1) exists independently via My Garage (`apps/web/app/(my-garage)/garage`, `(storefront)/vehicle`) — not GSF-derived. See §4 for a recommendation to formally close this rather than leave it dangling. |

### 2.3 Client application strategy (§7)

| Surface | Master plan ask | Status | Evidence |
|---|---|---|---|
| Customer web | AutoDoc-ish storefront | **Done — exceeds spec** | `apps/web/app/(storefront)/*`, `(account)/*`, `(my-garage)/*`, plus `(b2b)/*` and `(supplier)/*` portals the master plan never asked for. |
| Customer Android | GSF-inspired UX, OmniCart-inspired structure, vehicle-aware catalogue, cart/checkout/orders/account | **Done** | Jetsnack-based Shop/Cart/Account shell, ZiG pricing, garage/wishlist/compare/reviews (`docs/plans/2026-07-27-mobile-storefront-parity.md` feature matrix — all ✅ except diagram canvas, flagged Later). |
| Customer iOS | *Not in master plan's 5-surface vision at all* | **Done — beyond original scope** | `apps/ios/GTRCustomer` has parity with Android (deep links, `ReviewCamera` bridge, `GTRTheme`). Repo added a 6th surface the source doc never named. |
| Management web | Separate management web app | **Superseded-by-different-choice** | One Next.js app with route-group RBAC (`(staff)/staff/*`) instead of two web apps — `docs/decisions/2026-07-25-web-management-parity-rbac.md`. Achieves the same *outcome* (management workflows on web) via a different structural choice. |
| Management Android | Dashboards, inventory, orders, customers, suppliers, reports, RBAC | **Done — exceeds spec** | `apps/android-management/feature/{warehouse,procurement,hr,credit,dispatch,fleet,chat,pos,kiosk}` — also adds HR onboarding wizard and tablet kiosk module the master plan didn't specify in this detail. |
| Tablet kiosk/POS | Large touch targets, fast search, barcode, customer/vehicle lookup, server-authorized discounts, multi-payment, receipts, returns/refunds, stock lookup, role-gated management access | **Done — substantially exceeds spec** | `docs/plans/2026-08-03-tablet-kiosk-pos-full-action-plan.md` status **complete**: 69-item catalog, Device Owner/Lock Task/Magisk Path B boot hardening, offline SQLCipher cache + WorkManager sync, quotations create/send/convert, ESC/POS bridge printing, Admin\|shop-manager-gated void/discount/refund routed through the finance refund ledger pipeline. |
| Dedicated delivery app | *Not in master plan's surface list at all* | **Done — beyond original scope** | `apps/android-delivery/` per `docs/decisions/2026-07-25-dedicated-delivery-app.md` (jobs/POD/GPS FGS/panic — no POS/warehouse/finance). |
| Shared Android `core/domain/feature` module layering (§7.4) | Explicit shared-module tree | **Partial** | Real module separation exists (`packages/android-ui` GtrTheme, `bridges/android/*` shared bridges), but not literally the `android/core/…/domain/…/feature/…` tree from §7.4 — each app (`android-customer`, `android-management`, `android-delivery`) has its own `feature/*` Gradle modules rather than one shared super-repo layer. Low-priority structural gap; current per-app modules already avoid duplication of business logic (that lives in `packages/shared`). |

### 2.4 Security & quality strategy (§8)

| Requirement | Status | Evidence |
|---|---|---|
| RLS / server-side authz on every protected action | **Done** | `.cursorrules` RLS Mandate is a standing global law; `/supabase-rls-auditor` subagent runs on every migration. |
| SECURITY DEFINER action-gating on sensitive ops (discount/void/refund/price-override) | **Done** | `20260803250000_pos_approver_void_discount_refund.sql`; kiosk plan #28/#45–48. |
| Audit logging on sensitive actions | **Partial** | POS void/discount/refund are audited; a unified "core audit" event table/module spanning *all* domains (HR onboarding, procurement approvals, warehouse adjustments) is not confirmed as consolidated — worth a targeted pass (§5 Next-3), not a full rebuild. |
| CI: lint/typecheck/tests, hard-exclusion grep, RLS smoke | **Done** | `.github/workflows/ci.yml` — `quality` (lint/typecheck/`pnpm test`), `exclusions` (ripgrep gate for ZIMRA/payroll-tax/HTML5-QR strings across apps/packages/bridges/data-pipeline/edge functions/migrations), `db-smoke` (migrate + `phase2_rls_smoke.sql` + `phase14_ci_smoke.sql`). |
| Dependency vulnerability scanning | **Not started** | No `.github/dependabot.yml`, no `npm audit`/`pip-audit`/Snyk step in `ci.yml`. Real, cheap gap. |
| OWASP MASVS mobile security review | **Not started as a formal pass** | No `docs/audit/12-android-audit.md`-equivalent artifact exists anywhere. |
| Monitoring/alerting, load testing, backup-restore drills, incident response plan (§13 Phase 11) | **Not started** | No observability stack, load-test report, or incident-response doc found under `docs/`. This is the master plan's Phase 11 and is the most concretely absent item in the whole audit. |

### 2.5 Data & workflow design (§9)

| Requirement | Status | Evidence |
|---|---|---|
| Inventory as append-only StockMovement ledger, not a mutable quantity | **Done** | `20260724040000_stock_recon_mutation_guards.sql` + warehouse/bins/transfers/cycle-count/consignment migrations; global Ledger Immutability law. |
| Explicit order-lifecycle state machine | **Partial** | Lifecycle states exist per-domain (customer orders, POS park/resume/void, quotation draft→issued→converted, delivery job states) but are **not consolidated into one documented canonical state-machine reference** the way §9.2 describes. Functionally fine; a documentation gap, not a behavior gap. |
| Automotive fitment as relational entities (Make/Model/Generation/Engine/Transmission/OEM/CrossReference/FitmentRule/Diagram/Hotspot) | **Done — exceeds spec** | This is the most mature area vs the master plan's aspiration: `data-pipeline/data_pipeline/{parse_fast,hierarchy,vin_decode,chassis_discovery,diagram_gen}.py` implement Nissan-FAST-EPC-precise PNC/OEM/vehicle-hierarchy mapping, more precise than the generic ACES/PIES model the master plan sketches. Diagram/hotspot migrations exist (`catalog_diagrams` seeds). |

---

## 3. Other tools named in the master plan — adopt-first evaluation

The master plan is deliberately **reference-pattern-only** — it names no infrastructure libraries, SaaS platforms, search engines, or payment rails, only the five reference *projects* covered in §2.2 plus two RE tools:

| Tool | Purpose in master plan | Adopt-first call |
|---|---|---|
| **APKLab** (VS Code/Cursor extension) | Decompile GSF APK for UX reference | **Skip.** No open item requires it; Jetsnack + AutoDoc already deliver an equivalent, legally clean automotive-commerce UX reference (see §2.2, §4). |
| **JADX-GUI** | Navigate/search decompiled GSF classes | **Skip**, same reasoning. |

The actual tool-adoption legwork the master plan gestures at ("extract patterns," "reference architecture") was already done independently and in more depth by `docs/plans/2026-08-02-cursor-oss-strategy-findings.md` and `docs/plans/2026-08-02-open-source-erp-toolkit-audit.md`, which evaluated a much wider real candidate list (Meilisearch, Traccar, OSRM, node-casbin, Gorse, Apache Superset, NHTSA vPIC, ZXing/ML Kit) under the same Integrate/Fork/Build rubric and produced `docker-compose.satellites.yml` + `infra/satellites/README.md` scaffolding. Those decisions are **already recorded and partially scaffolded** — this plan does not re-litigate them, only folds their still-open next-steps into §5's roadmap (Meilisearch dual-read wiring, Traccar/OSRM/Casbin/Gorse/Superset sequencing).

---

## 4. Conflicts / items to flag explicitly (not silently drop)

- **Governance weight mismatch.** The master plan's Phase 0–2 (§10, §13) requires a full code-freeze audit + explicit target-architecture approval *before any refactor*. The repo's live process (`.cursor/rules/session_discipline.mdc`: `/manager`/`/planner` → one lane → `/security-reviewer` → `/verifier`) is a lighter, continuous-ADR model that has already been running successfully across 50+ dated plans and 20+ decisions. **Recommendation: do not retrofit the heavyweight one-time audit gate** — it would burn tokens re-deriving already-decided things. Instead, close the *specific* missing artifacts (API/RPC inventory, security-findings register, risk register — §5 Now) as thin, targeted docs without reopening governance.
- **GSF APK reverse-engineering** (§5.5, §12) sits in tension with the adopt-first ethos and carries real IP/legal exposure (decompiling a third party's proprietary parts-catalog APK). It should be **explicitly rejected**, not left as a dangling unimplemented "Should" that a future agent might pick up cold. See §5 Now-5.
- **Odoo/Bagisto/ERPNext-as-platform** are not proposed by name as *platforms* in this master doc (only as pattern sources), but a related PDF-derived plan (`docs/plans/2026-08-02-open-source-erp-toolkit-audit.md`) already fully evaluated and rejected ERPNext/Odoo/Bagisto as SoR replacements (GPLv3 rebase risk, stack mismatch, payroll-tax exclusion conflict for Frappe HR). No new conflict — just citing it so nobody re-opens that door reading the master plan cold.
- **Hard exclusions check**: the master plan contains **zero** references to ZIMRA/FDMS/fiscalisation or payroll tax. Clean — nothing to reject here.
- **Text-to-SQL / "fully autonomous zero-touch AI"** is *not* in this master design doc — that ask lives in the separate `catalog/`-adjacent `Cursor_AI_Architecture_Prompt.md`, already resolved and constrained by `docs/decisions/2026-08-03-ai-autonomous-layer-constraints.md` (Text-to-SQL rejected, CRM outreach made opt-in). Mentioned here only so the two documents aren't conflated during roadmap execution.

---

## 4.5 Medusa / Bagisto / Odoo (+ OmniCart) Adoption Verdict — 2026-08-04

Direct response to the user's ask for an actual per-capability call (**Adopt / Adapt / Reject**), not a restatement of §2.2's "study the patterns" framing. Grounded entirely in `docs/audit/2026-08-04-master-audit/structural-critique.md` and `.../domain-database-analysis.md` — no new reference-project research performed here.

> **Note on audit ground truth:** `docs/audit/2026-08-04-master-audit/security-findings.md` (listed as ground truth for this update) currently **exists but is empty (0 bytes)** — a security-findings workstream referenced by `structural-critique.md` ("Finding 1", "Finding 5", "Finding 6") appears not to have been written to disk yet. This verdict does not depend on its contents, but flag it back to whoever owns that audit pass — the citations to it in `structural-critique.md` currently point at a file with nothing in it.

### Condensed verdict table

| # | Pattern | Source | Verdict | Owning lane | Effort |
|---|---|---|---|---|---|
| 1 | `res.partner`-style customer/supplier unification | Odoo | **Adapt** — link, don't merge | `@backend_agent` | S–M |
| 2 | Procurement (requisition→RFQ→PO→GRN→landed-cost) + `account.move`-style ledger | Odoo | **Adopt — already shipped, preserve** | `@backend_agent` / `@finance_agent` | XS (verify only) |
| 3 | Modular commerce services behind a workflow-orchestration layer | Medusa | **Reject** at backend; **Adapt** at Android RPC-client boundary | `@management_app_agent`, `@android_agent` | M |
| 4 | Separate cart/checkout/order modules | Medusa | **Reject** — no action | — | n/a |
| 5 | Laravel package-per-domain / storefront↔admin structural separation | Bagisto | **Adapt** — principle only, not the stack | `@web_agent` | M |
| 6 | Clean-Architecture Compose layering (data/domain/presentation, repo+use-case boundary) | OmniCart | **Adapt** the pattern; **Reject** any code reuse (no license — see below) | `@management_app_agent`, `@android_agent` | L |

### Reasoning per item

1. **Odoo `res.partner` unification → Adapt.** `domain-database-analysis.md` §2 confirms `customers`/`suppliers` are fully separate tables with no link, and explicitly frames this as "a trade-off, not an unambiguous defect" — a unified partner table needs careful RLS design so a sales rep can't see supplier banking data via a customer-scoped policy or vice versa. Full `res.partner`-style merge is **rejected** as unnecessary schema/RLS risk for a scenario that's plausible but not evidenced as a live problem. **Adapt** instead: a lightweight linking mechanism (`party_links(customer_id, supplier_id)` or nullable cross-FKs) so the relationship can be expressed without merging schemas or RLS surfaces. Needs an ADR before build (schema + RLS implications) — do not build ad hoc.

2. **Odoo procurement + accounting ledger → Adopt (already shipped).** `domain-database-analysis.md` §3 calls the ledger "a direct structural match" to `account.move`/`account.move.line`; §4 calls procurement "structurally faithful, arguably more complete" than most from-scratch ERPs. This confirms what's already built should be **preserved, not rebuilt**. One carried-over, cheap follow-up from §3: DB-level enforcement of ledger immutability (a `REVOKE UPDATE, DELETE` grant or trigger on `journal_entries`) is flagged **UNKNOWN, high-priority to verify** — `packages/shared/src/ledger/journal.ts`'s `assertBalanced` is a client-side check only and cannot enforce this at the database layer. Verify next time `@finance_agent` touches ledger migrations; not a blocker for this plan's current priority.

3. **Medusa modular services + workflows → Reject at backend, Adapt at the Android RPC boundary.** `domain-database-analysis.md` §5 calls PL/pgSQL single-transaction orchestration (e.g. `replay_offline_pos_sale`) "arguably simpler and safer" than a workflow engine at this scale — **reject** a Medusa-style workflows-SDK or splitting Postgres RPCs into service "modules." But Medusa's underlying idea — narrow, per-domain service interfaces instead of one god interface — is exactly `structural-critique.md` §2's fix for the **102-method `RpcClient` God interface** (management app) and its ~39-method customer-app twin: decompose into `PosRpc`, `HrRpc`, `WarehouseRpc`, `LogisticsRpc`, `ChatRpc`, etc., all still implemented by one `SupabaseRpcClient`. **Adapt** at this layer only. §2 is explicit this must land **before or alongside** the `PosViewModel` split, "or the ViewModel refactor... will not actually reduce coupling."

4. **Medusa separate cart/checkout/order modules → Reject.** `domain-database-analysis.md` §5 calls the current unified `pos_carts`/`pos_cart_lines` (shared by storefront checkout and the POS till, channel-discriminated) a **positive deviation** from Medusa's separate-module ideal — it avoids "duplicate implementations of stock/order state," which the project's own non-negotiable rules forbid. Splitting cart from order Medusa-style would reintroduce that exact risk. No action — recorded so nobody re-opens this cold.

5. **Bagisto package-per-domain / admin↔shop separation → Adapt (principle only).** The Laravel/PHP stack is already rejected elsewhere (`docs/plans/2026-08-02-open-source-erp-toolkit-audit.md`: "Poor fit / stack mismatch") — **reject** literally porting its package structure. But the *principle* — admin and shop are structurally separate route/theme/auth layers even inside one app — is precisely what `structural-critique.md` §3 finds **missing** in `apps/web`: no `middleware.ts` anywhere, the supplier portal inherits the customer `ShopChrome` with no `SupplierGate`, and staff-only `/procurement` sits beside customer-facing `/b2b` in the same route group. **Adapt**: add edge-level `middleware.ts` (or per-layout `@supabase/ssr` checks) for `/staff` and `/procurement`, plus a dedicated supplier layout/`SupplierGate` — `structural-critique.md` calls this "the highest-value, lowest-risk fix in this entire document" (no schema/RLS change, purely additive).

6. **OmniCart Clean-Architecture layering → Adapt the pattern, reject the code.** See §4.6 immediately below for the license finding. The layering itself — `data/` (repository, remote/local sources) → `domain/` (use cases) → `presentation/` (ViewModel + Compose) — is a well-established, uncopyrightable industry convention (same shape as Google's own "Now in Android" sample). It is independently exactly what `structural-critique.md` §1–2 recommends to fix `PosViewModel` (1,550 lines / 51 state fields / 61 handlers / zero tests / zero repository layer) and the `RpcClient` God interface. **Adapt the pattern only** — introduce repository/use-case types between the split ViewModels (item 3) and the domain-scoped RPC interfaces. Do not clone, copy, or reference `3wiida/OmniCart`'s actual source in any form.

## 4.6 OmniCart license verification — 2026-08-04 (user ask #4)

`docs/plans/2026-08-03-mobile-ui-oss-discovery.md` never actually evaluated OmniCart in its candidates table (checked directly — zero mentions); the "ambiguous licensing/provenance" characterization instead lives in **this doc's own §2.2** table. Verified live via GitHub:

- **Confirmed repo**: [`3wiida/OmniCart`](https://github.com/3wiida/OmniCart) — Kotlin/Jetpack Compose e-commerce sample, Clean Architecture + MVVM, Hilt DI, Retrofit, DataStore. 6 stars, 1 contributor, last pushed 2024-02-21. Matches the master design plan's description of "OmniCart" (Android commerce architecture, Compose, product discovery/search/cart/checkout/account/order flows).
- **License: none.** The repo has **no LICENSE file** — stronger than "ambiguous." Under default copyright law, all rights are reserved by the author; the code is **not licensed for use, copying, modification, or redistribution** by anyone else. This is a **harder stop than AGPL/GPL** would have been (those at least grant conditional reuse rights) — flagged per the adopt-first rule's "do not silently ignore a bad license" instruction.
- **Correction applied**: §2.2's OmniCart row previously read "ambiguous licensing/provenance" — that undersold the finding. Corrected: **no license = no legal right to copy/fork/vendor any of its code, at all.** The existing choice of Jetsnack (Apache-2.0, official Google `compose-samples`) for the customer app's visual shell/IA stands and is reinforced, not weakened, by this finding — Jetsnack remains the only code-reuse-eligible reference of the two. OmniCart's sole contribution to this plan is the **architecture-layering pattern** (verdict item 6 above), treated as general industry knowledge, never as a codebase to integrate or fork.
- **Companion edit**: `docs/plans/2026-08-03-mobile-ui-oss-discovery.md` gets an explicit OmniCart row added in the same pass (Reject code / reference pattern only) so a future reader doesn't re-open it as an integration candidate.

## 4.7 Mobile Redesign Directive — Android Customer + POS Layout (authoritative, 2026-08-04)

This is the spec `@android_agent` and `@management_app_agent` build against for the current top-priority work (see §5 Now, items 1–2). It layers the user's structural direction on top of `docs/decisions/2026-08-04-gsf-ux-behaviour-specification.md` — it does not replace that doc's IA findings.

### 4.7.1 Android customer app — OmniCart-style architecture + GSF merchandising IA

- **Structural reference: OmniCart's layering, not its code** (§4.6). Introduce a repository/use-case layer between each feature ViewModel and `RpcClient` in `apps/android-customer` (e.g. `CatalogRepository`, a `DealsRepository`/`GetHomeMerchandisingUseCase`, `VehicleRepository`) — this closes the "no repository/use-case layer" gap `architecture-map.md` §3 and `structural-critique.md` §0 both note as the actual delta vs. OmniCart-style Compose convention.
- **Visual/IA reference: Jetsnack shell stays as-is** (`docs/plans/2026-08-03-mobile-ui-oss-discovery.md` — Apache-2.0, already licensed and shipped). Do not re-open Jetsnack-vs-OmniCart — they answer different questions: Jetsnack = screens/nav/design system, OmniCart-*pattern* = internal layering behind those screens.
- **Merchandising home, vehicle-ID card, deals** — build per `docs/decisions/2026-08-04-gsf-ux-behaviour-specification.md` §3.2–§3.4 and its "Top 3" priority list:
  1. Add a promo banner + "Popular Part Types" tile row + a Deals section to `CatalogHome` (Shop-home) — no promo/merchandising surface exists today.
  2. Promote vehicle identification into an above-the-fold "Your Current Vehicle" card (large single CTA + "or select manually" fallback) wired to the **existing** VIN input + cascading maker/model/generation/engine selects. **Do not** add UK-plate lookup — `docs/decisions/2026-07-23-autodoc-shop-features.md`'s "no UK plate lookup" rule stays intact; only the *card-prominence pattern* is adopted, never GSF's underlying mechanism.
  3. Keep the existing 3-tab shell (Shop / Cart / Account) — do not adopt GSF's 5-tab bar; Deals surfaces as a Shop-home section, not a bottom-nav destination (already decided in the GSF spec §3.1, restated here as non-negotiable for this pass).
- New merchandising/vehicle-card logic goes into new, narrowly-scoped repository/use-case types (per the OmniCart-pattern above), not appended to the existing `CatalogViewModel` — this redesign must not create a second, smaller `PosViewModel`-style God object on the customer side.
- Scope: **Android only**, per the user's direction ("Android customer apps"). iOS parity is not required by this directive; route to `@ios_agent` separately if/when requested.

### 4.7.2 Tablet POS — cart-right / catalog-left layout (layout only, GTR theme)

**Current-state finding (verified by direct read, not assumed): the primary two-pane split already matches this directive.** `TabletTillLayout` in `apps/android-management/feature/pos/src/main/java/co/zw/nissangtr/management/pos/PosScreen.kt` (lines 203–239) renders, at ≥700dp width, a `Row` with `CatalogPane` (search/scan/grid, weight 1.15) placed **first (left)** and `CartPane` (lines/tenders/total, weight 1f) placed **second (right)**. This is already cart-right/catalog-left — treat it as the **locked reference layout**, not a target to build toward.

What the directive actually requires going forward:

1. **Lock and document** the existing `CatalogPane`-left / `CartPane`-right convention as non-negotiable for any future edit to `TabletTillLayout` — no change should flip this without a new decision doc.
2. **Extend the same convention to the rest of "other POS functions"** the user's ask names — companion/QR-pairing panel, manager-auth discount/void/refund prompts, quotations/park-cart panel. Audit where each currently renders inside `PosScreen.kt`'s ~860 lines (dialogs vs. inline expanding sections below the two-pane row) and keep them anchored to the catalog/left side or as modal overlays — never displacing or covering `CartPane`, so cart contents/total/tender state stay visible on the right through the whole till transaction.
3. **Colors: explicitly reject** `docs/decisions/2026-08-04-gsf-ux-behaviour-specification.md` §3.6/Gap #8's suggestion to accent-color the catalog grid per category, takepayments-EPOS-style. That prior spec recommended the colour treatment; **the user's direction supersedes it for this pass** — adopt only the takepayments reference's *density/layout* pattern (large touch tiles + persistent cart column), never its literal saturated per-category palette. Keep the existing GTR brand tokens (`packages/ui/brand-tokens.json`: steel `#12151C`, chalk `#F4F5F7`, CTA red `#C8102E`) as the only palette on this screen.
4. **Risk to manage during the touch** (cross-referencing `structural-critique.md` §1–2, per the user's explicit ask): `PosViewModel.kt` is 1,550 lines / 51 state fields / 61 public handlers with **zero test coverage**, and it is a God object *because* `RpcClient` is a 102-method God interface (§2: fixing the ViewModel without the interface "will simply move the same... dependency into four smaller files that all still import the same god object"). Anyone touching `PosScreen.kt`/`PosViewModel.kt` for this relayout is, by construction, already inside the highest-risk, least-tested file in the codebase. **Recommendation: decompose state during the touch, not after** — while moving/wrapping `CatalogPane`/`CartPane`/companion/manager-auth/quotation sections for the layout work, extract the corresponding slice of `PosUiState`/`PosViewModel` into its own ViewModel (`PosCartViewModel`, `PosCompanionViewModel`, `PosManagerAuthViewModel`, `PosQuotationViewModel` — the exact split `structural-critique.md` §1 already names) behind the OmniCart-pattern repository boundary (§4.5 item 6), instead of leaving the layout change as a pure Compose edit on top of the same 1,550-line file. Strong recommendation, not a blocking gate — don't let it stall the layout work if a full split can't land in the same pass, but at minimum do not add new state/handlers to the existing monolith while doing the relayout.
5. Owning lane: `@management_app_agent`. No schema/RLS change implied — client layout (and, per item 4, opportunistic-refactor) only.

---

## 5. Merged, prioritized roadmap

### Now (cheap, high-leverage, mostly consolidation — days, not weeks)

**Top priority as of 2026-08-04: items 1–2 below (mobile redesign directive, §4.7) — everything else in this table was already Now-priority before this update and is unchanged in substance, only renumbered.**

| # | Item | Owning lane | Effort |
|---|---|---|---|
| 1 | **POS tablet relayout**: lock the existing cart-right/catalog-left two-pane convention and extend it to companion/manager-auth/quotation panels; GTR theme only, no takepayments colours. Decompose `PosViewModel`/`RpcClient` God-object state during this touch, not after (`structural-critique.md` §1–2). See §4.7.2. | `@management_app_agent` | M |
| 2 | **Android customer merchandising redesign**: home promo/deals/vehicle-ID card per the GSF UX spec, built on an OmniCart-style repository/use-case layer behind the existing Jetsnack shell. See §4.7.1. | `@android_agent` | M |
| 3 | Write a lightweight **API/RPC inventory** doc (grep `supabase/migrations/*.sql` + `supabase/functions/*` for RPC/endpoint names, auth requirement, caller) — closes the one real gap from §2.1's "Not started" row without re-running the full 20-file audit | `@backend_agent` | S |
| 4 | Write a **security-findings / risk register** doc consolidating already-known Stub/Later items with security implications (kiosk plan's #20 login-lockout stub, #58 offline-login Later, missing dependency scanning) — note: the audit's own `security-findings.md` is currently empty (§4.5 flag above); this item should populate it, not duplicate it elsewhere | `/security-reviewer` | S |
| 5 | Add a short **decision doc** formally closing GSF-APK/APKLab/JADX-GUI as "not pursuing — superseded by Jetsnack + AutoDoc; IP risk" so it stops being a silent unimplemented "Should" | `/planner` (docs only) | XS |
| 6 | Add **dependency vulnerability scanning** to CI (`dependabot.yml` or `pnpm audit`/`pip-audit` step) | `@backend_agent` | S |

Verdict-derived follow-ups from §4.5 (Bagisto-style web `middleware.ts`/`SupplierGate`, Medusa-style `RpcClient` domain-split, Odoo-style customer/supplier link) are **not** duplicated into this table — each already carries its owning lane + effort in §4.5's verdict table. Sequence them via `/manager` after items 1–2 land; do not run them in parallel with the mobile redesign to avoid cross-lane churn on the same review cycle.

### Next (real product/infra gaps, medium effort)

| # | Item | Owning lane | Effort | Notes |
|---|---|---|---|---|
| 7 | **Production hardening pass** (master plan §13 Phase 11): basic observability/alerting on Supabase Edge Functions + Postgres, backup-restore drill runbook, load-test pass on POS checkout + storefront checkout RPCs | `@backend_agent` + `/manager` to scope | L | Needs its own adopt-first pass for an observability tool before build |
| 8 | Consolidate order-lifecycle naming across customer orders / POS sales / delivery jobs into **one documented state-machine reference** (tables can stay separate) | `@backend_agent` (docs + light status normalization) | M | Satisfies master plan §9.2 intent without a schema rewrite |
| 9 | Broaden audit-log coverage beyond POS void/discount/refund into HR onboarding, procurement approvals, warehouse adjustments | `@backend_agent` + `/security-reviewer` | M | |
| 10 | **Meilisearch catalog sync** — dual-read behind the store search API (already scaffolded via `docker-compose.satellites.yml`; flagged as "separate PR" in the 2026-08-02 OSS audit) | `@data_pipeline_agent` + `@web_agent` | M | This is the concrete "other tool not yet adopted" from the OSS-strategy findings, distinct from the master design doc itself |
| 11 | Wire remaining kiosk Stub items called out as non-blocking: login-lockout (#20), saleable-qty UI (#36), price-override till (#48), web staff emp#/idle parity (#63) | `@management_app_agent` + `@web_agent` | M each | |
| 12 | Offline login (#58, Argon2id cache) + PIN/NFC/badge auth (#16–19) | `@management_app_agent` + `@backend_agent` | M | **Requires a fresh ADR before build** — explicitly gated in `docs/decisions/2026-08-03-offline-sqlcipher-pos-cache.md` |
| 13 | **Bagisto-derived**: web `middleware.ts` + supplier `SupplierGate` + `/procurement` route-group move (§4.5 item 5) | `@web_agent` | M | Verdict: Adapt (principle only) |
| 14 | **Medusa-derived**: decompose `RpcClient` God interface into domain-scoped interfaces, e.g. `PosRpc`/`HrRpc`/`WarehouseRpc` (§4.5 item 3) | `@management_app_agent` + `@android_agent` | M | Verdict: Adapt at RPC-client boundary only; sequence with item 1 above (POS relayout touches the same files) |

### Later (large / speculative / already explicitly deferred)

| # | Item | Owning lane |
|---|---|---|
| 15 | Diagram canvas + hotspots polish (mobile parity Phase 3, already Later) | `@data_pipeline_agent` + `@web_agent`/mobile |
| 16 | Casbin / full action-matrix RBAC (kiosk #60) — only if `module_access` + RPC checks prove insufficient | `@backend_agent` |
| 17 | Traccar → OSRM → Gorse → Apache Superset satellite wiring beyond current compose scaffolding, per the OSS-strategy doc's own suggested sequence | `@backend_agent` + `@data_pipeline_agent` |
| 18 | Full Reply adaptive tablet rail for management (currently "hub restyled... full NavigationSuiteScaffold Later") | `@management_app_agent` |
| 19 | iOS staff kiosk (#64) | — (no current plan) |
| 20 | **GSF APK analysis** | Rejected — listed only to mark closed (see §4, §5 Now-5) |
| 21 | **Odoo-derived**: `party_links`-style customer/supplier link (§4.5 item 1) — needs a fresh ADR first (schema + RLS implications), not built ad hoc | `@backend_agent` |

---

## 6. Handoff

1. `/manager` sequences §5 Now items 1–2 first (mobile redesign directive, §4.7 — the current top priority; these are product-code lane work in `@management_app_agent` and `@android_agent` respectively, not docs-only). `/security-reviewer` is not required for item 1's layout-only scope, but **is** required if the opportunistic `PosViewModel`/`RpcClient` decomposition (§4.7.2 item 4) lands in the same pass, since it touches checkout/void/refund code paths.
2. Items 3–6 in §5 Now are docs-only/CI-only and can run in parallel with items 1–2 via `/planner`, `/security-reviewer`, and `@backend_agent` — no lane conflict with the mobile redesign.
3. Route each Next item to its named lane one at a time per standing session discipline; `/security-reviewer` on anything touching auth/schema; `/supabase-rls-auditor` on any new migration.
4. `/verifier` after each lane lands.
5. Do not re-run the master plan's full Phase 0–3 governance gate — it is superseded by the lighter continuous-ADR model already in force (see §4).
