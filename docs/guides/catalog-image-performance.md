# Catalog / EPC image loading performance

How diagram images are served today, why they feel slow, and what to do next — without soft blurry thumbs as the only view.

## Diagnosis (hosted `gylrgwqyuiwkyykardwc`)

| Finding | Evidence |
|---------|----------|
| **Public Storage URLs, no next/image historically** | Web used raw `<img src={getPublicUrl(...)}>`; Android Coil `ShopRemoteImage`; iOS `AsyncImage`. |
| **`Cache-Control: no-cache` on objects** | `HEAD`/`GET` on `…/object/public/catalog-diagrams/epc/nissan/*.png` returns `Cache-Control: no-cache`, `CF-Cache-Status: REVALIDATED`. REST uploads without `cache-control` default to no-cache. |
| **Supabase Image Transformation off** | `…/render/image/public/…` → `403 FeatureNotEnabled`. |
| **Client waterfall on EPC diagram** | Auth → `get_catalog_diagram` RPC → (was) child `useEffect` to build public URL → image fetch. |
| **Section thumbs empty / vendor scrubbed** | Megazip `thumbnail_url` was vendor CDN; scrubbed so grids often show placeholders — no Storage thumb variants yet. |
| **Asset size is modest** | Local Megazip dump ~7k PNGs, ~850×425–465, avg ~37 KB (p90 ~54 KB). Perceived slowness is mostly **cache + waterfalls + no format negotiation**, not multi‑MB originals. PartSouq GIFs (when present) can be heavier. |

Single-object TTFB from Johannesburg to Storage was ~200 ms for a 25 KB PNG — acceptable once, painful when every navigation revalidates and grids open many images.

## Target architecture (quality-preserving)

```
┌─────────────────────────────────────────────────────────────┐
│  Browser / app                                              │
│  • next/image (web): AVIF/WebP @ quality ≥90 for canvas     │
│  • Lazy section thumbs; priority only for open diagram      │
│  • Coil / URLCache (mobile) with sized decode               │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP/2, long Cache-Control
┌──────────────────────────▼──────────────────────────────────┐
│  Edge cache (Vercel Image / Cloudflare in front of Storage) │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│  Supabase Storage `catalog-diagrams` (SoR originals)        │
│  • PNG/WebP master (lossless or high-quality lossy)         │
│  • Optional later: `…/thumbs/{w}/` offline variants         │
│  • Optional later: Image Transformation when plan enables it│
└─────────────────────────────────────────────────────────────┘
```

**Do:** progressive enhancement (skeleton → full-res), WebP/AVIF at high quality, CDN cache, prefetch **only** the open diagram.  
**Don't:** ship soft 80 px thumbs as the only view; don't wipe the bucket; don't depend on HTML5 camera/QR for these assets.

## Ranked actions

### 1. High impact / ops — restamp `Cache-Control` on existing objects

Existing ~7k diagrams were uploaded via REST **without** `cache-control` → browsers keep revalidating.

- Shared default: `data_pipeline.storage_diagrams.DIAGRAM_CACHE_CONTROL` (`public, max-age=31536000, immutable`).
  Bare numeric seconds can be served as `public, 31536000` (no real TTL) — always use explicit `max-age=`.
- Re-upsert same paths (no wipe):  
  `python data-pipeline/scripts/restamp_catalog_diagrams_cache.py --force`  
  (uses local `out/megazip/nissan/diagrams/` bytes when present; otherwise downloads then re-uploads).
- Also covered for *new* uploads: `upload_megazip_diagrams_to_storage.py`, Amayama/PartSouq `upload_diagram_supabase`, megazip orchestrator `upload` phase (when service role is set), `supabase/seed_catalog_diagrams.mjs` (API mode).
- Spot-check with **GET** (Smart CDN `HEAD` often still shows `no-cache`):  
  `curl -sI` is unreliable here — use `curl -sD - -o NUL 'https://…/object/public/catalog-diagrams/epc/nissan/<hash>.png?v=1' | findstr /i cache`  
  Expect `Cache-Control: public, max-age=31536000, immutable`.

### 2. High impact / web — next/image + kill URL waterfall (done in-lane)

- `apps/web/next.config.ts` — `images.remotePatterns` for Supabase public Storage + AVIF/WebP.
- `CatalogStorageImage` — quality 90 (diagram) / 82 (thumb); lazy grids; `priority` on open canvas.
- EPC hub resolves `resolveDiagramImageUrl` before first paint of the canvas (no extra `useEffect` hop).

### 3. Medium — enable Supabase Image Transformation (dashboard)

When the plan allows it, use `catalogDiagramTransformUrl()` for **section-grid** widths only (`width=160–320`, `quality≈85`). Keep **full public URL** (or transform at native width, quality 90) for the hotspot canvas.

### 4. Medium — offline thumb + WebP masters (data-pipeline)

Generate during publish (do not replace masters until verified):

| Path | Purpose |
|------|---------|
| `epc/nissan/{hash}.png` | Master (current) |
| `epc/nissan/thumbs/320/{hash}.webp` | Section grid |
| optional `epc/nissan/{hash}.webp` | High-quality lossy twin |

Point `catalog_sections.thumbnail_url` at Storage public URLs for thumbs (not vendor hosts).

### 5. Lower / mobile

- Android: Coil `ImageRequest` with `size` + crossfade; disk cache already helps once Storage sends `max-age`.
- iOS: prefer cached loader over bare `AsyncImage` for EPC canvases.

### 6. Avoid

- Signed URLs for public diagrams (unique tokens defeat CDN).
- Prefetching every section thumb on variant pages.
- Dropping canvas quality below ~85 for “speed.”

## Verify

1. Web: open an EPC diagram → Network shows `/_next/image?url=…supabase…` (or optimised) with AVIF/WebP; diagram is sharp; hotspots still align.
2. Storage: after restamp, `Cache-Control` includes `max-age=31536000`; repeat visit is cache HIT / no full re-download.
3. Transform (if enabled): `…/render/image/public/…?width=200` returns 200, not `FeatureNotEnabled`.
4. Exclusions: no ZIMRA; no catalog wipe; Bridge-First N/A for Storage images.

## Related

- Bucket + MIME: `supabase/migrations/20260724010000_catalog_search_fts.sql`, GIF allow `20260806120000`.
- Browse guide: `docs/guides/vehicle-cascade-and-epc-browse.md`.
- Vercel deploy: `docs/guides/vercel-web-deploy.md`.
