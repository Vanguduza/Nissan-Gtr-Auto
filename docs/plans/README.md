# Plans index

| Plan | Role |
|------|------|
| [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) | **Source of truth** for whole-ERP phasing (includes distributor gap register → Phases 3–16) |
| [`2026-07-23-phase1-monorepo-supabase.md`](./2026-07-23-phase1-monorepo-supabase.md) | Phase 1 child (done) |
| [`2026-07-23-phase2-auth-roles.md`](./2026-07-23-phase2-auth-roles.md) | Phase 2 child (done) |
| [`2026-07-23-phase3-finance-core.md`](./2026-07-23-phase3-finance-core.md) | Phase 3 child (done) |
| [`2026-07-23-phase4-inventory-ops.md`](./2026-07-23-phase4-inventory-ops.md) | Phase 4 child (done) |
| [`2026-07-23-phase4-inventory-ops.md`](./2026-07-23-phase4-inventory-ops.md) | Phase 4 child (inventory ops + QR + UOM) |

## Process

1. Follow the **master** plan phase order.
2. Before coding a phase, `/planner` adds/refines a **child** plan.
3. `/manager` sequences lane → `/security-reviewer` → `/verifier`.
4. Record durable choices in `docs/decisions/`.
