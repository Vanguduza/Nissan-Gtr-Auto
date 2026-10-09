# X-Trail T31 diagram placeholders

Placeholder line art, 480×320 px, for the local PDP canvas and EPC Browse (no scrape). The seeded `part_fitment.bbox_*` callouts are pixels of this size, so each box sits on its drawn part. Paths match `diagram_assets.json` / `part_fitment.diagram_path`.

After `supabase db reset`:

```bash
node supabase/seed_catalog_diagrams.mjs --docker
```

(Seed discovers this pack alongside Navara.)
