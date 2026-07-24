-- Phase 16 slice 3 — consignment stock smoke (postgres).
-- Requires MAIN warehouse, EA uom, admin seed user.
-- Revenue rule: 4100 only on recognize_sale; receive / place / take_ownership never credit 4100.

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
  v_item UUID;
  v_supplier UUID;
  v_customer UUID;
  v_entry UUID;
  v_qty_cns NUMERIC;
  v_qty_main NUMERIC;
  v_qty_main_before NUMERIC;
  v_rev_cnt INT;
  v_je UUID;
  v_codes TEXT;
BEGIN
  PERFORM public._test_set_auth_uid(v_admin);

  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  IF v_main IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN/EA missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P16-CNS-001', 'Phase16 consignment part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P16-CNS-001';
  END IF;

  INSERT INTO public.suppliers (code, name, default_currency, is_active)
  VALUES ('P16-CNS-SUP', 'Phase16 Consignment Supplier', 'USD', true)
  ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, is_active = true
  RETURNING id INTO v_supplier;
  IF v_supplier IS NULL THEN
    SELECT id INTO v_supplier FROM public.suppliers WHERE code = 'P16-CNS-SUP';
  END IF;

  INSERT INTO public.customers (display_name, currency)
  VALUES ('Phase16 Consignment Customer', 'USD')
  RETURNING id INTO v_customer;

  -- Seed owned stock for place_at_customer
  PERFORM public.post_stock_receipt(
    v_main,
    'P16 consignment seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item, 'uom_id', v_uom, 'qty', 100,
        'unit_cost', 8, 'currency', 'USD', 'valuation_method', 'FIFO'
      )
    )
  );

  SELECT COALESCE(quantity, 0) INTO v_qty_main_before
  FROM public.stock_levels WHERE stock_item_id = v_item AND warehouse_id = v_main;

  -- -----------------------------------------------------------------------
  -- 1) Supplier receive: consignment qty up; MAIN stock unchanged; no 4100
  -- -----------------------------------------------------------------------
  v_entry := public.create_consignment_entry_draft(
    'supplier_owned', 'receive', v_main, v_supplier, NULL, 'USD', 1, 'smoke receive'
  );
  PERFORM public.add_consignment_entry_line(v_entry, v_item, v_uom, 20, 7, 0, 'USD');
  PERFORM public.submit_consignment_entry(v_entry);

  SELECT quantity INTO v_qty_cns
  FROM public.consignment_stock_levels
  WHERE kind = 'supplier_owned' AND supplier_id = v_supplier
    AND stock_item_id = v_item AND warehouse_id = v_main;
  IF v_qty_cns IS DISTINCT FROM 20 THEN
    RAISE EXCEPTION 'smoke fail: receive qty expected 20 got %', v_qty_cns;
  END IF;

  SELECT COALESCE(quantity, 0) INTO v_qty_main
  FROM public.stock_levels WHERE stock_item_id = v_item AND warehouse_id = v_main;
  IF v_qty_main IS DISTINCT FROM v_qty_main_before THEN
    RAISE EXCEPTION 'smoke fail: receive must not change owned MAIN stock';
  END IF;

  SELECT journal_entry_id INTO v_je FROM public.consignment_entries WHERE id = v_entry;
  IF v_je IS NOT NULL THEN
    RAISE EXCEPTION 'smoke fail: receive must not post a journal';
  END IF;

  -- -----------------------------------------------------------------------
  -- 2) Take ownership: consignment down, MAIN up, Dr 1300 / Cr 2100, no 4100
  -- -----------------------------------------------------------------------
  v_entry := public.create_consignment_entry_draft(
    'supplier_owned', 'take_ownership', v_main, v_supplier, NULL, 'USD', 1, 'smoke own'
  );
  PERFORM public.add_consignment_entry_line(v_entry, v_item, v_uom, 5, 7, 0, 'USD');
  PERFORM public.submit_consignment_entry(v_entry);

  SELECT quantity INTO v_qty_cns
  FROM public.consignment_stock_levels
  WHERE kind = 'supplier_owned' AND supplier_id = v_supplier
    AND stock_item_id = v_item AND warehouse_id = v_main;
  IF v_qty_cns IS DISTINCT FROM 15 THEN
    RAISE EXCEPTION 'smoke fail: after take_ownership consignment expected 15 got %', v_qty_cns;
  END IF;

  SELECT COALESCE(quantity, 0) INTO v_qty_main
  FROM public.stock_levels WHERE stock_item_id = v_item AND warehouse_id = v_main;
  IF v_qty_main IS DISTINCT FROM v_qty_main_before + 5 THEN
    RAISE EXCEPTION 'smoke fail: take_ownership MAIN expected % got %',
      v_qty_main_before + 5, v_qty_main;
  END IF;

  SELECT journal_entry_id INTO v_je FROM public.consignment_entries WHERE id = v_entry;
  IF v_je IS NULL THEN
    RAISE EXCEPTION 'smoke fail: take_ownership must post journal';
  END IF;

  SELECT string_agg(account_code, ',' ORDER BY account_code) INTO v_codes
  FROM public.journal_entry_lines WHERE journal_entry_id = v_je;
  IF v_codes IS DISTINCT FROM '1300,2100' THEN
    RAISE EXCEPTION 'smoke fail: take_ownership accounts expected 1300,2100 got %', v_codes;
  END IF;

  SELECT COUNT(*) INTO v_rev_cnt
  FROM public.journal_entry_lines
  WHERE journal_entry_id = v_je AND account_code = '4100';
  IF v_rev_cnt <> 0 THEN
    RAISE EXCEPTION 'smoke fail: take_ownership must not credit revenue 4100';
  END IF;

  v_qty_main_before := v_qty_main;

  -- -----------------------------------------------------------------------
  -- 3) Place at customer: MAIN down, customer-held up, Dr 1320 / Cr 1300, no 4100
  -- -----------------------------------------------------------------------
  v_entry := public.create_consignment_entry_draft(
    'customer_held', 'place_at_customer', v_main, NULL, v_customer, 'USD', 1, 'smoke place'
  );
  PERFORM public.add_consignment_entry_line(v_entry, v_item, v_uom, 10, 8, 0, 'USD');
  PERFORM public.submit_consignment_entry(v_entry);

  SELECT COALESCE(quantity, 0) INTO v_qty_main
  FROM public.stock_levels WHERE stock_item_id = v_item AND warehouse_id = v_main;
  IF v_qty_main IS DISTINCT FROM v_qty_main_before - 10 THEN
    RAISE EXCEPTION 'smoke fail: place MAIN expected % got %',
      v_qty_main_before - 10, v_qty_main;
  END IF;

  SELECT quantity INTO v_qty_cns
  FROM public.consignment_stock_levels
  WHERE kind = 'customer_held' AND customer_id = v_customer
    AND stock_item_id = v_item AND warehouse_id = v_main;
  IF v_qty_cns IS DISTINCT FROM 10 THEN
    RAISE EXCEPTION 'smoke fail: place customer-held expected 10 got %', v_qty_cns;
  END IF;

  SELECT journal_entry_id INTO v_je FROM public.consignment_entries WHERE id = v_entry;
  SELECT string_agg(account_code, ',' ORDER BY account_code) INTO v_codes
  FROM public.journal_entry_lines WHERE journal_entry_id = v_je;
  IF v_codes IS DISTINCT FROM '1300,1320' THEN
    RAISE EXCEPTION 'smoke fail: place accounts expected 1300,1320 got %', v_codes;
  END IF;

  SELECT COUNT(*) INTO v_rev_cnt
  FROM public.journal_entry_lines
  WHERE journal_entry_id = v_je AND account_code = '4100';
  IF v_rev_cnt <> 0 THEN
    RAISE EXCEPTION 'smoke fail: place_at_customer must not credit revenue 4100';
  END IF;

  -- -----------------------------------------------------------------------
  -- 4) Recognize sale: ONLY path with 4100; customer-held down; COGS from 1320
  -- -----------------------------------------------------------------------
  v_entry := public.create_consignment_entry_draft(
    'customer_held', 'recognize_sale', v_main, NULL, v_customer, 'USD', 1, 'smoke sale'
  );
  PERFORM public.add_consignment_entry_line(v_entry, v_item, v_uom, 4, 8, 25, 'USD');
  PERFORM public.submit_consignment_entry(v_entry);

  SELECT quantity INTO v_qty_cns
  FROM public.consignment_stock_levels
  WHERE kind = 'customer_held' AND customer_id = v_customer
    AND stock_item_id = v_item AND warehouse_id = v_main;
  IF v_qty_cns IS DISTINCT FROM 6 THEN
    RAISE EXCEPTION 'smoke fail: after sale customer-held expected 6 got %', v_qty_cns;
  END IF;

  SELECT journal_entry_id INTO v_je FROM public.consignment_entries WHERE id = v_entry;
  SELECT COUNT(*) INTO v_rev_cnt
  FROM public.journal_entry_lines
  WHERE journal_entry_id = v_je AND account_code = '4100' AND credit = 100;
  IF v_rev_cnt <> 1 THEN
    RAISE EXCEPTION 'smoke fail: recognize_sale must credit 4100 for 100 (4*25)';
  END IF;

  SELECT COUNT(*) INTO v_rev_cnt
  FROM public.journal_entry_lines
  WHERE journal_entry_id = v_je AND account_code = '5100' AND debit = 32;
  IF v_rev_cnt <> 1 THEN
    RAISE EXCEPTION 'smoke fail: recognize_sale must debit COGS 5100 for 32 (4*8)';
  END IF;

  SELECT COUNT(*) INTO v_rev_cnt
  FROM public.journal_entry_lines
  WHERE journal_entry_id = v_je AND account_code = '1320' AND credit = 32;
  IF v_rev_cnt <> 1 THEN
    RAISE EXCEPTION 'smoke fail: recognize_sale must credit 1320 for COGS';
  END IF;

  -- -----------------------------------------------------------------------
  -- 5) Draft cancel (no stock effect)
  -- -----------------------------------------------------------------------
  v_entry := public.create_consignment_entry_draft(
    'supplier_owned', 'receive', v_main, v_supplier, NULL, 'USD', 1, 'smoke cancel draft'
  );
  PERFORM public.add_consignment_entry_line(v_entry, v_item, v_uom, 1, 7, 0, 'USD');
  PERFORM public.cancel_consignment_entry(v_entry);
  IF (SELECT status FROM public.consignment_entries WHERE id = v_entry) <> 'cancelled' THEN
    RAISE EXCEPTION 'smoke fail: draft cancel status';
  END IF;

  RAISE NOTICE 'phase16_consignment_smoke PASS';
END;
$$;
