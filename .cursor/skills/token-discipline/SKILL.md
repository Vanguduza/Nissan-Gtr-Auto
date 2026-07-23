---
name: token-discipline
description: Token and context discipline for Nissan GTR Auto ERP agents. Use at session start or when planning multi-file work to avoid burning context on blueprints, full-file rewrites, or cross-lane reads.
---

# Token & Context Discipline

## Goals

Maximize development speed and quality while minimizing token burn across long multi-session builds.

## Standing Rules

1. **Lane first** — Read only `rufler.yaml` paths for your agent + shared packages. Never browse another frontend "just in case."
2. **Skills on demand** — Domain skills load when triggers match. Heavy design skills (`ui-ux-pro-max`, brand, slides) are **explicit invoke only** (`/ui-ux-pro-max`).
3. **Diffs over dumps** — Patch existing files; full-file output only for new files.
4. **Progressive memory** — Prefer `docs/decisions/` and claude-mem search (`search` → `timeline` → `get_observations`) over re-reading blueprint PDFs.
5. **Cite, don't paste** — Reference section IDs from setup prompt / skills instead of quoting long specs into the working set.
6. **Batch related work** — Group related migrations / rule edits in one pass.
7. **Plan large features** — Plan Mode → save to `docs/plans/` → implement → `/verifier`.
8. **Ignore noise** — Respect `.cursorignore` (build artifacts, lockfiles, binaries, generated types).

## Session Opening Checklist (cheap)

1. Identify lane (`@backend_agent`, `@web_agent`, …)
2. Search memory / `docs/decisions/` for prior decisions
3. Load at most **one** domain skill that matches the task
4. Read only the files you will edit

## Anti-Patterns (expensive)

- Re-pasting the full setup PDF into chat
- Loading all `.claude/skills/` or design suite skills at once
- Reading every app under `apps/` for a single-lane bugfix
- Regenerating entire files for one-line fixes
- Fetching full claude-mem observations before filtering via `search`
