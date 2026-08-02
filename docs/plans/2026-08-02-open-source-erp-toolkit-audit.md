# Open-Source ERP Toolkit Audit — Nissan GTR Auto ERP

**Date:** 2026-08-02  
**Source PDF:** `c:\Users\j\Desktop\New folder (3)\erp-open-source-toolkit.pdf`  
**Target system:** Nissan GTR Auto ERP monorepo (Supabase + Next.js web + Android management/customer/delivery + iOS customer)  
**Hard exclusions (confirmed):** no ZIMRA, no payroll/tax modules  

**Scope:** research + recommendations only. No clones, no ERP rewrites.

---

## 1. PDF summary

### Purpose
Research pass mapping free/open-source projects (with real APIs or readable source) to a multi-platform ERP module list, so implementers can extend existing codebases instead of building every module from a blank file.

### Recommended architecture in the PDF
| Layer | PDF pick |
| --- | --- |
| Backend of record | **ERPNext** (Frappe) for Financial, HR, Payroll, Inventory, Procurement |
| POS / storefront | Stay in ERPNext **or** Bagisto / Medusa in front |
| Satellites | Traccar (GPS), Casbin (RBAC), Meilisearch (search), Gorse + Metabase/Superset (analytics), optional Hyperledger / hash-chain |

### Categories covered
1. Financial management  
2. HR & onboarding  
3. Payroll  
4. Identity (QR ID cards)  
5. Inventory (+ blockchain stock ledger options)  
6. Procurement / buying  
7. Logistics / live GPS  
8. Omnichannel e-commerce & POS  
9. Visual parts catalog / VIN / search / cross-sell  
10. Returns & tiered B2B pricing (config, not new tools)  
11. MPM Companion — RBAC + notice board  

### Every named product / repo / URL in the PDF

| Name | URL / identifier | License (per PDF) |
| --- | --- | --- |
| ERPNext | github.com/frappe/erpnext | GPLv3 |
| Frappe HR / Payroll | github.com/frappe/hrms | GPLv3 |
| frappe_docker | GitHub (Frappe Docker) | (companion to ERPNext) |
| qrcode (Python) / node-qrcode | npm/PyPI packages | MIT |
| ZXing | Android barcode lib | Apache-2.0 |
| Google ML Kit Barcode Scanning | Google on-device SDK | Free (proprietary SDK) |
| SourceCodester Employee ID Card Generator | Django reference | (demo/reference) |
| Hyperledger Fabric | Linux Foundation | Apache-2.0 |
| awesome-hyperledger-fabric | github.com/wearetheledger/awesome-hyperledger-fabric | — |
| Traccar | github.com/traccar/traccar | Apache-2.0 |
| Traccar Client | companion mobile GPS app | (Traccar org) |
| OSRM | Open Source Routing Machine | BSD |
| TailPOS | github.com/bailabs/tailpos | (ERPNext offline POS) |
| react-native-vision-camera + barcode plugin | RN camera stack | (actively maintained; PDF warns against abandoned `react-native-qrcode-scanner`) |
| Bagisto | github.com/bagisto/bagisto | MIT |
| Bagisto B2B suite | github.com/bagisto/b2b-suite | MIT (suite) |
| Medusa.js | medusajs.com / GitHub | MIT |
| Vendure | (headless GraphQL commerce) | GPLv3 core + paid enterprise |
| NHTSA vPIC API | vpic.nhtsa.dot.gov | Free public |
| nhtsa-api-wrapper / vpic-api | JS / Python wrappers | — |
| Auto Care ACES / PIES + VCdb/PCdb/Qdb/PAdb | Industry standards | Paid association data |
| Meilisearch | Meilisearch | MIT (PDF) |
| Typesense | Typesense | GPLv3 |
| Gorse | github.com/gorse-io/gorse | Apache-2.0 |
| Metabase | Metabase free edition | AGPLv3 |
| Apache Superset | Apache Superset | Apache-2.0 |
| Casbin | github.com/apache/casbin | Apache-2.0 |
| Keycloak | Keycloak | Apache-2.0 |
| Wiki.js / Outline | Internal announcements hubs | (open source / see notes) |
| Odoo | Mentioned for payroll comparison & returns/pricelists | Community vs Enterprise payroll note |

### Suggested Cursor sequence (from PDF)
1. Stand up ERPNext + Frappe HR via Docker first  
2. Generate custom fields/workflows from Frappe doctype JSON  
3. Decide POS Path A vs B before mobile storefront work  
4. Deploy satellites (Traccar, Casbin, Meilisearch, Gorse) separately over APIs  
5. Budget parts catalog as a real build (no full OSS shortcut)

