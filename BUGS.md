# Bugs / known gaps

| ID | Symptom | Severity | Notes |
| --- | --- | --- | --- |
| B-MONEY-1 | Payable paths still use `NUMERIC` / JS `number` | High (architecture) | Dual types started; full cutover = E5 |
| B-MAP-1 | Delivery/customer Android still render Google Maps tiles | Medium | OSRM preferred for **routing**; MapLibre UI = E2b |
| B-EMAIL-1 | CRM promos fall back to Resend if Brevo unset | Low | Intentional during rollout; configure `BREVO_*` |
| B-DOCS-1 | Root README historically lagged delivery app | Low | Updated 2026-08-12 |
| B-PS-1 | PowerSync rules present without mobile SDK wiring | Medium | See `powersync/README.md` |
| B-OSRM-1 | OSRM compose service commented until map data present | Medium | `infra/satellites/README.md` |

Do not close adoption epics as Done while only stubs exist (D-52).
