# PowerSync stubs + management client contract (Phase 14 / H7)

Checked-in **sync rules** and **schema manifest** for management offline surfaces. Android management SDK is wired (H7): Fake when `POWERSYNC_URL` unset; live openDatabase when set.

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

## Wiring

Management Android (`apps/android-management`) points the PowerSync SDK at these rules when `POWERSYNC_URL` is set (`LivePowerSyncClient`). Treat this directory as the sync-eligible table contract. Cloud E2E still needs a PowerSync project + secrets (not in repo).

**H7 progress:** Android management PowerSync SDK wired (`com.powersync:core:1.8.1`) — Fake when `POWERSYNC_URL` unset; `LivePowerSyncClient.openDatabase` + `GtrPowerSyncSchema` / `GtrPowerSyncConnector` (RPC-intent upload policy, no journal) when set. Offline POS sale queue remains `OfflinePosSyncEngine`. Secrets: `powersync/.env.example` / app `local.properties` only — never commit. Plan: `docs/plans/2026-08-14-h7-powersync-live-sdk.md`.

## Secrets

Set `POWERSYNC_URL`, `POWERSYNC_PUBLIC_KEY` (or project-specific names from your PowerSync dashboard) in Edge/CI/mobile secret stores only. See `docs/HARDENING.md`.
