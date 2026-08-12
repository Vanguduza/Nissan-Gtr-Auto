# Enhancements

Tracked improvements aligned with Dial-a-Spare adoption (`docs/DIAL_SPARE_ADOPTION_PLAN.md`).

| ID | Idea | Status | Notes |
| --- | --- | --- | --- |
| E1 | amountMinor + Brevo + OSRM routing spine | Done (spine) | Packages + helpers; dual-write migration landed |
| E-Proc | Relationship procurement + fund release + tracker | Done | Web preferred PO + GRN; Android web-first for manual PO |
| E-WH | Dual WH1/WH2 + master stock | Done | View/RPC + GRN invoice attach |
| E-POS | Dial UX web + tablet POS | Done | Web shell + Android GtrTheme PosScreen |
| E-Sec | DIAL AppSec (Semgrep/Checkov CI) | Done | `semgrep-gtr` + Checkov workflows + root `semgrep.yml` |
| E2a | Temporal `DeliveryDispatchWorkflow` | Done (bridge) | Package + edge `delivery-dispatch-cycle`; full worker binary later |
| E2b | MapLibre Native courier map | Done | JobDetailScreen uses MapLibreJobMap |
| E3 | PspAdapter registry + D-57 checkout FX UX | Done | `@gtr/payments` + cart-checkout `buildCheckoutDisplay` |
| E4 | Promptfoo + human promote for CRM/report AI | Done (outline) | `promptfoo/promptfoo.config.yaml`; wire real provider in CI later |
| E5 | Ledger/payment `amount_minor` dual-write | Done (PO path) | PO lines + fund releases; broader ledger cutover later |
| E6 | Meili dual-read default for catalog | Done | `searchCatalog({ preferMeili })`; FTS fallback |
| — | Chatwoot / Metabase / PowerSync live | Deferred | Tier-2 satellites |
| — | Android native preferred-supplier PO screen | Deferred | Web `/procurement/orders/new` is SoR for this landing |