### GTR-specific verdict on PDF architecture
The PDF’s **ERPNext-first** recommendation conflicts with this monorepo. GTR already runs on **Supabase + Next.js + native apps** with ledger, POS, warehouse, logistics, finance, and fleet MVP. Replacing that with Frappe/ERPNext would discard existing investment and pull in **HR/Payroll** (explicitly excluded). Treat the PDF as a **module-by-module menu of satellites and libraries**, not as a mandate to rebase onto ERPNext.

---

## 2. GitHub cross-check (as of ~2026-08-02)

Activity/stars are approximate from public GitHub metadata; treat as directional.

| Project | License (verified) | Stack | Stars (approx) | Last activity signal | Notes |
| --- | --- | --- | --- | --- | --- |
| frappe/erpnext | GPLv3 | Python / Frappe / MariaDB|Postgres | ~37k | Active (v16.x Jul 2026) | Full ERP replacement |
| frappe/hrms | GPLv3 | Python / Vue PWA | ~8k | Active (v16 Jul 2026) | HR + **Payroll** |
| medusajs/medusa | MIT | Node / TypeScript | ~35k | Active (v2.18.0 Jul 2026) | Headless commerce; Next.js storefront |
| bagisto/bagisto | MIT | PHP Laravel / Vue | ~27k | Active (v2.4.8 Jul 2026) | Wrong language stack for GTR |
| vendurehq/vendure | GPLv3 (+ plugin exception) | NestJS / GraphQL / TS | ~8k | Active (v3.7 Jul 2026) | Open-core; GPL core |
| bailabs/tailpos | GPLv3 | React Native + ERPNext | ~0.7k | Stale / community reports abandoned | ERPNext-only; skip |
| traccar/traccar | Apache-2.0 | Java server | ~7.5k | Active through mid-2026 | REST/WS GPS; phone client exists |
| Project-OSRM/osrm-backend | BSD-2-Clause | C++ | ~8k | Active | Routing / ETA |
| meilisearch/meilisearch | MIT CE + BUSL EE | Rust | ~59k | Active mid-2026 | **Use Community Edition only** |
| typesense/typesense | GPL-3.0 (server); Apache clients | C++ | ~26k | Active | Solid alt; GPL vs MIT CE |
| apache/casbin + node-casbin | Apache-2.0 | Go core; **TS node-casbin** | ~3k (node) | Active (node v5.51 mid-2026) | Fits Next.js/API layer |
| keycloak/keycloak | Apache-2.0 | Java | ~36k | Active | Heavy SSO; overlaps Supabase Auth |
| gorse-io/gorse | Apache-2.0 | Go | ~10k | Mature | Cross-sell recommender |
| metabase/metabase | AGPLv3 (OSS) + commercial | Clojure/JS | ~48k | Active | AGPL care if embedding externally |
| apache/superset | Apache-2.0 | Python / TS | ~74k | Active | Prefer for commercial-friendly BI |
| hyperledger/fabric | Apache-2.0 | Go | ~17k | Mature | Ops-heavy; usually overkill |
| soldair/node-qrcode | MIT | JS | ~8k | Quiet (last push ~2024) | Still widely used |
| zxing/zxing | Apache-2.0 | Java (+ ports) | ~34k | Mature | Android scanning |
| outline/outline | **BSL 1.1** (not OSI OSS) | Node / React / TS | ~40k | Active | PDF called it OSS; license is BSL |
| requarks/wiki | AGPLv3 | Node / Vue | ~29k | Active | Overkill for notice board |
| NHTSA vPIC | Public API | HTTP | — | Official US DOT | US-centric VIN decode |

---

## 3. Integrate vs skip for GTR Auto ERP

### Integrate / adapt (high value, stack-aligned)

| Area | Candidate | Why |
| --- | --- | --- |
| Parts search | **Meilisearch CE** | Typo-tolerant + hybrid search for SKU/keyword; wires to existing catalog without replacing Postgres |
| Fleet GPS | **Traccar** (+ Client Android) | Live positions, geofences, REST/WS; complements fleet MVP; dispatch UI stays custom in GTR |
| Delivery routing | **OSRM** | ETAs / route optimization for logistics app; pair with Traccar |
| RBAC | **node-casbin** | Library-level authz across Next.js API + mobile backends; policies can live in Postgres |
| Cross-sell | **Gorse** | “Bought together / replaced together” on sales events; no need for full commerce platform |
| Analytics | **Apache Superset** | Dashboards on Supabase Postgres; Apache-2.0 friendlier than Metabase AGPL for commercial products |
| VIN decode | **NHTSA vPIC** (+ thin wrapper) | Fill gaps in partial VIN/catalog; expect thin coverage for non-US-spec vehicles |
| QR / barcode | **node-qrcode**, **ZXing** / ML Kit, RN vision-camera barcode | POS/warehouse scanning; small libs, not platforms |
| Optional B2B storefront | **Medusa** (only if needed) | MIT + TS + Next.js; use for customer/mechanic portal **if** current POS/storefront is insufficient — do **not** replace back-office ERP |

