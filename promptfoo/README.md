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

**Human-promote path (no self-certify)**

1. Change prompts / Edge narrative copy under the target functions listed below.
2. Run `npx promptfoo eval -c promptfoo/promptfoo.config.yaml` locally — must pass.
3. Attach eval summary (or CI artifact when real provider lands — §H) to the PR.
4. A human reviewer explicitly approves “promote” of the prompt/copy; do **not** merge on green alone if asserts were weakened.
5. Real model provider in CI = deferred (§H); offline SoR is `file://providers/safe-narrative.js` (fixed safe narrative — avoids `echo` false positives from rule text and avoids paid `llm-rubric`).

**C6 evidence (Epic C):** Edge workers `process-ai-reports` and `process-crm-promos`
are grep-checked in `@gtr/payments` (`AI_NEVER_WRITES_MONEY.greppedWorkerPaths` /
`psp.test.ts`) so narrative/promo workers stay free of payable / PO money writes.
See `docs/plans/2026-08-12-epic-c-payments-d57-dod.md`.

**Run**

```bash
npx promptfoo eval -c promptfoo/promptfoo.config.yaml
```

Replace `providers/safe-narrative.js` with a real model provider when API keys are available in CI secrets (never commit keys).
