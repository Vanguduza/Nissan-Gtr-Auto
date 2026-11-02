-- Dev hierarchy seed for Megazip-style EPC browse (unblocks UI while crawl runs).
-- Aligns with fixture chassis (X-Trail T31, Navara D40) and existing Storage PNG paths
-- from 20260725180000 / 20260725204000 so get_catalog_diagram returns hotspots.
-- RLS: catalog_* SELECT already open to authenticated (20260807120000). No new tables.
-- NO ZIMRA / payroll tax.

INSERT INTO public.catalog_makers (slug, name, sort_order, source)
VALUES ('nissan', 'Nissan', 10, 'dev-seed')
ON CONFLICT (slug) DO UPDATE
SET name = EXCLUDED.name, sort_order = EXCLUDED.sort_order, source = EXCLUDED.source;

INSERT INTO public.catalog_models (
  maker_slug, slug, display_name, body_type, sort_key, year_start, year_end, source_url
)
VALUES
  ('nissan', 'x-trail', 'X-Trail', 'SUV', 'x-trail', 2007, 2013, NULL),
  ('nissan', 'navara', 'Navara', 'Pickup', 'navara', 2005, 2015, NULL)
ON CONFLICT (maker_slug, slug) DO UPDATE
SET
  display_name = EXCLUDED.display_name,
  body_type = EXCLUDED.body_type,
  sort_key = EXCLUDED.sort_key,
  year_start = EXCLUDED.year_start,
  year_end = EXCLUDED.year_end;

INSERT INTO public.catalog_variants (
  maker_slug, model_slug, slug, chassis_code, frame, grade, sales_region,
  year_start, year_end, year_label, engine_code, external_data_id, source_url
)
VALUES
  (
    'nissan', 'x-trail', 't31-mr20', 'T31', 'T31', 'MR20', 'ZA',
    2007, 2013, '2007–2013', 'MR20', NULL, NULL
  ),
  (
    'nissan', 'navara', 'd40-yd25', 'D40', 'D40', 'YD25', 'ZA',
    2005, 2015, '2005–2015', 'YD25', NULL, NULL
  )
ON CONFLICT (maker_slug, model_slug, slug) DO UPDATE
SET
  chassis_code = EXCLUDED.chassis_code,
  frame = EXCLUDED.frame,
  grade = EXCLUDED.grade,
  sales_region = EXCLUDED.sales_region,
  year_start = EXCLUDED.year_start,
  year_end = EXCLUDED.year_end,
  year_label = EXCLUDED.year_label,
  engine_code = EXCLUDED.engine_code;

INSERT INTO public.catalog_sections (
  maker_slug, model_slug, variant_slug, slug, name, thumbnail_url, sort_order, assembly_group_id
)
VALUES
  ('nissan', 'x-trail', 't31-mr20', 'section-filters', 'Filters', NULL, 10, NULL),
  ('nissan', 'x-trail', 't31-mr20', 'section-body', 'Body', NULL, 20, NULL),
  ('nissan', 'x-trail', 't31-mr20', 'section-engine', 'Engine', NULL, 30, NULL),
  ('nissan', 'x-trail', 't31-mr20', 'section-brakes', 'Brakes', NULL, 40, NULL),
  ('nissan', 'x-trail', 't31-mr20', 'section-cooling', 'Cooling', NULL, 50, NULL),
  ('nissan', 'x-trail', 't31-mr20', 'section-suspension', 'Suspension', NULL, 60, NULL),
  ('nissan', 'x-trail', 't31-mr20', 'section-electrical', 'Electrical', NULL, 70, NULL),
  ('nissan', 'navara', 'd40-yd25', 'section-filters', 'Filters', NULL, 10, NULL),
  ('nissan', 'navara', 'd40-yd25', 'section-brakes', 'Brakes', NULL, 20, NULL),
  ('nissan', 'navara', 'd40-yd25', 'section-cooling', 'Cooling', NULL, 30, NULL),
  ('nissan', 'navara', 'd40-yd25', 'section-engine', 'Engine', NULL, 40, NULL),
  ('nissan', 'navara', 'd40-yd25', 'section-body', 'Body', NULL, 50, NULL),
  ('nissan', 'navara', 'd40-yd25', 'section-suspension', 'Suspension', NULL, 60, NULL),
  ('nissan', 'navara', 'd40-yd25', 'section-electrical', 'Electrical', NULL, 70, NULL)
ON CONFLICT (maker_slug, model_slug, variant_slug, slug) DO UPDATE
SET name = EXCLUDED.name, sort_order = EXCLUDED.sort_order;

