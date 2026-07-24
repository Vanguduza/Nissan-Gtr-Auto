# PowerSync stubs (Phase 14 must-now)

Checked-in **sync rules** and **schema manifest** for management offline surfaces. No live mobile SDK wiring yet (deferred to Phases 11–12 / management Android).

| File | Purpose |
|------|---------|
| `sync-rules.yaml` | Bucket definitions + table allowlists (POS, dispatch/DN, cycle-count recon) |
| `schema.json` | Client schema stub mirroring synced columns (types only; not generated from DB) |
| `.env.example` | Env **names** for PowerSync cloud URL/keys — never commit real values |

## Design constraints

- Sync **respects RLS**: buckets are staff-scoped; clients do not pull open ledger mutations.
- Offline clients queue **RPC intents** (checkout, submit DN, submit reconciliation) — never direct journal entry INSERT/UPDATE/DELETE.
- Money fields always carry explicit `currency` (`USD` \| `ZIG`) and `exchange_rate_applied` where converted.
- Hardware (QR scan / print / GPS) stays Bridge-First under `bridges/` — not in sync rules.

## Wiring later

Management / customer apps will point the PowerSync SDK at these rules after cloud project setup. Until then, treat this directory as the contract for which tables are sync-eligible.

## Secrets

Set `POWERSYNC_URL`, `POWERSYNC_PUBLIC_KEY` (or project-specific names from your PowerSync dashboard) in Edge/CI/mobile secret stores only. See `docs/HARDENING.md`.
