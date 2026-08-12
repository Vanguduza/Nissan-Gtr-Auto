# Nissan GTR Auto — procurement, dual-warehouse, POS, security (founder 2026-08-12)

**Authority:** This doc extends `DIAL_SPARE_ADOPTION_PLAN.md`. Nissan remains a **principal distributor** (not Dial-a-Spare agency).  
**Commercial rule (founder):** Shop / replenishment does **not** authorize suppliers from winning RFQ quotations. Long-standing supplier relationships are the SoR; RFQ remains optional legacy for rare spot buys.

---

## 1. OSS / tool picks (pattern donors — Postgres remains SoR)

| Need | Pick | Licence | Tier | Use |
| --- | --- | --- | --- | --- |
| Procurement / PO / GRN UX & state vocabulary | [inventree/InvenTree](https://github.com/inventree/InvenTree) | MIT | **Reference** | PO → approve → receive; part-centric stock; barcode/QR habits — **do not** run InvenTree as a second DB |
| Buying workflow / multi-step approval UX | [frappe/erpnext](https://github.com/frappe/erpnext) Buying | GPL-3 | **Reference only** | Progress stages & approval gates — **never** add as dependency |
| CSV supplier / catalog import UX | [tableflowhq/csv-import](https://github.com/tableflowhq/csv-import) | MIT | Tier 1 habit | Supplier roster + PO line paste |
| Restock / demand analytics | Existing `forecast_suggestions` + Edge `demand-forecast` / `stores-insights`; satellite Prophet notes in `infra/satellites/PHASE2_PROPHET_GORSE.md`; optional **Nixtla statsforecast** (Apache-2.0) later | — | Integrate | AI **suggests only**; humans build POs with quoted figures |
| Product analytics UI | Metabase (already DIAL-aligned) | AGPL self-host | Tier 2 | Dashboards over `master_stock_*` + forecast tables |
| Delivery auto-assign / FIFO | DIAL D-45 + AWS Last Mile Hyperlocal (MIT-0) → `@gtr/delivery` | — | Locked | Temporal `DeliveryDispatchWorkflow` + SQL auto-assign already started |
| POS / kiosk UX | DIAL D-38: CoolMallKotlin / Nimara / Medusa DTC **patterns** + `@gtr/ui` tokens | MIT | Pattern | Redesign tablet + web POS — native Android stays (no Expo) |
| Security | DIAL D-47/D-48 habits | — | Adopt | See §7 |

**Reject as SoR:** ERPNext/InvenTree runtime DB, Fleetbase, Baileys, Google/Mapbox distance SoR, Medusa/Formance money SoR, RFQ-win as mandatory PO path.

---

## 2. Target process (happy path)

```text
AI forecast / season / min-stock triggers
        ↓ (suggestion only — never auto-PO / never payable amounts)
Procurement builds PO from preferred supplier roster + quoted unit costs
        ↓ submit
Assigned finance/admin approves
        ↓ auto
Finance fund release under requesting official (PO created_by) + ledger event
        ↓
Supplier delivers → procurement GRN (invoice = GRN attachment)
  • Fast path: oem_part_number + qty_received (maps stock_items)
  • Optional: in-house QR stickers → same OEM → camera / cart / receive
        ↓ stock into WH1 (receiving)
WH1 → WH2 (storefloor) stock transfer order → dual approval → trackable like PO
Master stock view: total + WH1 qty + WH2 qty per OEM
Delivery jobs: auto offer / FIFO queue per DIAL D-45 (@gtr/delivery)
```

### Warehouses

| Code | Role |
| --- | --- |
| `WH1` / legacy `MAIN` | All goods received (bulk) |
| `WH2` / storefloor | Sales floor / POS pick |

Migration ensures both exist and aliases MAIN↔WH1 when needed.

---

## 3. Module DoD (tracer)

### E-Proc — Relationship procurement
- [x] Preferred supplier roster CRUD (add/remove + full details) in procurement dashboard
- [x] Manual PO from roster (quoted figures); **not** gated on winning quotation
- [x] Cool progress tracker UI (draft → submitted → approved → funds_released → partially_received → received → closed)
- [x] On approve: `procurement_fund_releases` + finance event under `created_by`
- [x] RFQ marked secondary / optional in nav copy

### E-WH — Dual warehouse + master stock
- [x] WH1 receive SoR; WH2 storefloor
- [x] Transfer WH1→WH2 requires approval + progress tracker *(existing `create_stock_transfer` / `approve_stock_transfer` RPCs + staff transfers UI)*
- [x] `v_master_stock` (or RPC) totals + WH1 + WH2 columns
- [x] GRN by part number + qty; QR maps to OEM *(web GRN OEM+qty fast path + invoice attach)*

### E-POS — Dial UX redesign
- [x] Web POS uses `@gtr/ui` tokens / CoolMall-like density
  - **Evidence:** `apps/web/components/staff-pos-shell.module.css` uses `--gtr-chalk` / `--gtr-mist` / `--gtr-steel` / `--gtr-red` / `--gtr-radius-staff` (+ display/body fonts); panel reuses `account.module.css` staff density (`--staff-radius*`, field min-heights).
- [x] Tablet kiosk POS visual pass (Material 3 + brand tokens)
  - **Evidence:** `PosScreen.kt` wraps `GtrTheme(GtrDensity.Standard)` + Shop staff chrome; dual-pane ≥700dp catalog|cart; `POS_TOUCH_MIN` 48dp; FlowRow chip wrap; cart line controls stacked (no H-scroll traps). Offline SqlCipher + Bridge QR/ESC-POS retained — no Expo. See `docs/plans/2026-08-12-pos-dial-ux-redesign.md` tablet QA.
- [x] Responsive desktop + mobile staff POS *(web stacked + breakpoints; Android two-pane ≥700dp)*
  - **Evidence:** redesign plan QA `docs/plans/2026-08-12-pos-dial-ux-redesign.md` — 1280/390 web + Android ≥700dp dual-pane evidenced.
- [x] WH2 storefloor is POS pick source (WH1 receiving only)
  - **Evidence:** `listSaleableWarehouses` / `isPosSaleableWarehouse` in `apps/web/lib/staff-pos.ts` + panel copy.

### E-Del — Auto dispatch
- [x] Offer → accept/reject/timeout → requeue / FIFO when none available *(`@gtr/delivery` + edge `delivery-dispatch-cycle`; autoAcceptOffers opt-in; timeout tests)*
- [x] OSRM distance SoR; MapLibre render *(MapLibreJobMap primary on JobDetailScreen; Google deprecated fallback; B-MAP-1 full skin / B-OSRM-1 compose deferred §H)*

### E-Sec — DIAL security baseline
- [x] No body `userId`/role trust; JWT/session only *(Semgrep `no-body-identity` + HARDENING §7)*
- [x] Webhook signature + idempotency *(PSP webhooks + Semgrep `webhook-signature`)*
- [x] Fail-closed worker secrets *(`assertWorkerSecret`)*
- [x] No service_role in client bundles *(Semgrep `no-client-secrets`)*
- [x] RLS smokes green; HARDENING.md checklist current *(Epic A smokes; HARDENING §7 synced to landed CI)*

---

## 4. Implementation status (this landing)

| Artifact | Status |
| --- | --- |
| This plan | Living — E-Proc/E-WH/E-Del/E-Sec verified; **E-POS Done** (web + Android tablet QA evidenced, Epic G) |
| `@gtr/procurement` domain + progress tracker | **Done** (verified) |
| Migration preferred suppliers / fund release / master stock / WH codes | **Done** (`20260812010000`–`70000`) |
| Migration GRN invoice + amount_minor dual-write | **Done** |
| Procurement dashboard + suppliers + tracker UI | **Done** (live `progress_step` bind) |
| Manual preferred PO + GRN web panels | **Done** (OEM resolve + draft-only invoice bind) |
| Delivery FIFO + SQL assign bridge + edge | **Done** (Epic B; autoAcceptOffers opt-in) |
| MapLibre courier map on job detail | **Done** (primary SoR; Google deprecated) |
| POS Dial UX (web shell + Android GtrTheme) | **Done** — web 1280/390 + Android ≥700dp dual-pane / 48dp / SqlCipher+Bridge evidenced |
| `@gtr/payments` PspAdapter + D-57 cart display | **Done** (Epic C) |
| Meili dual-read `searchCatalog` | **Done** (Epic D; qty strip) |
| Promptfoo outline + Semgrep/Checkov CI | **Done** (Epics E / F) |
| Android preferred-supplier PO | **Web-first** — hub copy points to `/procurement/orders/new` |

---

## 5. Next tickets

1. ~~Wire `create_purchase_order` UI for preferred-supplier manual lines (web)~~ **Done** — Android remains web-first (DoD)
2. ~~Attach supplier invoice upload on GRN panel~~ **Done** (+ storage bind / draft-only `20260812060000`)
3. MapLibre Native courier map (E2b) — **Epic B** (candidate wiring exists; DoD/evidence open)
4. Promptfoo gate on AI report/CRM edges — **Epic E**
5. Semgrep/Checkov CI port from DIAL D-48 — **Epic F**

**Optional follow-ups (not blocking E-Proc/E-WH):** full Temporal worker binary; Android native preferred-PO screen; Promptfoo CI job with real model provider; fund-release insert-once (no ON CONFLICT money rewrite).
