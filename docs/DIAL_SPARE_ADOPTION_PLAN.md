# Dial-a-Spare → Nissan GTR Auto adoption plan

**Status:** Phase A complete · Phase B · Phase C E1 + **Epics A–G verified 2026-08-12** (E-Proc/E-WH/E-Del/E3/E6/E4/E-Sec/E-POS). §H progress: H2/H3/H5-Android Done; remaining: Temporal worker binary, B-MONEY-1, H5-iOS MapKit, B-OSRM-1 / B-PS-1.  
**Companion:** `docs/PROCUREMENT_WAREHOUSE_POS_SECURITY_PLAN.md`
**DIAL authority:** `DIAL_Consolidated_Plan_v4.md` → Agent Pack → Blueprint / Stitch / WA / D-53–D-60 companions  
**Nissan repo:** `nissan-gtr-auto-erp` @ `nissangtrauto.co.zw`  
**Do not treat DIAL v7 as SoR.**

---

## 0. Executive verdict

Nissan GTR Auto is a **single-company Zimbabwe spare-parts distributor ERP** (owned storefront + POS + WMS + driver app + staff web). Dial a Spare is a **multi-supplier agency marketplace**. Adopt DIAL’s **engineering spine** (integer money, outbox, PspAdapter habits, MapLibre + OSRM/VROOM delivery SoR, Temporal dispatch pattern, Resend/Brevo split, AI-off-critical-path). **Do not** force D-49/D-58 agency marketplace characterisation, informal→B2B hide, or `DIAL_OWNED` semantics onto Nissan — Nissan already **is** the principal/operator with owned + consignment stock.

| Product | Commercial model |
| --- | --- |
| Dial a Spare (DIAL) | Agent marketplace; “Sold by {Supplier}”; supplier heartbeat as stock signal; agency FDMS receipt types |
| Nissan GTR Auto | First-party / dealer-style distributor; one CoA + inventory SoR; multi-make catalog data OK; **no marketplace sellers** (ADR 2026-07-23) |

---

## 1. Phase A — factual inventory (summary)

### 1.1 Stack

| Layer | Nissan today |
| --- | --- |
| Monorepo | pnpm `apps/*` + `packages/*`; Android/iOS outside npm |
| Web | Next.js 16 + React 19 — customer + staff + supplier in one app |
| Mobile | Android customer / management / **delivery**; iOS customer |
| Backend | Supabase (Postgres + RLS + Edge) — ~111 migrations, ~152 tables |
| Payments | ContiPay, Paynow, EcoCash; COD / store credit |
| Search | Postgres FTS SoR; Meili optional satellite |
| Maps | Web staff: MapLibre; Android delivery/customer: **Google Maps + Directions** |
| Email | Resend-compatible only — **no Brevo** |
| WA | Official Cloud API + Flows FastAPI (good — keep) |
| Money | `NUMERIC(18,2)` + `USD`/`ZIG` — **not** `amountMinor` |
| AI | Gemini Edge for reports/CRM narrative; numeric KPIs without LLM |
| Fiscal | **Hard exclusion** of ZIMRA/FDMS (differs from DIAL D-40a/D-59) |

### 1.2 Domain maturity

| Domain | Maturity | Notes |
| --- | --- | --- |
| Finance / ledger | Strong schema + RPCs + staff UI | Decimal money; deepen toward integer minor + webhook SoR |
| Stock / warehouse | Strong (bins, consignment, recon, POS) | Keep as SoR; adapt DIAL catalogue/fitment patterns only |
| Deliveries | Strong jobs/POD/GPS; Google SoR on Android | Redesign maps/routing + Temporal dispatch package |
| Job management | = `delivery_jobs` + pick/DN | Align naming to DIAL `packages/delivery` SM |
| AI / CRM | Edge workers + staff analytics | Narrow Spare AI scope; Promptfoo+human promote |
| Email | Resend transactional path | Add Brevo for promo/CRM |

### 1.3 Tech debt (adoption risks)

