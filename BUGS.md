# Bugs / known gaps

| ID | Symptom | Severity | Notes |
| --- | --- | --- | --- |
| B-MONEY-1 | Payable paths still use `NUMERIC` / JS `number` for some SQL bridges | Medium (architecture) | H4 **Done (cutover habit)** — dual-write + dual-read + shared APIs prefer `amountMinor`; physical column drop + SQL arg require-minor deferred. Loyalty/credit-limit RPCs still major-only. |
| B-MAP-1 | (closed) Customer iOS MapLibre SoR; Android delivery+customer MapLibre SoR **Done** | Low | H5-iOS Done: `bridges/ios/MapsNav` + Address/Track MapLibre primary; MapKit deprecated (`USE_MAPLIBRE=false` / load fail). Android: `AddressPickMap` / `MapLibreAddressPickMap`. OSRM = distance SoR when configured. Mac verify: `xcodebuild` MapsNav tests + GTRCustomer build (Windows: code only). |
| B-EMAIL-1 | CRM promos fall back to Resend if Brevo unset | Low | Intentional during rollout; configure `BREVO_*` |
| B-DOCS-1 | Root README historically lagged delivery app | Low | Updated 2026-08-12 |
| B-PS-1 | (closed) PowerSync mobile SDK wired on management | Low | H7 Done: `LivePowerSyncClient` + `GtrPowerSyncSchema` / connector; Fake when URL unset; no journal upload. Cloud E2E needs `POWERSYNC_URL` secrets. |
| B-OSRM-1 | (closed) OSRM routing satellite online | Low | Graph prepared; `docker compose … --profile routing`; set `OSRM_URL=http://127.0.0.1:5000` |

Do not close adoption epics as Done while only stubs exist (D-52).
