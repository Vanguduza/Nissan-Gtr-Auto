---
name: sdlc-pipeline
description: Optimal agent pipeline for quality and speed without token waste. Use when starting a feature, organizing handoffs, or when the user asks how to run the agent team.
disable-model-invocation: true
---

# SDLC Pipeline (token-efficient team)

## Why this shape

Always-on five-person teams burn tokens. **On-demand specialists** + **one coding lane** give better quality and speed.

```
/manager  →  /planner  →  @lane (code)  →  /security-reviewer  →  /verifier  →  done
```

| Role | Invoke | Codes? | When |
|------|--------|--------|------|
| Manager | `/manager` | No | Epic start, stuck handoffs |
| Planner | `/planner` | No (plans only) | Non-trivial features |
| Coding | `@web_agent` etc. | Yes | After plan |
| Security | `/security-reviewer` | No | Auth/schema/secrets/bridges/APIs |
| Testing | `/verifier` | Can run tests | Before done |
| RLS deep-dive | `/supabase-rls-auditor` | No | Migrations |

## Token rules

1. Never run planner + coder + security + verifier in one mega-prompt.
2. One primary coding lane per task.
3. Security and verifier read **diffs**, not the whole repo.
4. Skip `/planner` only for trivial one-file fixes.
5. Skip `/security-reviewer` only when the change is pure docs/copy with no auth surface.

## User shortcuts

- "Run the pipeline for X" → start `/manager`
- "Just plan X" → `/planner`
- "Implement the plan" → named `@lane`
- "Ship check" → `/security-reviewer` then `/verifier`
