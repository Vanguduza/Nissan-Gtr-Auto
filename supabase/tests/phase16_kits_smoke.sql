-- Phase 16 slice 2 — kits / BOM sell smoke (postgres).
-- Requires MAIN warehouse, EA uom, RETAIL price list, admin seed user.

CREATE OR REPLACE FUNCTION public._test_set_auth_uid(p_uid UUID)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  PERFORM set_config('request.jwt.claim.sub', p_uid::text, true);
  PERFORM set_config(
    'request.jwt.claims',
    json_build_object('sub', p_uid::text, 'role', 'authenticated')::text,
    true
  );
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);
END;
$$;

DO $$
DECLARE
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_main UUID;
  v_uom UUID;
  v_list UUID;
  v_kit_item UUID;
  v_comp_a UUID;
  v_comp_b UUID;
  v_stocked_kit UUID;
  v_kit_explode UUID;
  v_kit_stocked UUID;
  v_cart UUID;
  v_inv UUID;
  v_qty_kit NUMERIC;
  v_qty_a NUMERIC;
  v_qty_b NUMERIC;
  v_qty_stocked NUMERIC;
  v_qty_a2 NUMERIC;
  v_core_cnt INT;
  v_comp_cnt INT;
  v_cogs NUMERIC;
  v_header_issues BOOLEAN;
