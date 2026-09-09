-- Restore storefront catalog rows wiped / never loaded on the hosted project.
-- Idempotent. Demo Navara D40, X-Trail T31, GT-R R35 + stocked parts so the
-- customer app Select Vehicle cascade and shop browse have live data.
-- NO ZIMRA / payroll tax. No new tables (RLS already on these).

DO $$
DECLARE
  v_uom UUID;
  v_list UUID;
  v_wh UUID;
  r RECORD;
BEGIN
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA' LIMIT 1;
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL' LIMIT 1;
  SELECT id INTO v_wh FROM public.warehouses WHERE code = 'MAIN' LIMIT 1;

  -- Vehicles (natural key via unique expression index)
  INSERT INTO public.vehicle_master (
    vin_prefix, chassis_code, engine_code, production_year, model_variant
  )
  SELECT v.vin_prefix, v.chassis_code, v.engine_code, v.production_year, v.model_variant
  FROM (VALUES
    ('MNTCCND40', 'D40', 'YD25', 2010, 'Nissan Navara D40 · YD25'),
    ('JN1TANT31', 'T31', 'MR20', 2012, 'Nissan X-Trail T31 · MR20'),
    ('JN1AR5EF', 'R35', 'VR38DETT', 2012, 'Nissan GT-R R35 · VR38DETT'),
    ('JN1N16', 'N16', 'QG18DE', 2002, 'Nissan Almera N16')
  ) AS v(vin_prefix, chassis_code, engine_code, production_year, model_variant)
  WHERE NOT EXISTS (
    SELECT 1 FROM public.vehicle_master vm
    WHERE COALESCE(vm.vin_prefix, '') = COALESCE(v.vin_prefix, '')
      AND vm.chassis_code = v.chassis_code
      AND COALESCE(vm.engine_code, '') = COALESCE(v.engine_code, '')
      AND COALESCE(vm.production_year, 0) = COALESCE(v.production_year, 0)
      AND vm.model_variant = v.model_variant
  );

  INSERT INTO public.pnc_categories (pnc_code, category_name, subcategory_name)
  VALUES
    ('15208', 'Filters', 'Oil filter'),
    ('16546', 'Filters', 'Air filter'),
    ('40206', 'Brakes', 'Front brake disc'),
    ('21410', 'Cooling', 'Water pump'),
    ('23300', 'Electrical', 'Starter motor')
  ON CONFLICT (pnc_code) DO UPDATE
  SET
    category_name = EXCLUDED.category_name,
    subcategory_name = EXCLUDED.subcategory_name;

  FOR r IN
    SELECT * FROM (VALUES
      ('15208-65F0C', '15208', 'D40', 'YD25'),
      ('16546-00Q0A', '16546', 'D40', 'YD25'),
      ('40206-EA00A', '40206', 'D40', 'YD25'),
      ('21410-JF00A', '21410', 'D40', 'YD25'),
      ('15208-9N00A', '15208', 'T31', 'MR20'),
      ('16546-JA00A', '16546', 'T31', 'MR20'),
      ('15208-65F0C', '15208', 'R35', 'VR38DETT'),
      ('23300-AL510', '23300', 'R35', 'VR38DETT')
    ) AS t(oem_part_number, pnc_code, chassis_code, engine_code)
  LOOP
    IF NOT EXISTS (
      SELECT 1 FROM public.part_fitment pf
      WHERE pf.oem_part_number = r.oem_part_number
        AND COALESCE(pf.chassis_code, '') = COALESCE(r.chassis_code, '')
        AND COALESCE(pf.engine_code, '') = COALESCE(r.engine_code, '')
        AND COALESCE(pf.pnc_code, '') = COALESCE(r.pnc_code, '')
    ) THEN
      INSERT INTO public.part_fitment (
        oem_part_number, pnc_code, chassis_code, engine_code
      )
      VALUES (r.oem_part_number, r.pnc_code, r.chassis_code, r.engine_code);
    END IF;
  END LOOP;

  IF v_uom IS NOT NULL THEN
    FOR r IN
      SELECT * FROM (VALUES
        ('15208-65F0C', 'Oil filter — Navara D40 / GT-R', 18.50::numeric, 0::numeric, 12::numeric),
        ('16546-00Q0A', 'Air filter — Navara D40', 22.00, 0, 8),
        ('40206-EA00A', 'Front brake disc — Navara D40', 65.00, 25.00, 4),
        ('21410-JF00A', 'Water pump — Navara D40 YD25', 42.00, 15.00, 3),
        ('15208-9N00A', 'Oil filter — X-Trail T31 MR20', 16.50, 0, 10),
        ('16546-JA00A', 'Air filter — X-Trail T31', 19.00, 0, 6),
        ('23300-AL510', 'Starter motor — GT-R R35', 185.00, 0, 2)
      ) AS t(oem, descr, price, core, qty)
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

      IF v_wh IS NOT NULL THEN
        INSERT INTO public.stock_levels (
          stock_item_id, warehouse_id, quantity, currency
        )
        SELECT si.id, v_wh, r.qty, 'USD'
        FROM public.stock_items si
        WHERE si.oem_part_number = r.oem
        ON CONFLICT (stock_item_id, warehouse_id) DO UPDATE
        SET quantity = EXCLUDED.quantity;
      END IF;
    END LOOP;
  END IF;
END;
$$;
