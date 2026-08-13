# Bugs / known gaps

| ID | Symptom | Severity | Notes |
| --- | --- | --- | --- |
| B-MONEY-1 | Payable paths still use `NUMERIC` / JS `number` | High (architecture) | Dual types started; full cutover = E5 |
| B-MAP-1 | Customer iOS still MapKit (not MapLibre); Android delivery+customer MapLibre SoR **Done** (H5 Android) | Medium | Android Done: `AddressPickMap` / `MapLibreAddressPickMap` `useMapLibre=true` default; Google deprecated. **H5-iOS open** — MapKit remain until MapLibre Native. OSRM = distance SoR when configured. |
| B-EMAIL-1 | CRM promos fall back to Resend if Brevo unset | Low | Intentional during rollout; configure `BREVO_*` |
| B-DOCS-1 | Root README historically lagged delivery app | Low | Updated 2026-08-12 |
| B-PS-1 | PowerSync rules present without mobile SDK wiring | Medium | See `powersync/README.md` |
| B-OSRM-1 | OSRM graph not prepared on this machine / Docker missing | Low (ops) | Compose `routing` profile + `infra/satellites/osrm/prepare.sh` landed (H6). Runtime smoke when Docker + PBF ready. |

Do not close adoption epics as Done while only stubs exist (D-52).
