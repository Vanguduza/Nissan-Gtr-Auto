# Architecture & product decisions (claude-mem backup)

Agents: before re-deriving schema or conventions from blueprint PDFs, check this folder
and claude-mem (`search` → `timeline` → `get_observations`).

## How to add a decision

Create `YYYY-MM-DD-short-title.md`:

```markdown
# Title

- Date:
- Lane: @backend_agent | @web_agent | ...
- Status: accepted | superseded

## Decision
One paragraph.

## Why
Constraints / alternatives rejected.

## Consequences
What agents must not re-litigate.
```

## Seed decisions

See `2026-07-23-orchestration-baseline.md`.
