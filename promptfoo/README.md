# Promptfoo outline — AI CRM / report edges (E4 + H3)

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

**Human-promote path (no self-certify)** — unchanged

1. Change prompts / Edge narrative copy under the target functions listed below.
2. Run the offline gate locally — must pass (`npm run gate` in `promptfoo/`).
3. Prefer also `npm run eval` (full promptfoo) on Node 20 — must pass when runnable.
4. Attach eval / gate summary (or CI artifact) to the PR.
5. A human reviewer explicitly approves “promote” of the prompt/copy; do **not** merge on green alone if asserts were weakened.
6. Offline SoR remains `file://providers/safe-narrative.js` (fixed safe narrative — avoids `echo` false positives from rule text and avoids paid `llm-rubric`).

**C6 evidence (Epic C):** Edge workers `process-ai-reports` and `process-crm-promos`
are grep-checked in `@gtr/payments` (`AI_NEVER_WRITES_MONEY.greppedWorkerPaths` /
`psp.test.ts`) so narrative/promo workers stay free of payable / PO money writes.
See `docs/plans/2026-08-12-epic-c-payments-d57-dod.md`.

## Run locally (offline default — Epic E)

```bash
cd promptfoo
npm run gate          # zero-dep asserts vs safe-narrative (works on any Node ≥20)
```

Full promptfoo eval (needs Node **20** LTS — `better-sqlite3` prebuilds; Node 24 on Windows often fails):

```bash
cd promptfoo
npm ci
npm run eval
```

From repo root: `pnpm test:promptfoo` → `npm --prefix promptfoo run gate`.

No API keys required for the default gate. This is the Epic E SoR.

## CI (H3)

Workflow: `.github/workflows/promptfoo.yml`

| Job | When | What |
| --- | --- | --- |
| `promptfoo-offline` | Always (path-filtered PRs/pushes + `workflow_dispatch`) | `npm run gate` then `promptfoo eval` with `file://providers/safe-narrative.js` |
| `promptfoo-real` | After offline; **eval runs only if secrets present** | Real model provider (see below) |

**Optional secrets** (repo Settings → Secrets — never commit):

| Secret | Effect |
| --- | --- |
| `PROMPTFOO_PROVIDER` | Explicit promptfoo provider id (e.g. `openai:gpt-4o-mini`) |
| `OPENAI_API_KEY` | Uses `openai:gpt-4o-mini` if `PROMPTFOO_PROVIDER` unset |
| `GEMINI_API_KEY` or `GOOGLE_API_KEY` | Uses `google:gemini-2.0-flash` if OpenAI unset |

Without those secrets, the real-provider job is a no-op skip (offline still must pass).

## Targets for human promote (not auto-merge)

- `supabase/functions/analytics-insights`
- `supabase/functions/process-ai-reports`
- `supabase/functions/process-crm-promos`
- `supabase/functions/stores-insights`
- `supabase/functions/demand-forecast`