-- First diagram per section wins in get_catalog_diagram (ORDER BY slug).
-- Prefer PNG paths already uploaded by seed_catalog_diagrams.mjs for filters/body/brakes/cooling.
INSERT INTO public.catalog_diagrams (
  maker_slug, model_slug, variant_slug, section_slug, slug, title,
  image_url, image_width, image_height, storage_path, source_url, diagram_kind, hotspot_count
)
VALUES
  (
    'nissan', 'x-trail', 't31-mr20', 'section-filters', 'oil-filter',
    'Oil filter', NULL, NULL, NULL, 'xtrail-t31/15208-oil-filter.png', NULL, 'exploded', 1
  ),
  (
    'nissan', 'x-trail', 't31-mr20', 'section-body', 'front-bumper',
    'Front bumper', NULL, NULL, NULL, 'xtrail-t31/62022-front-bumper.png', NULL, 'exploded', 1
  ),
  (
    'nissan', 'x-trail', 't31-mr20', 'section-engine', 'engine',
    'Engine', NULL, NULL, NULL, 'xtrail-t31/section-engine.svg', NULL, 'exploded', 3
  ),
  (
    'nissan', 'x-trail', 't31-mr20', 'section-brakes', 'brakes',
    'Brakes', NULL, NULL, NULL, 'xtrail-t31/section-brakes.svg', NULL, 'exploded', NULL
  ),
  (
    'nissan', 'x-trail', 't31-mr20', 'section-cooling', 'cooling',
    'Cooling', NULL, NULL, NULL, 'xtrail-t31/section-cooling.svg', NULL, 'exploded', NULL
  ),
  (
    'nissan', 'x-trail', 't31-mr20', 'section-suspension', 'suspension',
    'Suspension', NULL, NULL, NULL, 'xtrail-t31/section-suspension.svg', NULL, 'exploded', NULL
  ),
  (
    'nissan', 'x-trail', 't31-mr20', 'section-electrical', 'electrical',
    'Electrical', NULL, NULL, NULL, 'xtrail-t31/section-electrical.svg', NULL, 'exploded', NULL
  ),
  (
    'nissan', 'navara', 'd40-yd25', 'section-filters', 'oil-filter',
    'Oil filter', NULL, NULL, NULL, 'navara-d40/15208-oil-filter.png', NULL, 'exploded', 1
  ),
  (
    'nissan', 'navara', 'd40-yd25', 'section-brakes', 'brake-disc',
    'Front brake disc', NULL, NULL, NULL, 'navara-d40/40206-brake-disc.png', NULL, 'exploded', 1
  ),
  (
    'nissan', 'navara', 'd40-yd25', 'section-cooling', 'water-pump',
    'Water pump', NULL, NULL, NULL, 'navara-d40/21410-water-pump.png', NULL, 'exploded', 1
  ),
  (
    'nissan', 'navara', 'd40-yd25', 'section-engine', 'engine',
    'Engine', NULL, NULL, NULL, 'navara-d40/section-engine.svg', NULL, 'exploded', NULL
  ),
  (
    'nissan', 'navara', 'd40-yd25', 'section-body', 'body',
    'Body', NULL, NULL, NULL, 'navara-d40/section-body.svg', NULL, 'exploded', NULL
  ),
  (
    'nissan', 'navara', 'd40-yd25', 'section-suspension', 'suspension',
    'Suspension', NULL, NULL, NULL, 'navara-d40/section-suspension.svg', NULL, 'exploded', NULL
  ),
  (
    'nissan', 'navara', 'd40-yd25', 'section-electrical', 'electrical',
    'Electrical', NULL, NULL, NULL, 'navara-d40/section-electrical.svg', NULL, 'exploded', NULL
  )
ON CONFLICT (maker_slug, model_slug, variant_slug, section_slug, slug) DO UPDATE
SET
  title = EXCLUDED.title,
  storage_path = EXCLUDED.storage_path,
  diagram_kind = EXCLUDED.diagram_kind,
  hotspot_count = EXCLUDED.hotspot_count;

INSERT INTO public.pnc_categories (pnc_code, category_name, subcategory_name)
VALUES
  ('11044', 'Engine', 'Cylinder head'),
  ('13028', 'Engine', 'Timing chain'),
  ('15010', 'Engine', 'Oil pump')
ON CONFLICT (pnc_code) DO UPDATE
SET
  category_name = EXCLUDED.category_name,
  subcategory_name = EXCLUDED.subcategory_name;

-- Fixture OEMs for engine SVG section (hotspots); skip if already present for that path.
INSERT INTO public.part_fitment (
  oem_part_number, pnc_code, chassis_code, engine_code,
  bbox_x, bbox_y, bbox_width, bbox_height, diagram_path
)
SELECT v.oem_part_number, v.pnc_code, v.chassis_code, v.engine_code,
       v.bbox_x, v.bbox_y, v.bbox_width, v.bbox_height, v.diagram_path
FROM (VALUES
  ('11044-EN200', '11044', 'T31', 'MR20', 0.08::numeric, 0.12::numeric, 0.14::numeric, 0.16::numeric,
   'xtrail-t31/section-engine.svg'),
  ('13028-EN200', '13028', 'T31', 'MR20', 0.30, 0.08, 0.13, 0.15,
   'xtrail-t31/section-engine.svg'),
  ('15010-EN200', '15010', 'T31', 'MR20', 0.52, 0.14, 0.14, 0.17,
   'xtrail-t31/section-engine.svg')
) AS v(oem_part_number, pnc_code, chassis_code, engine_code, bbox_x, bbox_y, bbox_width, bbox_height, diagram_path)
WHERE NOT EXISTS (
  SELECT 1 FROM public.part_fitment pf
  WHERE pf.oem_part_number = v.oem_part_number
    AND COALESCE(pf.diagram_path, '') = v.diagram_path
);
