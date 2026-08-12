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

**Run**

```bash
npx promptfoo eval -c promptfoo/promptfoo.config.yaml
```

Replace the `echo` provider with a real model when API keys are available in CI secrets (never commit keys).
