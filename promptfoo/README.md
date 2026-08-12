# Promptfoo outline — AI CRM / report edges (E4)

Gates narrative outputs for:

- `analytics-insights`
- `process-ai-reports`
- `process-crm-promos`
- `stores-insights`
- `demand-forecast`

**Locks**

- AI never writes payable amounts / `amount_minor` / unit prices.
- AI never auto-creates POs (humans quote preferred suppliers).
- No ZIMRA / FDMS content.
- Human + Promptfoo promote before shipping prompt changes (no silent auto-publish).

**C6 evidence (Epic C):** Edge workers `process-ai-reports` and `process-crm-promos`
are grep-checked in `@gtr/payments` (`AI_NEVER_WRITES_MONEY.greppedWorkerPaths` /
`psp.test.ts`) so narrative/promo workers stay free of payable / PO money writes.
See `docs/plans/2026-08-12-epic-c-payments-d57-dod.md`.

**Run**

```bash
npx promptfoo eval -c promptfoo/promptfoo.config.yaml
```

Replace the `echo` provider with a real model when API keys are available in CI secrets (never commit keys).
