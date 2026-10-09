# H7 / B-PS-1 — PowerSync live SDK

**Status:** Done (Android management SDK wired; cloud E2E needs `POWERSYNC_URL` + auth secrets)

**Depends on:** Phase 14 `powersync/` stubs (Done); management OfflinePos RPC queue already exists

**Laws:** Offline queue = RPC intents only; never upload `journal_entries` / JE lines; Bridge-First unchanged; AI never invents money; no secrets in repo.

## Adopt inventory (do not rewrite OfflinePos)

| Existing | Role vs PowerSync |
| --- | --- |
| `powersync/sync-rules.yaml` + `schema.json` | Sync **read-model** contract (POS / dispatch / cycle-count) |
| `OfflinePosSyncEngine` + SQLCipher store | **Write path** — queue cash sales → `replayOfflinePosSale` (RPC intents) |
| H7 PowerSync Kotlin SDK | **Read sync** of allowlisted tables; optional later replace snapshot pull |

Do **not** replace OfflinePos replay with PowerSync uploads. Do **not** sync-upload ledger tables.

## Freeze DoD

1. Management app has PowerSync client path (live when `POWERSYNC_URL` set; Fake when unset — matches FakeRpc habit).  
2. Client schema mirrors `powersync/schema.json` tables (POS / dispatch / recon allowlist).  
3. Contract forbids journal upload; documents RPC intents for mutations.  
4. Unit tests for Fake + forbidden-table guard.  
5. Living docs + verifier evidence.  
6. Secrets only via `powersync/.env.example` names / BuildConfig — never committed.

## Slice 1 (Done)

- Plan + Kotlin `PowerSyncOfflineContract` (sync-eligible tables, forbidden JE tables, RPC intents).  
- Fake client + JVM unit test.  
- Docs: H7 In progress; `powersync/README` wiring note.

## Slice 2 (Done — this pass)

- `api("com.powersync:core:1.8.1")` on management `core:rpc` (Kotlin 2.2.10; 1.13.x needs 2.3 metadata).  
- `GtrPowerSyncSchema` from `powersync/schema.json` (+ sync-rule stubs).  
- `LivePowerSyncClient.openDatabase(Context)` when `POWERSYNC_URL` set; Fake otherwise.  
- `GtrPowerSyncConnector` — credentials from URL + JWT; `uploadData` fail-closed on JE and discards CRUD (RPC intents only).  
- App `BuildConfig.POWERSYNC_*` from `local.properties`; `MainActivity` opens DB when live.  
- Unit tests skip live open without Context/URL.

## Verify

```bash
cd apps/android-management
./gradlew :core:rpc:testDebugUnitTest --tests co.zw.nissangtr.management.rpc.PowerSyncOfflineContractTest
```

Cloud sync E2E (optional): set `POWERSYNC_URL` (+ public key / project id) in gitignored `local.properties`; sign in so JWT is available for `LivePowerSyncClient.connect`.