### Skip / do not adopt as core

| Candidate | Reason |
| --- | --- |
| **ERPNext / Frappe** | Full-stack replacement of Supabase/Next.js; GPLv3; PDF’s “stand this up first” sequence is wrong for GTR |
| **Frappe HR / Payroll** | Hard exclusion: no payroll/tax; also tied to ERPNext |
| **Odoo** | Same replacement conflict; Community payroll gaps irrelevant |
| **Bagisto** | PHP/Laravel — wrong monorepo language; dual-runtime tax |
| **TailPOS** | Abandoned; ERPNext sync only; GTR already has POS |
| **Vendure** | GPLv3 core; Medusa MIT is the better TS option if commerce is needed |
| **Hyperledger Fabric** | PDF itself recommends a simple hash-chain unless multi-party trust is required; ops cost huge |
| **Typesense** | Fine product, but Meilisearch CE is MIT-friendlier for the same job |
| **Metabase** | Prefer Superset unless team loves Metabase UX and keeps it internal-only |
| **Keycloak** | Only if true multi-IdP SSO is required across many products; otherwise keep **Supabase Auth** |
| **Outline** | BSL 1.1 — not truly open source; notice board is a small CRUD + push feature |
| **Wiki.js** | AGPL + overkill for announcements |
| **SourceCodester ID generator** | Demo quality; use QR libs + your own print template |

### Legal / commercial license notes (not legal advice)
- **MIT / Apache-2.0 / BSD** (Medusa, Bagisto, Traccar, Casbin, OSRM, Gorse, Superset, ZXing, node-qrcode, Meilisearch CE): permissive; keep attribution; no obligation to open your proprietary ERP code for typical SaaS/internal use.  
- **GPLv3** (ERPNext, Frappe HR, Typesense server, Vendure core, TailPOS): copyleft on **distributed modifications** of that codebase. Internal self-host often OK; shipping a modified fork to customers needs GPL compliance planning.  
- **AGPLv3** (Metabase OSS, Wiki.js): network-use copyleft — extra caution if modified instances are exposed to external users or embedded in a product.  
- **Meilisearch dual license:** stick to **Community Edition (MIT)** features; Enterprise (BUSL) is not free for production without a commercial deal.  
- **Outline BSL:** production commercial use may be restricted until change-date / Apache conversion — do not treat as MIT.  
- **NHTSA vPIC:** free public API; data coverage is US-market oriented.  
- **ACES/PIES databases:** standards structures are useful; reference DBs are paid — design fitment tables to those field names for future imports.

---

## 4. Decision table (all toolkit projects)