BEGIN
  PERFORM public._test_set_auth_uid(v_admin);

  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';

  IF v_main IS NULL OR v_uom IS NULL OR v_list IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN/EA/RETAIL missing';
  END IF;

  -- -----------------------------------------------------------------------
  -- Seed SKUs: explode kit + 2 components; stocked kit (+ reuse comps for BOM)
  -- -----------------------------------------------------------------------
  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P16-KIT-EXP', 'Phase16 explode kit', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_kit_item;
  IF v_kit_item IS NULL THEN
    SELECT id INTO v_kit_item FROM public.stock_items WHERE oem_part_number = 'P16-KIT-EXP';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P16-KIT-CA', 'Phase16 kit component A', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_comp_a;
  IF v_comp_a IS NULL THEN
    SELECT id INTO v_comp_a FROM public.stock_items WHERE oem_part_number = 'P16-KIT-CA';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P16-KIT-CB', 'Phase16 kit component B', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_comp_b;
  IF v_comp_b IS NULL THEN
    SELECT id INTO v_comp_b FROM public.stock_items WHERE oem_part_number = 'P16-KIT-CB';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P16-KIT-STK', 'Phase16 stocked kit', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_stocked_kit;
  IF v_stocked_kit IS NULL THEN
    SELECT id INTO v_stocked_kit FROM public.stock_items WHERE oem_part_number = 'P16-KIT-STK';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_kit_item, 100, 15)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 100, core_charge = 15;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_stocked_kit, 80, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 80, core_charge = 0;

  -- Components need prices only if sold alone; explode lines are $0
  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_comp_a, 30, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 30, core_charge = 0;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_comp_b, 20, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 20, core_charge = 0;

  -- Stock: components for explode; kit SKU for stocked; leave explode kit unstocked
  PERFORM public.post_stock_receipt(
    v_main,
    'P16 kits seed comps',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_comp_a, 'uom_id', v_uom, 'qty', 50,
        'unit_cost', 10, 'currency', 'USD', 'valuation_method', 'FIFO'
      ),
      jsonb_build_object(
        'stock_item_id', v_comp_b, 'uom_id', v_uom, 'qty', 50,
        'unit_cost', 5, 'currency', 'USD', 'valuation_method', 'FIFO'
      ),
      jsonb_build_object(
        'stock_item_id', v_stocked_kit, 'uom_id', v_uom, 'qty', 10,
        'unit_cost', 40, 'currency', 'USD', 'valuation_method', 'FIFO'
      )
    )
  );

  -- -----------------------------------------------------------------------
  -- Kit master data
  -- -----------------------------------------------------------------------
  DELETE FROM public.item_kit_components
  WHERE kit_id IN (SELECT id FROM public.item_kits WHERE stock_item_id IN (v_kit_item, v_stocked_kit));
  DELETE FROM public.item_kits WHERE stock_item_id IN (v_kit_item, v_stocked_kit);

  v_kit_explode := public.create_item_kit(v_kit_item, 'explode');
  PERFORM public.add_kit_component(v_kit_explode, v_comp_a, 2, v_uom);
  PERFORM public.add_kit_component(v_kit_explode, v_comp_b, 1, v_uom);

  v_kit_stocked := public.create_item_kit(v_stocked_kit, 'stocked');
  PERFORM public.add_kit_component(v_kit_stocked, v_comp_a, 1, v_uom);

  -- -----------------------------------------------------------------------
  -- 1) Explode sell: components down, kit SKU untouched, core on header, COGS = comps
  -- -----------------------------------------------------------------------
  SELECT COALESCE(quantity, 0) INTO v_qty_a
  FROM public.stock_levels WHERE stock_item_id = v_comp_a AND warehouse_id = v_main;
  SELECT COALESCE(quantity, 0) INTO v_qty_b
  FROM public.stock_levels WHERE stock_item_id = v_comp_b AND warehouse_id = v_main;
  SELECT COALESCE(quantity, 0) INTO v_qty_kit
  FROM public.stock_levels WHERE stock_item_id = v_kit_item AND warehouse_id = v_main;

  v_cart := public.create_pos_cart(v_main, NULL, 'USD');
  PERFORM public.add_cart_line(v_cart, v_kit_item, v_uom, 1);

  SELECT count(*)::int INTO v_core_cnt
  FROM public.pos_cart_lines
  WHERE cart_id = v_cart AND is_core_charge AND unit_price = 15;
  IF v_core_cnt <> 1 THEN
    RAISE EXCEPTION 'smoke fail: explode kit missing core charge line';
  END IF;

  SELECT count(*)::int INTO v_comp_cnt
  FROM public.pos_cart_lines
  WHERE cart_id = v_cart AND kit_line_kind = 'component';
  IF v_comp_cnt <> 2 THEN
    RAISE EXCEPTION 'smoke fail: expected 2 explode component lines, got %', v_comp_cnt;
  END IF;

  SELECT issues_stock INTO v_header_issues
  FROM public.pos_cart_lines
  WHERE cart_id = v_cart AND kit_line_kind = 'header';
  IF v_header_issues IS DISTINCT FROM false THEN
    RAISE EXCEPTION 'smoke fail: explode header must not issue stock';
  END IF;

  v_inv := public.checkout_pos_cart(v_cart);

  IF NOT EXISTS (
    SELECT 1 FROM public.sales_invoices WHERE id = v_inv AND status = 'posted'
  ) THEN
    RAISE EXCEPTION 'smoke fail: explode invoice not posted';
  END IF;

  -- Kit SKU stock unchanged (still no row or same qty)
  IF EXISTS (
    SELECT 1 FROM public.stock_levels
    WHERE stock_item_id = v_kit_item AND warehouse_id = v_main AND quantity <> COALESCE(v_qty_kit, 0)
  ) OR (
    v_qty_kit IS NULL AND EXISTS (
      SELECT 1 FROM public.stock_levels
      WHERE stock_item_id = v_kit_item AND warehouse_id = v_main
    )
  ) THEN
    -- if no prior level, still must not create negative/consume; allow create only if qty stayed 0
    IF COALESCE((
      SELECT quantity FROM public.stock_levels
      WHERE stock_item_id = v_kit_item AND warehouse_id = v_main
    ), 0) <> COALESCE(v_qty_kit, 0) THEN
      RAISE EXCEPTION 'smoke fail: explode must not deplete kit SKU stock';
    END IF;
  END IF;

  IF COALESCE((
    SELECT quantity FROM public.stock_levels
    WHERE stock_item_id = v_comp_a AND warehouse_id = v_main
  ), 0) <> (v_qty_a - 2) THEN
    RAISE EXCEPTION 'smoke fail: component A not depleted by BOM qty 2';
  END IF;

  IF COALESCE((
    SELECT quantity FROM public.stock_levels
    WHERE stock_item_id = v_comp_b AND warehouse_id = v_main
  ), 0) <> (v_qty_b - 1) THEN
    RAISE EXCEPTION 'smoke fail: component B not depleted by BOM qty 1';
  END IF;

  -- COGS = 2*10 + 1*5 = 25
  SELECT COALESCE(jel.debit, 0) INTO v_cogs
  FROM public.sales_invoices si
  JOIN public.journal_entry_lines jel ON jel.journal_entry_id = si.journal_entry_id
  WHERE si.id = v_inv AND jel.account_code = '5100';
  IF round(v_cogs, 2) <> 25.00 THEN
    RAISE EXCEPTION 'smoke fail: explode COGS expected 25 got %', v_cogs;
  END IF;

  -- Revenue = kit 100 (core 15 separate on 4200)
  IF NOT EXISTS (
    SELECT 1 FROM public.journal_entry_lines jel
    JOIN public.sales_invoices si ON si.journal_entry_id = jel.journal_entry_id
    WHERE si.id = v_inv AND jel.account_code = '4100' AND jel.credit = 100
  ) THEN
    RAISE EXCEPTION 'smoke fail: explode revenue not 100';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.journal_entry_lines jel
    JOIN public.sales_invoices si ON si.journal_entry_id = jel.journal_entry_id
    WHERE si.id = v_inv AND jel.account_code = '4200' AND jel.credit = 15
  ) THEN
    RAISE EXCEPTION 'smoke fail: explode core revenue not 15';
  END IF;

  -- -----------------------------------------------------------------------
  -- 2) Stocked kit: kit SKU down, components unchanged, COGS = kit cost
  -- -----------------------------------------------------------------------
  SELECT quantity INTO v_qty_stocked
  FROM public.stock_levels WHERE stock_item_id = v_stocked_kit AND warehouse_id = v_main;
  SELECT quantity INTO v_qty_a2
  FROM public.stock_levels WHERE stock_item_id = v_comp_a AND warehouse_id = v_main;

  v_cart := public.create_pos_cart(v_main, NULL, 'USD');
  PERFORM public.add_cart_line(v_cart, v_stocked_kit, v_uom, 1);

  SELECT count(*)::int INTO v_comp_cnt
  FROM public.pos_cart_lines
  WHERE cart_id = v_cart AND kit_line_kind = 'component';
  IF v_comp_cnt <> 0 THEN
    RAISE EXCEPTION 'smoke fail: stocked kit must not explode components into cart';
  END IF;

  v_inv := public.checkout_pos_cart(v_cart);

  IF COALESCE((
    SELECT quantity FROM public.stock_levels
    WHERE stock_item_id = v_stocked_kit AND warehouse_id = v_main
  ), 0) <> (v_qty_stocked - 1) THEN
    RAISE EXCEPTION 'smoke fail: stocked kit SKU not depleted';
  END IF;

  IF COALESCE((
    SELECT quantity FROM public.stock_levels
    WHERE stock_item_id = v_comp_a AND warehouse_id = v_main
  ), 0) <> v_qty_a2 THEN
    RAISE EXCEPTION 'smoke fail: stocked kit must not deplete BOM components';
  END IF;

  SELECT COALESCE(jel.debit, 0) INTO v_cogs
  FROM public.sales_invoices si
  JOIN public.journal_entry_lines jel ON jel.journal_entry_id = si.journal_entry_id
  WHERE si.id = v_inv AND jel.account_code = '5100';
  IF round(v_cogs, 2) <> 40.00 THEN
    RAISE EXCEPTION 'smoke fail: stocked kit COGS expected 40 got %', v_cogs;
  END IF;

  RAISE NOTICE 'phase16_kits_smoke: PASS explode_inv + stocked_kit COGS ok';
END;
$$;
