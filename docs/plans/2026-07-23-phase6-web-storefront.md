# Phase 6 — Web storefront

- Status: **implemented** (scaffold + AutoDoc-inspired chrome; search index Phase 7)
- Lane(s): `@web_agent`
- Skills needed: `/ui-ux-pro-max` (explicit before visual work); `/parts-catalog-ingestion` consume-only for canvas/search contracts
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 6
- Domain: `https://nissangtrauto.co.zw` ([decision](../decisions/2026-07-23-company-domain.md)); local `http://127.0.0.1:3000`
- Design: [AutoDoc-inspired IA + official logo](../decisions/2026-07-23-storefront-autodoc-logo.md)

## Goal

Scaffold Next.js App Router storefront in `apps/web` with spare-parts shop chrome (logo + search + categories), My Garage, B2B, catalog canvas stub, and cart/checkout against Phase 5 APIs.

## Preconditions (current repo)

| Path | Status |
|------|--------|
| `apps/web/` | **Done** — `@gtr/web` App Router |
| `packages/ui/` (`@gtr/ui`) | Tokens extended (steel / silver / red CTAs) |
| `pnpm-workspace.yaml` | Includes `apps/*` |

## Acceptance criteria

- [x] `pnpm --filter @gtr/web dev` runs App Router app
- [x] Route groups: `(storefront)`, `(my-garage)`, `(b2b)`, `(auth)`
- [x] Shop UX: dense black/steel header, official logo, 4-way header search, category strip, vehicle entry, product-list preview, trust footer (AutoDoc-inspired IA — not minimal art landing)
- [x] Catalog canvas stub; 4-way search UI (part / VIN / model / PNC) against stub path `/search`
- [x] Auth-facing pages (login/signup) using Supabase anon client; no `service_role` in bundle
- [x] Cart page documents Phase 5 RPC contract; explicit USD|ZiG chrome
- [x] `NEXT_PUBLIC_SITE_URL` wired (prod `https://nissangtrauto.co.zw`); prod host documented
- [x] No HTML5/browser QR libraries; My Garage documents vehicle filter scope

## Paths in scope

- `apps/web/` — Next.js scaffold, App Router, env, layouts/pages, `public/brand/logo.png`
- `packages/ui/` — token CSS export
- `pnpm-workspace.yaml`, root scripts — `dev:web`
- `.env.example` / app env docs — `NEXT_PUBLIC_SITE_URL`

## Out of scope

- Live Meilisearch / full FAST ingestion (Phase 7)
- Payment gateways, ContiPay settlement UI beyond currency display
- Receipt PDF generation / send (Phase 13)
- Native QR scan/print (bridges only)
- ZIMRA / fiscal QR / payroll tax
- Management Android POS UI (`@management_app_agent`)
- Full staff admin console

## Risks / exclusions

- **Bridge-First:** web never scans QR; cart from catalog/search/manual SKU only
- **RLS:** browser uses anon key + user session only
- **Design:** AutoDoc layout patterns only — do not copy assets/copy; brand = official logo + `#C8102E` / steel / silver
- **API stubs OK** where Phase 7 search isn’t ready — UI contracts must match 4-way paths

## Handoff

1. `/security-reviewer` (auth pages, env, no service_role leak)
2. `/verifier`
3. `/manager` for done gate → Phase 7 (search/index) or 5b/4b as needed
