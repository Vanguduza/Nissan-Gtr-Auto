# apps/web — Nissan GTR Auto storefront

- Domain (prod): https://nissangtrauto.co.zw
- Lane: `@web_agent`
- Design: `@gtr/ui` tokens + brand-first hero (no Inter/purple defaults)
- **No browser QR libraries**

```bash
pnpm install
pnpm --filter @gtr/web dev
```

Copy root `.env.example` values into `apps/web/.env.local` (or repo `.env.local` if using Next load from root via dotenv — prefer `apps/web/.env.local`):

```
NEXT_PUBLIC_SITE_URL=https://nissangtrauto.co.zw
NEXT_PUBLIC_SUPABASE_URL=...
NEXT_PUBLIC_SUPABASE_ANON_KEY=...
```

Route groups: `(storefront)`, `(my-garage)`, `(b2b)`, `(auth)`.
