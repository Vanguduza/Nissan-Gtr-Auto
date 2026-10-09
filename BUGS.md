# Bugs / known gaps

| ID | Symptom | Severity | Notes |
| --- | --- | --- | --- |
| B-WH-MS-1 | (closed) Master stock page rendered as skinny left card (OEM search only) | Low | Missing `StaffNav` in shell grid + narrow panel. Fixed 2026-08-15: full-width report + filters + CSV. |
| B-STAFF-1 | (closed) Staff web reload after idle cleared lock without password | Medium | Idle lock lived only in React state; GoTrue refresh kept session. Fixed 2026-08-15: persist lastActiveAt/locked in sessionStorage (`staff-idle-lock-state.ts`). |
| B-CAT-1 | (closed) Customer app missing models / thin catalog vs DB | Medium | Mobile `deriveMaker` lagged web (bare Nissan + multi-make WMIs); also `vehicle_master` limit 500 + browse undersample before shop gate. Fixed 2026-08-15. Note: shop still shows only qty>0 + priced (intentional). `vehicle_master` SELECT remains authenticated-only RLS. |
| B-MONEY-1 | Payable paths still use `NUMERIC` / JS `number` for some SQL bridges | Medium (architecture) | H4 **Done (cutover habit)** + loyalty/credit-limit dual-read/dual-write follow-on (`20260814200000_*`); physical NUMERIC column drop + SQL arg require-minor still deferred. |
| B-MAP-1 | (closed) Customer iOS MapLibre SoR; Android delivery+customer MapLibre SoR **Done** | Low | H5-iOS code Done — **awaiting Mac verify** (`xcodebuild` MapsNav tests + GTRCustomer build). Android: `AddressPickMap` / `MapLibreAddressPickMap`. OSRM = distance SoR when configured. |
| B-EMAIL-1 | CRM promos fall back to Resend if Brevo unset | Low | **Infra ready — awaiting `BREVO_*`**; Resend fallback intentional during rollout |
| B-DOCS-1 | Root README historically lagged delivery app | Low | Updated 2026-08-12 |
| B-PS-1 | (closed) PowerSync mobile SDK wired on management | Low | H7 Done: Fake when URL unset; **infra ready — awaiting `POWERSYNC_URL` keys** for cloud E2E |
| B-OSRM-1 | (closed) OSRM routing satellite online | Low | Graph prepared; `docker compose … --profile routing`; set `OSRM_URL=http://127.0.0.1:5000` |
| B-POD-1 | (closed) Driver POD signature/photo evidence | Low | Signature pad WORKING (Compose stroke→PNG); photo via CameraX; upload on submit; no gallery picker |

Do not close adoption epics as Done while only stubs exist (D-52).
