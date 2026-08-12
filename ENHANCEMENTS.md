# Enhancements

Tracked improvements aligned with Dial-a-Spare adoption (`docs/DIAL_SPARE_ADOPTION_PLAN.md`).

| ID | Idea | Status | Notes |
| --- | --- | --- | --- |
| E1 | amountMinor + Brevo + OSRM routing spine | Done (spine) | Packages + helpers; dual-write migration landed |
| E-Proc | Relationship procurement + fund release + tracker | Done | Verified: live tracker, fund release smoke, RFQ secondary |
| E-WH | Dual WH1/WH2 + master stock | Done | Verified: `list_master_stock` staff-only; GRN OEM + invoice bind |
| E-POS | Dial UX web + tablet POS | In progress | Candidate shell/GtrTheme; redesign QA open (Epic G) |
| E-Sec | DIAL AppSec (Semgrep/Checkov CI) | In progress | Rules/workflows present; Epic F evidence gate open |
| E2a | Temporal `DeliveryDispatchWorkflow` | Done (bridge) | Package + edge cycle; autoAcceptOffers opt-in; full worker §H |
| E2b | MapLibre Native courier map | Done | JobDetailScreen MapLibre SoR; Google deprecated fallback |
| E3 | PspAdapter registry + D-57 checkout FX UX | In progress | Package + cart display candidate; Epic C DoD open |
| E4 | Promptfoo + human promote for CRM/report AI | In progress (outline) | Config present; Epic E DoD open |
| E5 | Ledger/payment `amount_minor` dual-write | Done (PO path) | PO lines + fund releases; broader ledger cutover later |
| E6 | Meili dual-read default for catalog | In progress | `searchCatalog` candidate; Epic D DoD open |
| — | Chatwoot / Metabase / PowerSync live | Deferred | Tier-2 satellites |
| — | Android native preferred-supplier PO screen | Deferred | Web `/procurement/orders/new` is SoR for this landing |
