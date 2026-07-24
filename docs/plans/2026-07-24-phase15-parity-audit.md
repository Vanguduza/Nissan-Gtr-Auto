# Phase 15 — ERPNext parity audit + polish

- Status: draft
- Lane(s): `/manager` (sequence), `/verifier` (gates); polish fixes routed to owning lanes (`@backend_agent`, `@web_agent`, …)
- Skills needed: `/erpnext-feature-parity` (explicit invoke), `/token-discipline`
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) § Phase 15 + Distributor gap register
- Depends: Phase 14 **must-now** ideally ([`…phase14-offline-ci-hardening.md`](./2026-07-24-phase14-offline-ci-hardening.md)); **this plan may scaffold audit docs after the Phase 14 plan exists** (already true)

## Goal

Run a docs-first ERPNext parity audit: checklist path, exclusion grep, gap-register must-haves, and operator runbooks — closing only small in-scope polish gaps; defer Phase 16 extras.

## Acceptance criteria

- [ ] Checklist working copy under `docs/` (e.g. `docs/parity/erpnext-checklist.md`) derived from `.cursor/skills/erpnext-feature-parity/SKILL.md` — mark Done / Gap / Deferred / Excluded per item
- [ ] Exclusion grep runbook + evidence: no ZIMRA/FDMS/fiscal; no PAYE/NSSA/statutory payroll tax; no HTML5/browser QR (align CI from Phase 14 if present)
- [ ] Gap register **must-haves** (master §§ Must-have Phases 3–13) each: Done, or deferred via new `docs/decisions/YYYY-MM-DD-*.md`
- [ ] Phase 16 items (bins, kits, consignment, loyalty, attachments) listed as **explicitly deferred** — not failed AC
- [ ] Operator runbooks index (short): finance period close, stock recon, POS checkout, pick/pack/DN, payment allocation, receipt/SMS drain — paths to existing smoke SQL / Edge README
- [ ] Polish backlog: ≤ small targeted fixes only; each ticket names a single coding lane
- [ ] `/verifier` PASS on exclusions + lane checks after polish landings
- [ ] Master Phase 15 status updated when audit complete

## Paths in scope

- `docs/parity/**` (new checklist + audit notes)
- `docs/runbooks/**` or section in `docs/LOCAL_DEVELOPMENT.md` / `docs/HARDENING.md` (prefer thin new runbook index)
- `docs/decisions/**` — only for deferrals of must-haves
- `docs/plans/2026-07-23-master-erp-development.md` — status line only when Done
- Polish: **only** files named in `/manager`-routed tickets (no speculative rewrites)

## Out of scope

- Implementing Phase 16 distributor extras
- Full mobile feature apps (11–12 scaffold / later slices)
- Replacing Supabase with ERPNext/Frappe; Manufacturing MRP / Work Orders
- New tax/ZIMRA/fiscal receipt work
- Large feature builds disguised as “polish”

## Process (docs-only first)

1. `/manager` opens audit checklist + assigns readers by domain (finance/inventory/sales/procurement/logistics/payments)
2. Fill checklist against migrations + smoke tests — cite paths, do not re-paste blueprints
3. Exclusion grep (local or CI) → attach result note under `docs/parity/`
4. Must-have gaps → Done or written decision
5. Route polish tickets one lane at a time → `/security-reviewer` if schema/auth → `/verifier`
6. `/manager` done gate

## Risks / exclusions

- Do not load full ERPNext docs or blueprint PDFs; skill + gap register + smoke SQL only
- Standing exclusions remain never-schedule
- Audit can start while Phase 14 must-now is in flight; **do not** mark Phase 15 Done until 14 must-now CI/hardening is green (or decision waives)

## Handoff

1. `/manager` sequences audit scaffolding
2. Domain polish → named coding lane
3. `/security-reviewer` if any schema/RLS polish
4. `/verifier`
5. `/manager` done gate → unlock Phase 16 child plans