| Project | License | Stack | Fit for GTR | Suggested use | Risk |
| --- | --- | --- | --- | --- | --- |
| Meilisearch CE | MIT (CE) | Rust service + JS clients | **Excellent** | Index catalog/SKU/fitment for instant search | Medium — avoid EE features; ops for another service |
| Traccar | Apache-2.0 | Java + Kotlin clients | **Excellent** | Fleet live GPS / geofence; custom dispatch UI in GTR | Medium — Java ops; map into existing fleet schema |
| OSRM | BSD-2-Clause | C++ | **Strong** | Delivery ETA / route optimization | Medium — map data refresh ops |
| Casbin (node-casbin) | Apache-2.0 | TypeScript | **Strong** | Fine-grained RBAC on APIs (roles across apps) | Low–Medium — policy design work |
| Gorse | Apache-2.0 | Go | **Strong** | Parts cross-sell / “replaced together” | Medium — needs clean event feed |
| Apache Superset | Apache-2.0 | Python/TS | **Strong** | Finance/ops/warehouse dashboards on Postgres | Medium — deploy/ops weight |
| Medusa | MIT | Node/TS | **Conditional** | Headless B2B/mechanic storefront if POS isn’t enough | High if used as ERP replace; Low as satellite |
| NHTSA vPIC | Public | HTTP | **Good** | VIN decode enrichment | Low — US coverage gaps |
| node-qrcode / ZXing / ML Kit | MIT / Apache / proprietary | JS / Android | **Good** | Labels, POS scan, badges | Low |
| react-native-vision-camera (+ barcode) | (project licenses) | RN | **Good** (if RN in apps) | Camera scan for POS/warehouse | Low–Medium — native app maintenance |
| Keycloak | Apache-2.0 | Java | Weak–Conditional | Only if SSO beyond Supabase Auth | High complexity vs benefit |
| Typesense | GPL-3.0 | C++ | Good (alt) | Alternative to Meilisearch | License + duplicate search stack |
| Metabase | AGPLv3 | Clojure/JS | OK internal | Internal BI only | AGPL if embedded/customer-facing |
| Bagisto | MIT | PHP/Laravel | **Poor** | Skip | Stack mismatch |
| Vendure | GPLv3 | NestJS/TS | Weak | Prefer Medusa if commerce needed | GPL + open-core |
| ERPNext | GPLv3 | Python/Frappe | **Conflict** | Do not rebase ERP onto it | Existential rewrite |
| Frappe HR / Payroll | GPLv3 | Python | **Excluded** | Skip (payroll ban) | Policy + stack |
| TailPOS | GPLv3 | RN + ERPNext | **Skip** | Abandoned | Maintenance dead-end |
| Hyperledger Fabric | Apache-2.0 | Go | **Skip now** | Prefer hash-chain on stock ledger if needed | Extreme ops |
| Outline | BSL 1.1 | Node/TS | Skip | Build simple notice board | License + overkill |
| Wiki.js | AGPLv3 | Node/Vue | Skip | Same | AGPL + overkill |
| Odoo | LGPL/Enterprise mix | Python | Conflict | Skip as platform | Rewrite + payroll noise |

---

## 5. Top 5 integration candidates (ranked by leverage)

1. **Meilisearch (Community Edition)** — Highest day-to-day leverage on a spare-parts distributor: typo-tolerant SKU/keyword/fitment search over the existing catalog without rebuilding Postgres or Next.js.  
2. **Traccar** — Turns fleet MVP into real tracking (phone-as-tracker or dedicated devices) via REST/WebSocket; keep GTR’s dispatch/complete flow custom.  
3. **OSRM** — Pairs with logistics/delivery for ETA and route optimization; high customer-facing impact with modest API surface.  
4. **Casbin (`node-casbin`)** — One authorization model across web + Android + iOS backends; fits TypeScript/Supabase edge/API without adopting Keycloak.  
5. **Gorse** — Cross-sell / “commonly replaced together” on top of existing sales + catalog events — PDF’s analytics win without adopting Medusa/Bagisto.

**Honorable mentions:** Apache Superset (BI), NHTSA vPIC (VIN), Medusa (only if a true headless B2B storefront is a product gap).

---

## 6. What not to do next

- Do **not** stand up ERPNext as the “backend of record.”  
- Do **not** adopt Frappe HR/Payroll.  
- Do **not** clone Bagisto/TailPOS/Hyperledger into the monorepo.  
- Prefer **satellite services + small libs** wired to Supabase/Next.js APIs.

---

## 7. Suggested GTR-oriented sequence (replaces PDF § Cursor sequence)

1. Deploy **Meilisearch CE**; sync catalog/SKU documents from Supabase.  
2. Deploy **Traccar**; connect delivery Android + fleet entities; build thin dispatch screens against Traccar API.  
3. Add **OSRM** for ETA/routing in logistics.  
4. Introduce **node-casbin** policies for management vs customer vs delivery roles.  
5. Feed sales/returns into **Gorse**; surface recommendations in POS/customer apps.  
6. Optionally add **Superset** on read-only Postgres for finance/warehouse ops.  
7. Continue catalog/VIN/hotspots as a **first-party build** (PDF is correct: no full OSS shortcut).

---

*Audit only. License interpretations are not legal advice; re-read LICENSE files before commercial distribution of modified copies.*

---

## Implemented scaffolding (2026-08-02)

Pragmatic satellite setup (no ERPNext; no RLS→Casbin rewrite):

| Piece | Path |
|-------|------|
| Compose (Meilisearch + optional Traccar; OSRM commented stub) | `docker-compose.satellites.yml` |
| Env / ops README | `infra/satellites/README.md` |
| Casbin / Gorse Phase-2 notes | `infra/satellites/PHASE2_CASBIN_GORSE.md` |
| Web README + `.env.example` Meili stubs | `apps/web/README.md`, root / web env examples |
| Cursor OSS playbook | `docs/CURSOR_ERP_SAAS_SETUP_GUIDE.md` |

Next product work (separate PR): catalog → Meili sync job + dual-read behind store search API when FTS is insufficient.
