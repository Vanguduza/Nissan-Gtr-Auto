# apps/web — Nissan GTR Auto storefront

- Domain (prod): https://nissangtrauto.co.zw
- Lane: `@web_agent`
- Design: AutoDoc-inspired spare-parts chrome + official logo (`public/brand/logo.png`); tokens from `@gtr/ui` (steel / silver / `#C8102E`)
- Decision: `docs/decisions/2026-07-23-storefront-autodoc-logo.md`
- **No browser QR libraries**

```bash
pnpm install
pnpm --filter @gtr/web dev
```

Open http://127.0.0.1:3000

Copy root `.env.example` values into `apps/web/.env.local`:

```
NEXT_PUBLIC_SITE_URL=https://nissangtrauto.co.zw
NEXT_PUBLIC_SUPABASE_URL=...
NEXT_PUBLIC_SUPABASE_ANON_KEY=...
```

Route groups: `(storefront)`, `(my-garage)`, `(b2b)`, `(auth)`.
