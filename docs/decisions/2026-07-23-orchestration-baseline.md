# Orchestration baseline

- Date: 2026-07-23
- Lane: repo-wide
- Status: accepted

## Decision

Nissan GTR Auto ERP uses a path-routed polyglot monorepo with Ruflo lanes in `rufler.yaml`, global laws in `.cursorrules`, path rules in `.cursor/rules/`, domain skills in `.cursor/skills/`, and specialist subagents under `.cursor/agents/`.

Hard exclusions: **no ZIMRA / FDMS / fiscalisation**; **no payroll tax** (PAYE/NSSA/statutory forms). Hardware access is Bridge-First under `bridges/` only.

External tooling: claude-mem (persistent memory), ui-ux-pro-max (explicit design skill), n8n-mcp (optional).

## Why

Keeps agent context windows small, prevents ERPNext-parity instincts from reintroducing tax/fiscal modules, and enables parallel platform work without cross-contaminating frontends.

## Consequences

- Agents stay in lane unless `@hardware_mobile_agent` / `@finance_agent` is invoked.
- Heavy UI skills are explicit-invoke only.
- Durable conventions live in rules/skills; episodic decisions live in claude-mem + this folder.
