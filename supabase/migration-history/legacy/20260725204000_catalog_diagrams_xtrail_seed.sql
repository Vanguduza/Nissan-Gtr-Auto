-- Expand catalog diagram seed beyond Navara: X-Trail T31 / MR20 demo pack.
-- Idempotent upserts. Binary PNGs: node supabase/seed_catalog_diagrams.mjs --docker
-- Fixtures: data-pipeline/fixtures/xtrail_t31_mr20/
-- NO ZIMRA / payroll tax.

DO $$
DECLARE
  v_uom UUID;
  v_list UUID;
  r RECORD;
BEGIN
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA' LIMIT 1;
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL' LIMIT 1;

  IF NOT EXISTS (
    SELECT 1 FROM public.vehicle_master
    WHERE COALESCE(vin_prefix, '') = 'JN1TANT31'
      AND chassis_code = 'T31'
      AND COALESCE(engine_code, '') = 'MR20'
      AND COALESCE(production_year, 0) = 2012
      AND model_variant = 'Nissan X-Trail T31 · MR20'
  ) THEN
    INSERT INTO public.vehicle_master (
      vin_prefix, chassis_code, engine_code, production_year, model_variant
    )
    VALUES ('JN1TANT31', 'T31', 'MR20', 2012, 'Nissan X-Trail T31 · MR20');
  END IF;

  INSERT INTO public.pnc_categories (pnc_code, category_name, subcategory_name)
  VALUES
    ('15208', 'Filters', 'Oil filter'),
    ('16546', 'Filters', 'Air filter'),
    ('92100', 'Body', 'Front bumper')
  ON CONFLICT (pnc_code) DO UPDATE
  SET
    category_name = EXCLUDED.category_name,
    subcategory_name = EXCLUDED.subcategory_name;

  FOR r IN
    SELECT * FROM (VALUES
      ('15208-9N00A', '15208', 'T31', 'MR20', NULL::text,
       110.0::numeric, 70.0::numeric, 50.0::numeric, 60.0::numeric,
       'xtrail-t31/15208-oil-filter.png'),
      ('16546-JA00A', '16546', 'T31', 'MR20', NULL::text,
       95.0, 50.0, 110.0, 75.0,
       'xtrail-t31/16546-air-filter.png'),
      ('62022-JG00A', '92100', 'T31', 'MR20', NULL::text,
       180.0, 120.0, 200.0, 80.0,
       'xtrail-t31/62022-front-bumper.png')
    ) AS t(
      oem_part_number, pnc_code, chassis_code, engine_code, superseded_by,
      bbox_x, bbox_y, bbox_width, bbox_height, diagram_path
    )
  LOOP
    UPDATE public.part_fitment
    SET
      superseded_by = r.superseded_by,
      bbox_x = r.bbox_x,
      bbox_y = r.bbox_y,
      bbox_width = r.bbox_width,
      bbox_height = r.bbox_height,
      diagram_path = r.diagram_path
    WHERE oem_part_number = r.oem_part_number
      AND COALESCE(chassis_code, '') = COALESCE(r.chassis_code, '')
      AND COALESCE(engine_code, '') = COALESCE(r.engine_code, '')
      AND COALESCE(pnc_code, '') = COALESCE(r.pnc_code, '');

    IF NOT FOUND THEN
      INSERT INTO public.part_fitment (
        oem_part_number, pnc_code, chassis_code, engine_code, superseded_by,
        bbox_x, bbox_y, bbox_width, bbox_height, diagram_path
      )
      VALUES (
        r.oem_part_number, r.pnc_code, r.chassis_code, r.engine_code, r.superseded_by,
        r.bbox_x, r.bbox_y, r.bbox_width, r.bbox_height, r.diagram_path
      );
    END IF;
  END LOOP;

  INSERT INTO public.oe_cross_refs (oem_part_number, oe_number, brand)
  VALUES
    ('15208-9N00A', '15208-9N00B', 'Nissan OE'),
    ('16546-JA00A', 'AY120-NS015', 'Aftermarket')
  ON CONFLICT (oem_part_number, oe_number, brand) DO NOTHING;

  IF v_uom IS NOT NULL THEN
    FOR r IN
      SELECT * FROM (VALUES
        ('15208-9N00A', 'Oil filter — X-Trail T31 MR20', 16.50::numeric, 0::numeric),
        ('16546-JA00A', 'Air filter — X-Trail T31', 19.00, 0),
        ('62022-JG00A', 'Front bumper — X-Trail T31', 185.00, 0)
      ) AS t(oem, descr, price, core)
    LOOP
      INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
      VALUES (r.oem, r.descr, v_uom)
      ON CONFLICT (oem_part_number) DO UPDATE
      SET
        description = EXCLUDED.description,
        base_uom_id = COALESCE(public.stock_items.base_uom_id, EXCLUDED.base_uom_id);

      IF v_list IS NOT NULL THEN
        INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
        SELECT v_list, si.id, r.price, r.core
        FROM public.stock_items si
        WHERE si.oem_part_number = r.oem
        ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
        SET unit_price = EXCLUDED.unit_price, core_charge = EXCLUDED.core_charge;
      END IF;
    END LOOP;
  END IF;

  INSERT INTO storage.objects (bucket_id, name, owner, owner_id, metadata)
  VALUES
    (
      'catalog-diagrams', 'xtrail-t31/15208-oil-filter.png', NULL, NULL,
      jsonb_build_object('mimetype', 'image/png', 'size', 137, 'cacheControl', '3600')
    ),
    (
      'catalog-diagrams', 'xtrail-t31/16546-air-filter.png', NULL, NULL,
      jsonb_build_object('mimetype', 'image/png', 'size', 136, 'cacheControl', '3600')
    ),
    (
      'catalog-diagrams', 'xtrail-t31/62022-front-bumper.png', NULL, NULL,
      jsonb_build_object('mimetype', 'image/png', 'size', 137, 'cacheControl', '3600')
    )
  ON CONFLICT (bucket_id, name) DO UPDATE
  SET metadata = EXCLUDED.metadata;
END;
$$;