- Shared-DB + hundreds of `SECURITY DEFINER` RPCs; thin clients; God ViewModels
- Flat migration stream; rapid schema growth
- PowerSync stub; OSRM compose commented; Fake RPC when keys missing
- README lag (still “four surfaces”; delivery app already exists)
- Rewriting live money columns in one shot = high blast radius

Full inventory evidence: in-repo audits under `docs/audit/2026-08-04-master-audit/`.

---

## 2. Commercial-model differences (mandatory)

| Topic | DIAL lock | Nissan decision |
| --- | --- | --- |
| Agency vs principal | D-49 / D-58 agent; no owned SKUs | **Principal / owned inventory + consignment** — keep |
| Informal B2B hide | D-49 filter at Meili/API | N/A unless multi-tier inventory introduced |
| Multi-supplier failover | Shadow supplier offers | Warehouse transfer / backorder / OEM order |
| Mercur multi-vendor UX | Pattern for marketplace | Prefer **Nimara / Medusa DTC** polish only |
| SUPPLIER_COOP promos | Supplier-funded | OEM/dealer campaigns or internal margin |
| FDMS / agency receipts | D-40a / D-59 | Nissan **excludes ZIMRA** today — counsel before reopening; do not silently add |
| Tech WHT 30% | D-50 | Out of Spare storefront path; leave payroll-tax exclusion |
| USD browse / ZiG checkout | D-57 | **Adopt** — Nissan already has `daily_exchange_rates`; align cart display |

Cite this section in every PR that touches money, stock visibility, or fiscal.

---

## 3. Current vs target architecture (by domain)

### 3.1 Financial management

| | Current | Target (DIAL-aligned) |
| --- | --- | --- |
| Representation | `NUMERIC` + floaty TS `Money.amount` | `amountMinor: bigint` + `currency` in shared types; DB migrate via dual-read → cutover |
| SoR | Postgres ledger + payment intents | Keep Postgres ledger; **not** Medusa/Formance runtime |
| PSP | ContiPay/Paynow/EcoCash Edge | Formalize **PspAdapter** registry pattern (copy DIAL D-43 habits into `@gtr/payments`) |
| Webhooks | Present; harden idempotency | Webhook-as-truth + outbox; AI never writes payable amounts |
| FX | `daily_exchange_rates` | D-57: browse/cart USD; ZiG only at pay step with `fx_rate_id` |
| Job Reserve | N/A (principal prepaid/COD) | Optional **order hold** liability for prepaid online — not multi-supplier escrow |

**Replace:** ad-hoc float math in clients.  
**Adapt:** existing CoA/JE/RPCs.  
**Integrate:** DIAL money-path review habits / PspAdapter shape.  
**Defer:** full bigint column rewrite until dual-write proven; FDMS unless counsel reopens exclusion.

### 3.2 Stock and warehouse

| | Current | Target |
| --- | --- | --- |
| SoR | `warehouses` / `stock_*` / bins / consignment | **Keep** — extend dual-WH ops |
| Dual warehouse | MAIN + others | **WH1 receiving / WH2 storefloor**; transfers approval-tracked |
| Master stock | per-warehouse levels | `v_master_stock` / `list_master_stock` totals + WH1 + WH2 |
| Procurement | RFQ-centric hub | **Relationship preferred suppliers** — see `docs/PROCUREMENT_WAREHOUSE_POS_SECURITY_PLAN.md` |
| Catalog | EPC + PartSouq multimake pipeline | Keep dual-entry vehicle/EPC; Meili as derived index |
| Oversell | POS + levels | Heartbeat patterns optional for consignment suppliers only |

**Replace:** RFQ-win as mandatory authorization path.  
**Adapt:** InvenTree (MIT) PO/GRN vocabulary — not runtime.  
**Integrate:** existing GRN + QR + `create_stock_transfer` / `approve_stock_transfer`.  
**Defer:** SandPIM runtime.

### 3.3 Deliveries (+ delivery app redesign)

