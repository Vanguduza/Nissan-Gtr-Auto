# Bugs / known gaps

| ID | Symptom | Severity | Notes |
| --- | --- | --- | --- |
| B-MONEY-1 | Payable paths still use `NUMERIC` / JS `number` for some SQL bridges | Medium (architecture) | H4 **Done (cutover habit)** + loyalty/credit-limit dual-read/dual-write follow-on (`20260814200000_*`); physical NUMERIC column drop + SQL arg require-minor still deferred. |
| B-MAP-1 | (closed) Customer iOS MapLibre SoR; Android delivery+customer MapLibre SoR **Done** | Low | H5-iOS code Done — **awaiting Mac verify** (`xcodebuild` MapsNav tests + GTRCustomer build). Android: `AddressPickMap` / `MapLibreAddressPickMap`. OSRM = distance SoR when configured. |
| B-EMAIL-1 | CRM promos fall back to Resend if Brevo unset | Low | **Infra ready — awaiting `BREVO_*`**; Resend fallback intentional during rollout |
| B-DOCS-1 | Root README historically lagged delivery app | Low | Updated 2026-08-12 |
| B-PS-1 | (closed) PowerSync mobile SDK wired on management | Low | H7 Done: Fake when URL unset; **infra ready — awaiting `POWERSYNC_URL` keys** for cloud E2E |
| B-OSRM-1 | (closed) OSRM routing satellite online | Low | Graph prepared; `docker compose … --profile routing`; set `OSRM_URL=http://127.0.0.1:5000` |
| B-POD-1 | (closed) Driver POD signature/photo evidence | Low | Signature pad WORKING (Compose stroke→PNG); photo via CameraX; upload on submit; no gallery picker |

Do not close adoption epics as Done while only stubs exist (D-52).
