---
name: parts-catalog-ingestion
description: Hybrid catalog pipeline — FAST EPC + diagram scraping, bounding boxes, search indexing, visual canvas. Use when the task touches catalog scraping, diagram assets, bounding boxes, Meilisearch, or the visual parts catalog.
---

# Parts Catalog Ingestion

> **Trigger:** Load only when the task touches catalog scraping, diagram asset extraction, bounding-box parsing, search indexing, or the visual parts catalog pipeline.

## Hybrid Data Model

1. **Nissan FAST EPC** — VIN ranges, chassis codes, PNC codes, OEM part numbers (definitive fitment).
2. **Scraped diagram assets** — exploded-view images + X/Y bounding-box coordinates (visual navigation).

## Pipeline Stages

1. **Scrape diagram assets** (Amayama / 7zap / PartSouq) → Playwright + BeautifulSoup
2. **Parse FAST EPC** — see `/nissan-fast-parser`
3. **Merge & validate** against `data-pipeline/schemas/`
4. **Import** diagrams to Supabase Storage; spatial data to `part_fitment`
5. **Index** Meilisearch for 4-way search

## 4-Way Search Paths

1. Exact part number (with supersession)
2. VIN → My Garage
3. Model/Year/Engine cascading dropdowns
4. Category/PNC via visual diagram bounding boxes

## Interactive Canvas

- Base diagram as canvas background
- Overlay bounding boxes (absolute / SVG)
- Hover highlight; click opens add-to-cart modal

## Scraping Rules

See `.cursor/rules/data_pipeline.mdc` for retry/backoff, proxy rotation, robots.txt respect.