| | Current | Target |
| --- | --- | --- |
| Job SoR | `delivery_jobs` + RPCs | Keep tables; extract `@gtr/delivery` contracts + Temporal **`DeliveryDispatchWorkflow`** pattern (D-45) |
| Map render | Google Maps Compose (Android); MapLibre (web) | **MapLibre** Android + web (D-44) |
| Distance/route | Google Directions | **OSRM** (+ VROOM for multi-stop post-accept) |
| Assign | SQL auto-assign | Offer → accept/reject/timeout → requeue / FIFO (AWS Last Mile **algorithms**, MIT-0 — reimplement) |
| Reject | Fleetbase / Google as SoR | Pattern-only for Fleetbase; never AGPL runtime |

**Replace:** Google Directions as distance SoR; Google as courier map SoR.  
**Adapt:** existing POD/GPS/presence/panic RPCs.  
**Integrate:** foodhub-compose rider UX patterns; OSRM satellite already stubbed in compose.  
**Defer:** full MapLibre Compose UI skin until OSRM routing path is default.

### 3.4 Job management

Nissan “jobs” = logistics `delivery_jobs` (not Tech diagnostic jobs). Target state machine mirrors DIAL Spare fulfilment: pick → DN → dispatch offer → run → POD → settle. Admin MapLibre board stays SoR for live track via `delivery_locations` + Realtime.

### 3.5 AI analytics and CRM

| | Current | Target |
| --- | --- | --- |
| Scope | Reports, stores insights, CRM promos, demand scaffold | Spare-like: analytics/`crmInsight` only; **never** write prices/stock qty/payable |
| Eval | Informal | Promptfoo golden gates + **human promote** (D-54) |
| Inbox | In-app chat | Optional Chatwoot later (Tier 2) |

**Replace:** any path where Gemini invents money.  
**Adapt:** existing Edge + ADR constraints.  
**Defer:** auto-PO from demand forecast.

### 3.6 Resend + Brevo

| Channel | Current | Target |
| --- | --- | --- |
| Transactional (OTP, receipts, password, AI report PDF) | Resend | **Resend** (keep) |
| Promo / CRM journeys | Resend via `process-crm-promos` | **Brevo** adapter; fail closed if unset (local stub OK) |
| Consent | `marketing_opt_in` | Keep Postgres as consent SoR |

---

## 4. Mapping table — Nissan → DIAL pattern / package / repo

| Nissan module | DIAL package / pattern | Tool / repo | Action |
| --- | --- | --- | --- |
| `packages/shared` money | `packages/shared` amountMinor | — | **Adapt** (E1 started) |
| Ledger / JE RPCs | `packages/ledger` | Formance Console **UI patterns only** | Adapt in-place; extract `@gtr/ledger` later |
| ContiPay/Paynow/EcoCash Edge | `packages/payments` PspAdapter | DIAL D-43 | Integrate registry |
| Warehouse / stock | (DIAL has no owned WMS) | In-repo | Keep; document principal SoR |
| Catalog / EPC | `catalogue` + Meili | SandPIM schema ref; csv-import | Adapt |
| `delivery_jobs` + Android delivery | `packages/delivery` + Temporal workflow | MapLibre; OSRM; VROOM; foodhub-compose; AWS Last Mile algos | Replace maps SoR; adapt jobs |
| Staff logistics MapLibre | admin dispatch | — | Keep / deepen |
| AI reports / CRM | `packages/ai` narrow scope | Promptfoo, Langfuse, Gemini via LiteLLM habit | Adapt |
| Resend email | `notifications` Resend | Resend | Keep |
| CRM promo email | Brevo | Brevo API | **Integrate** (E1) |
| WA Cloud + Flows | official WA only | DIAL WA companion | Keep |
| Search | Meili derived | Meilisearch CE | Integrate dual-read |
| FX rates | `fx` / D-57 | — | Adapt checkout display |
| Chat | Chatwoot optional | Chatwoot | Defer |
| Analytics BI | Metabase optional | Metabase | Defer |

### Copy vs submodule strategy

- **Do not** invent a fourth money/delivery stack.
- Prefer **pattern copy** of DIAL contracts into `@gtr/*` packages (license-clean, Nissan-owned).
- Optional later: git submodule or published workspace packages **only** for truly shared pure types — never couple Nissan production to unfinished DIAL package stubs.
- DIAL repo remains read-authority; Nissan implements.

---

## 5. Suggested repos for gaps (licence-aware)

