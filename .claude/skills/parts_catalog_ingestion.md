# Parts Catalog Ingestion

> **Trigger:** Load only when the task touches catalog scraping, diagram asset extraction, bounding-box parsing, search indexing, or the visual parts catalog pipeline.

## Hybrid Data Model

Combines two data sources:
1. **Nissan FAST EPC** — VIN ranges, chassis codes, PNC codes, OEM part numbers (definitive fitment).
2. **Scraped diagram assets** — exploded-view images + X/Y bounding-box coordinates (visual navigation).

## Pipeline Stages

### Stage 1: Scrape Diagram Assets
- Sources: Amayama, 7zap, PartSouq.
- Extract: high-res PNG/JPG diagrams, X/Y/width/height bounding boxes per part region.
- Tools: Python + Playwright (JS-rendered) + BeautifulSoup (static HTML).
- Output: `data-pipeline/output/diagrams/{chassis_code}/{pnc_code}.json`

### Stage 2: Parse FAST EPC Data
- See `nissan_fast_parser.md` for VIN/PNC/OEM mapping.
- Output: `data-pipeline/output/fitment/{chassis_code}.json`

### Stage 3: Merge & Validate
- Join diagram bounding boxes with OEM part numbers via PNC code.
- Validate against JSON schemas in `data-pipeline/schemas/`.
- Deduplicate superseded parts (keep latest in chain).

### Stage 4: Import to Supabase
- Diagram images → Supabase Storage (`catalog-diagrams` bucket).
- Spatial data → `part_fitment` table (bounding box columns).
- Hierarchy → `vehicle_master`, `pnc_categories`.

### Stage 5: Index for Search
- Push to Meilisearch: VIN ranges, part numbers, model variants, PNC categories.
- Enable 4-way search endpoint: `/api/v1/store/search`.

## 4-Way Search Paths

1. **Exact part number** — with supersession/replacement lookup
2. **VIN** — decode → auto-populate "My Garage"
3. **Model/Year/Engine** — cascading dropdowns
4. **Category/PNC** — via visual diagram bounding boxes

"My Garage" entries silently filter all four paths to fitment-compatible parts.

## Interactive Canvas (Frontend)

- Render base diagram image as canvas background.
- Overlay bounding boxes via absolute positioning or SVG.
- Highlight region on hover.
- Click → "add to cart" modal with part, price, stock status.

## Scraping Rules

See `.cursor/rules/data_pipeline.mdc` for retry/backoff, proxy rotation, robots.txt respect.
