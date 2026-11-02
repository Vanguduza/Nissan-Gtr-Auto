-- Retire the development demo catalogue (Navara D40 YD25, X-Trail T31 MR20) wherever the full
-- Nissan catalogue is loaded.
--
-- Migrations 20260725180000, 20260725204000 and 20260807140000 wrote fixture rows into the shared
-- catalogue tables so EPC screens had something to show before the crawl landed. On a project that
-- holds the full catalogue those rows sit beside (and can overwrite) real data, so every app shows
-- the demo vehicles. This removes exactly the fixture rows, identified by their fixture-only
-- markers (fixture diagram paths, null external ids/source URLs, the three fixture VIN prefixes).
--
-- Guard: only runs when the database has catalogue variants other than the two fixture ones, so a
-- local `supabase db reset` (fixture data only) keeps a working demo EPC. Commerce rows
-- (stock_items / price_list_items) are left alone: they may carry sales history.
-- Read/delete of catalogue rows only; no new tables, no RLS change.

DO $$
DECLARE
  v_real_variants BIGINT;
  v_n BIGINT;
BEGIN
  SELECT count(*) INTO v_real_variants
  FROM public.catalog_variants v
  WHERE v.maker_slug = 'nissan'
    AND NOT (v.slug IN ('t31-mr20', 'd40-yd25') AND v.external_data_id IS NULL AND v.source_url IS NULL);

  IF v_real_variants = 0 THEN
    RAISE NOTICE 'retire_demo_catalog_rows: full catalogue not loaded here; demo rows kept for local development';
    RETURN;
  END IF;

  -- Fixture fitment rows (hotspot boxes on the fixture artwork).
  DELETE FROM public.part_fitment
  WHERE diagram_path LIKE 'navara-d40/%' OR diagram_path LIKE 'xtrail-t31/%';
  GET DIAGNOSTICS v_n = ROW_COUNT;
  RAISE NOTICE 'retire_demo_catalog_rows: % fixture part_fitment rows', v_n;

  -- Fixture diagrams, then the fixture sections and variants left empty by that.
  DELETE FROM public.catalog_diagrams d
  WHERE d.maker_slug = 'nissan'
    AND (d.model_slug, d.variant_slug) IN (('x-trail', 't31-mr20'), ('navara', 'd40-yd25'))
    AND (d.storage_path LIKE 'navara-d40/%' OR d.storage_path LIKE 'xtrail-t31/%');
  GET DIAGNOSTICS v_n = ROW_COUNT;
  RAISE NOTICE 'retire_demo_catalog_rows: % fixture diagrams', v_n;

  DELETE FROM public.catalog_sections s
  WHERE s.maker_slug = 'nissan'
    AND (s.model_slug, s.variant_slug) IN (('x-trail', 't31-mr20'), ('navara', 'd40-yd25'))
    AND s.slug LIKE 'section-%'
    AND NOT EXISTS (
      SELECT 1 FROM public.catalog_diagrams d
      WHERE d.maker_slug = s.maker_slug AND d.model_slug = s.model_slug
        AND d.variant_slug = s.variant_slug AND d.section_slug = s.slug
    );

  DELETE FROM public.catalog_variants v
  WHERE v.maker_slug = 'nissan'
    AND v.slug IN ('t31-mr20', 'd40-yd25')
    AND v.external_data_id IS NULL
    AND v.source_url IS NULL
    AND NOT EXISTS (
      SELECT 1 FROM public.catalog_sections s
      WHERE s.maker_slug = v.maker_slug AND s.model_slug = v.model_slug AND s.variant_slug = v.slug
    );
  GET DIAGNOSTICS v_n = ROW_COUNT;
  RAISE NOTICE 'retire_demo_catalog_rows: % fixture variants', v_n;

  -- A model is only removed when the fixtures were all it had.
  DELETE FROM public.catalog_models m
  WHERE m.maker_slug = 'nissan'
    AND m.slug IN ('x-trail', 'navara')
    AND NOT EXISTS (
      SELECT 1 FROM public.catalog_variants v WHERE v.maker_slug = m.maker_slug AND v.model_slug = m.slug
    );

  -- Fixture vehicle-master rows (exact fixture tuples only).
  DELETE FROM public.vehicle_master vm
  WHERE (vm.vin_prefix, vm.chassis_code, vm.engine_code, vm.production_year, vm.model_variant) IN (
    ('MNTCCND40', 'D40', 'YD25', 2010, 'Nissan Navara D40 · YD25'),
    ('JN1TANT31', 'T31', 'MR20', 2012, 'Nissan X-Trail T31 · MR20')
  );
  GET DIAGNOSTICS v_n = ROW_COUNT;
  RAISE NOTICE 'retire_demo_catalog_rows: % fixture vehicle_master rows', v_n;
END $$;