| Gap | Suggest | Licence / note |
| --- | --- | --- |
| DTC storefront polish | Nimara Store / Medusa DTC starters | UX donor only |
| Courier Android MapLibre | MapLibre Native + foodhub-compose patterns | Strip Google/Stripe |
| Dispatch algorithms | AWS Last Mile Hyperlocal | MIT-0 — reimplement |
| Multi-stop optimize | VROOM | Affero? — self-host; evaluate AGPL ops like DIAL |
| CSV supplier import UX | tableflowhq/csv-import | Check licence before vendor |
| Ledger explorer UI | Formance Console patterns | Never runtime SoR |
| Promo engine patterns | Medusa Promotion / OfferKit → in-repo only | No OfferKit as live SoR |
| CRM inbox | Chatwoot | Optional Tier 2 |
| Eval harness | Promptfoo | D-54 |
| Reject | Fleetbase runtime, Baileys, Google/Mapbox distance SoR, Medusa/Formance money SoR | — |

---

## 6. Replace / adapt / integrate / defer

### Replace
- Google Directions as **distance/route SoR** (OSRM)
- Google Maps as **courier map SoR** (MapLibre Native) — phased
- Float `Money.amount` as the **canonical** client type (keep compat wrappers)
- CRM promo sends via Resend (Brevo)

### Adapt
- Existing ledger, warehouse, POS, delivery RPCs, WA Cloud, receipt outbox
- Staff MapLibre tracking
- Gemini narrative with stronger money-write bans + Promptfoo later

### Integrate
- `@gtr/money` / amountMinor helpers (E1)
- `@gtr/notifications` Resend + Brevo (E1)
- `@gtr/delivery` dispatch contracts + OSRM client (E1)
- Temporal worker host (E2+) for `DeliveryDispatchWorkflow`
- Meili dual-read completion
- PspAdapter registry package (E2)

### Defer
- Full DB bigint money cutover
- Full MapLibre Compose UI (after OSRM default)
- ZIMRA/FDMS (exclusion stands until counsel)
- Chatwoot / Metabase / PowerSync live SDK
- Marketplace agency features

---

## 7. Thin verticals / tracer order (D-52)

### E1 — Money types + notifications split + OSRM routing spine (**this session**)

**Why not delivery-only first?** Delivery tables/POD already work; the highest architectural debt vs DIAL locks is (1) money representation, (2) email channel split, (3) Google-as-route-SoR. Proving package boundaries on money+notifications+OSRM unlocks later Temporal dispatch without rewriting warehouse.

**Tracer steps**
1. `amountMinor` types + conversion helpers in `@gtr/shared` (dual with legacy `Money`)
2. `@gtr/notifications` + Edge Brevo adapter; CRM promos prefer Brevo
3. `@gtr/delivery` contracts (offer SM + Temporal workflow names)
4. Android `OsrmRouteFetcher` + prefer OSRM when `OSRM_URL` set
5. Living docs + this plan

**DoD (E1)**
- [x] Plan doc + commercial caveat
- [x] amountMinor API + tests
- [x] Brevo adapter + CRM worker preference
- [x] Delivery package contracts
- [x] OSRM route fetcher + unit parse test
- [x] README/CHANGELOG/ENHANCEMENTS/BUGS updated
- [ ] Dual-write money columns (next ticket)
- [ ] MapLibre Native map surface (next ticket)
- [ ] Temporal worker deployed (next ticket)

### E2 — Delivery dispatch Temporal + MapLibre Android
- Temporal `DeliveryDispatchWorkflow` activities calling existing RPCs
- MapLibre Native map in `:maps-nav` (Google tiles optional fallback only)
- VROOM multi-stop after accept
- Staff board uses same ETA source enum (`osrm`)

### E3 — PspAdapter registry + D-57 checkout FX display
- Extract adapters; USD browse / ZiG-at-checkout UX audit on web + WA

### E4 — AI Promptfoo gate + human promote for CRM/report copy
### E5 — Money bigint dual-write migration
### E6 — Meili dual-read default + Catalogue Factory review queue polish

---

## 8. DoD checklists per epic

