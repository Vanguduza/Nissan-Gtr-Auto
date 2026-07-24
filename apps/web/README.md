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

Route groups: `(storefront)`, `(my-garage)`, `(b2b)`, `(supplier)`, `(auth)`.

### Phase 8b RFQ / supplier quotations

Thin auth-gated portal (anon client + session; RLS enforces staff vs supplier):

| Role | Routes | Actions |
|------|--------|---------|
| Staff | `/procurement`, `/procurement/rfqs`, `/procurement/rfqs/new`, `/procurement/rfqs/[id]` | `create_rfq`, `submit_rfq`, `award_quotation_to_po` |
| Supplier | `/supplier`, `/supplier/rfqs`, `/supplier/rfqs/[id]` | list invited RFQs; `upsert_supplier_quotation`, `submit_supplier_quotation` |

Blanket PO UI is not in this slice.
