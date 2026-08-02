# Phase-2 satellites — Casbin & Gorse (docs only)

**Status:** scaffold notes only. No production rewrite of Supabase RLS. No compose services yet.

Source audit: [`docs/plans/2026-08-02-open-source-erp-toolkit-audit.md`](../../docs/plans/2026-08-02-open-source-erp-toolkit-audit.md).

---

## Casbin (`node-casbin`)

| | |
|--|--|
| **License** | Apache-2.0 |
| **Role** | Fine-grained authorization model shared across Next.js BFF / edge / mobile backends |
| **Fit** | Complements Supabase RLS — does **not** replace it |

### Out of scope now

- Rewriting RLS policies into Casbin.
- Keycloak / second IdP.

### Low-cost future steps (when needed)

1. `pnpm add casbin` (or `node-casbin`) in the package that owns API authz checks — prefer `packages/shared/` if multiple apps need the same enforcer.
2. Store policies in Postgres (adapter) or start with a checked-in `model.conf` + CSV for staff roles (`admin`, `warehouse`, `dispatcher`, …).
3. Enforce at BFF/edge **in addition to** RLS; never trust client-supplied role claims alone.
4. ADR in `docs/decisions/` before adopting as mandatory.

### npm sketch (do not add until a real gap vs RLS + app roles exists)

```bash
# Example only — not installed in this monorepo yet
pnpm add casbin
# optional Postgres adapter when policy count grows
```

---

## Gorse

| | |
|--|--|
| **License** | Apache-2.0 |
| **Role** | Collaborative filtering / “bought together” / “replaced together” for parts |
| **Fit** | Satellite recommender fed by sales + returns events; UI stays in POS / storefront |

### Out of scope now

- Shipping a Gorse container in default compose.
- Replacing catalog browse with recommendations.

### Future steps

1. Add a `gorse` profile to `docker-compose.satellites.yml` when event volume justifies ops cost.
2. Emit insert/feedback events from completed sales (and quarantine returns) — never from speculative cart alone without product decision.
3. Surface recommendations via a thin API in `packages/shared/` + staff/POS widgets.

---

## Ordering vs search / GPS

Prefer Meilisearch (and optionally Traccar/OSRM) before Casbin/Gorse — higher day-to-day leverage for spare-parts distribution.
