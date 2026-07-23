# Agent team — quality × speed ÷ tokens

## Recommendation (what we use)

An **on-demand** team, not five agents in every chat:

| Role | Agent | Role in quality/speed |
|------|--------|------------------------|
| Manager | `/manager` | Sequences work; kills scope creep |
| Planning | `/planner` | Shrinks tasks before coding |
| Coding | `@web_agent`, `@backend_agent`, … | Lane-scoped implementation |
| Security | `/security-reviewer` (+ `/supabase-rls-auditor`) | Catches auth/RLS/exclusions early |
| Testing | `/verifier` | Proof via tests + exclusion scans |

## Why not always-on

Loading planner + manager + security + tester + coder every turn reloads overlapping context and burns tokens without improving code. Specialists run **once per phase**.

## Default command

For a non-trivial feature:

```text
/manager
Build <feature>. Use the standard pipeline.
```

Or step manually: `/planner` → implement in one lane → `/security-reviewer` → `/verifier`.

See `/sdlc-pipeline` for the full playbook.
