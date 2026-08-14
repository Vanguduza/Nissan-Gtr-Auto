# Catalog diagram scrape — ops runbook

**Product status:** Ready. Storefront PDP + staff catalog consume diagram assets already seeded for **Navara D40** and **X-Trail T31**. Expanding coverage is an **ops scrape/upload**, not a product code gap.

## What ships in-repo

| Asset | Path |
|-------|------|
| Fixture PNGs + local README | `data-pipeline/fixtures/<vehicle>/diagrams/` |
| Seed script (idempotent) | `supabase/seed_catalog_diagrams.mjs` |
| Storage / fitment paths | `diagram_assets` / `part_fitment.diagram_path` |

After `db reset`:

```bash
node supabase/seed_catalog_diagrams.mjs --docker
```

## Full scrape (ops only)

1. Prefer licensed FAST / approved catalog sources per pipeline etiquette (`data-pipeline/` README).
2. Run vehicle crawl → transform → publish diagram bytes to Storage with paths matching fitment rows.
3. Re-run `seed_catalog_diagrams.mjs` only for fixture packs; production upload uses the pipeline publish path.
4. Do **not** block shop/PDP DoD on a full multi-vehicle scrape — seed fixtures prove the product path.

## Not a Windows backlog item

Closing the “full catalog diagram scrape” product residual: **ops runbook / awaiting crawl ops**, not missing UI or RPC work.
