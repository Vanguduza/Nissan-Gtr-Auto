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

| File | Topic |
|------|--------|
| `2026-07-23-orchestration-baseline.md` | Agent pipeline, exclusions |
| `2026-07-23-remote-supabase-project.md` | Hosted Supabase |
| `2026-07-23-manager-sms-key-events.md` | Manager ops SMS catalog |
| `2026-07-23-customer-receipt-delivery.md` | Customer SMS summary + PDF via email/WhatsApp |