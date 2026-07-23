# Company public domain

- Date: 2026-07-23
- Lane: repo-wide / `@web_agent` / `@backend_agent`
- Status: accepted

## Decision

Nissan GTR Auto’s public company domain is **`nissangtrauto.co.zw`**.

| Use | Value |
|-----|--------|
| Canonical site | `https://nissangtrauto.co.zw` |
| `www` | Prefer redirect `www.nissangtrauto.co.zw` → apex (or reverse — pick one at DNS/hosting time; default apex canonical) |
| Auth redirect (prod) | `https://nissangtrauto.co.zw` (+ app paths as needed) |
| Customer receipt PDF links | `https://nissangtrauto.co.zw/...` signed/public download routes (Phase 13) |
| Transactional email From | e.g. `receipts@nissangtrauto.co.zw`, `noreply@nissangtrauto.co.zw` (configure with provider) |
| Env | `NEXT_PUBLIC_SITE_URL=https://nissangtrauto.co.zw` |

Local/dev continues to use `http://127.0.0.1:3000` (see `supabase/config.toml`).

## Why

Receipt SMS links, auth callbacks, storefront SEO, and email branding must share one production hostname.

## Consequences

- Do not invent alternate prod domains in code or docs.
- Phase 6 web app and Phase 13 receipt URLs use this host.
- DNS, TLS, and email SPF/DKIM are ops tasks outside schema migrations.
