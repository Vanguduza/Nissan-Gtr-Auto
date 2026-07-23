# Phase 6 — Web storefront

- Status: draft
- Lane(s): `@web_agent`
- Skills needed: `/ui-ux-pro-max` (explicit before visual work); `/parts-catalog-ingestion` consume-only for canvas/search contracts
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 6
- Domain: `https://nissangtrauto.co.zw` ([decision](../decisions/2026-07-23-company-domain.md)); local `http://127.0.0.1:3000`

## Goal

Scaffold Next.js App Router storefront in `apps/web` with brand-first landing, My Garage, B2B, catalog canvas stub, and cart/checkout against Phase 5 APIs.

## Preconditions (current repo)

| Path | Status |
|------|--------|
| `apps/web/` | Placeholder README only — **scaffold Next.js here** |
| `packages/ui/` (`@gtr/ui`) | Exists — tokens (Nissan red / steel / mist; DM Sans + Source Sans 3). Extend, don’t replace with Inter+purple |
| `pnpm-workspace.yaml` | Comment notes `apps/*` joins when `package.json` exists — ensure `apps/web` is included |

## Acceptance criteria

- [ ] `pnpm --filter web dev` (or documented equivalent) runs App Router app
- [ ] Route groups: `(storefront)`, `(my-garage)`, `(b2b-portal)` (+ optional `(admin)` read-only stub)
- [ ] Landing: brand-first **Nissan GTR Auto**, full-bleed hero, no cards in hero, atmospheric bg, expressive fonts via `@gtr/ui`, 2–3 intentional motions
- [ ] Catalog canvas stub (diagram + bbox overlay hooks); 4-way search UI (part / VIN / model / PNC) against stub or `/api/v1/store/search`
- [ ] Auth-facing pages (login/signup/callback) using Supabase anon client; no `service_role` in bundle
- [ ] Cart + checkout call Phase 5 RPCs/APIs; respect price list + credit hold; show explicit USD|ZiG
- [ ] `NEXT_PUBLIC_SITE_URL` wired (prod `https://nissangtrauto.co.zw`); prod host documented
- [ ] No HTML5/browser QR libraries; My Garage vehicle filter scopes search

## Paths in scope

- `apps/web/` — Next.js scaffold, App Router, env, layouts/pages
- `packages/ui/` — token CSS export / shared primitives as needed
- `pnpm-workspace.yaml`, root scripts — include/filter `web`
- `.env.example` / app env docs — `NEXT_PUBLIC_SITE_URL`

## Out of scope

- Live Meilisearch / full FAST ingestion (Phase 7)
- Payment gateways, ContiPay settlement UI beyond currency display
- Receipt PDF generation / send (Phase 13)
- Native QR scan/print (bridges only)
- ZIMRA / fiscal QR / payroll tax
- Management Android POS UI (`@management_app_agent`)
- Full staff admin console (stub route group only if needed)

## Risks / exclusions

- **Bridge-First:** web never scans QR; cart from catalog/search/manual SKU only
- **RLS:** browser uses anon key + user session only
- **Design:** invoke `/ui-ux-pro-max`; do not ship Inter + purple-on-white defaults
- **API stubs OK** where Phase 7 search isn’t ready — UI contracts must match 4-way paths

## Handoff

1. Implement in **`@web_agent`** (invoke `/ui-ux-pro-max` for landing + shell)
2. `/security-reviewer` (auth pages, env, no service_role leak)
3. `/verifier`
4. `/manager` for done gate → Phase 7 (search/index) or 5b/4b as needed