### Epic Finance
- [ ] All new APIs use `amountMinor` + currency
- [ ] Webhooks idempotent; outbox for receipt/notification side effects
- [x] AI cannot write payable fields (tests/Semgrep-style grep) — Epic C C6: `psp.test.ts` + process-ai-reports / process-crm-promos
- [x] D-57 display rules on Spare-like surfaces — web cart `fxRateId` (Epic C C2/C5); WA Flow gap noted C7
- [ ] Money-path review skill habits applied before merge

### Epic Stock/WMS
- [x] Principal stock remains SoR
- [x] Meili never invents qty — evidence: `@gtr/supabase-client` strip + contract test; Edge `mapHit` omits inventory; ADR `2026-08-05-meilisearch-catalog-search.md`
- [x] Consignment vs owned valuation documented — `docs/decisions/2026-08-12-consignment-vs-owned-valuation.md`

### Epic Delivery
- [x] OSRM (or VROOM) is distance SoR when configured
- [x] MapLibre is courier map SoR
- [x] Temporal dispatch SM with evidence (accept/reject/timeout) *(package + Edge bridge; full worker binary §H)*
- [x] No Fleetbase runtime; no Baileys
- [x] Responsive staff web tracking DoD

### Epic Jobs
- [ ] Single SM documented pick→DN→job→POD→settle
- [ ] Manual override audited

### Epic AI/CRM
- [x] Promptfoo gates; human promote *(offline safe-narrative provider; README promote path; H3 CI offline default + optional real provider secrets)*
- [x] Promo copy cannot include invented prices *(not-contains amount_minor / cash-out asserts)*

### Epic Email
- [ ] Resend = transactional only
- [ ] Brevo = promo/CRM
- [ ] Consent SoR = Postgres

---

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| Live NUMERIC → bigint rewrite | Dual types → dual-write → backfill → cutover; never big-bang |
| Breaking Android maps overnight | OSRM first (HTTP); MapLibre UI second; Google fallback flagged deprecated |
| God ViewModels | Extract packages gradually; no wholesale Kotlin rewrite in E1 |
| Licence infection (AGPL) | Fleetbase pattern-only; VROOM self-host legal review |
| Forcing agency locks | §2 of this doc; PR checklist |
| README/docs drift | Living docs required in same PR (Blueprint §8.0.2 habit) |
| ZIMRA exclusion vs DIAL FDMS | Keep exclusion until explicit counsel ticket |

---

## 10. Next tickets (after E1)

1. ~~**E2a** — Temporal worker + `DeliveryDispatchWorkflow` wrapping assign/offer RPCs~~ **Bridge done** (`packages/delivery` assign-bridge + edge `delivery-dispatch-cycle`); full Temporal binary optional.
2. ~~**E2b** — MapLibre Native in courier tracking~~ **Done** (`MapLibreJobMap` on JobDetailScreen).
3. ~~**E3a** — `@gtr/payments` PspAdapter registry~~ **Done**.
4. ~~**E3b** — Web D-57 USD browse + ZiG-at-checkout~~ **Done** (`buildCheckoutDisplay` in cart-checkout; `fxRateId` from `daily_exchange_rates.id` — see `docs/plans/2026-08-12-epic-c-payments-d57-dod.md`).
5. ~~**E4** — Promptfoo outline for CRM/report narratives~~ **Done** (`promptfoo/`).
6. ~~**E5** — `amount_minor` dual-write on PO money paths~~ **Done** (migration `20260812030000_*`); broader ledger later.
7. ~~**E6** — Meili dual-read default for catalog search~~ **Done**.

---

## 11. Citation index (DIAL)

C-4…C-6 · §3.2–§4.3 · §4.6 · §5.14–§5.15 · §6.12–§6.16 · D-38…D-46 · D-49 · D-54 · D-56 · D-57 · D-58 · D-59 · D-60 · Agent Pack §5–§10 · OSS Stitch · Blueprint §8.0.1–8.0.2 · `docs/planning/` diagrams

---

*Owned by Nissan GTR Auto engineering. DIAL remains architecture authority for patterns; commercial model remains principal distributor.*
