-- Phase 16 slice 1 — warehouse bins smoke (postgres).
-- Requires MAIN/QUAR warehouses, EA uom, admin seed user.

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
  v_quar UUID;
  v_uom UUID;
  v_item UUID;
  v_bin_a UUID;
  v_bin_b UUID;
  v_bin_quar UUID;
  v_entry UUID;
  v_level_bin UUID;
  v_hint_seq INTEGER;
  v_hint_code VARCHAR(64);
  v_bad BOOLEAN;
  v_ret UUID;
BEGIN
  PERFORM public._test_set_auth_uid(v_admin);

  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_quar FROM public.warehouses WHERE code = 'QUAR';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';

  IF v_main IS NULL OR v_quar IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN/QUAR/EA missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P16-BIN-001', 'Phase16 bin smoke part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P16-BIN-001';
  END IF;

  -- -----------------------------------------------------------------------
  -- 1) Create bins under MAIN (pick path order) + QUAR
  -- -----------------------------------------------------------------------
  v_bin_a := public.create_warehouse_bin(
    v_main, 'A-01-01', 'Aisle A Rack 1 Shelf 1', 10, 'A', '01', '01'
  );
  v_bin_b := public.create_warehouse_bin(
    v_main, 'B-02-03', 'Aisle B Rack 2 Shelf 3', 20, 'B', '02', '03'
  );
  v_bin_quar := public.create_warehouse_bin(
    v_quar, 'Q-01', 'Quarantine bay 1', 10, 'Q', '01', NULL
  );

  IF v_bin_a IS NULL OR v_bin_b IS NULL OR v_bin_quar IS NULL THEN
    RAISE EXCEPTION 'smoke fail: bin create returned null';
  END IF;

  PERFORM public.update_warehouse_bin(v_bin_b, 'Aisle B updated', 5, NULL, NULL, NULL, NULL);

  -- -----------------------------------------------------------------------
  -- 2) Receipt with optional bin_id sets stock_levels.bin_id
  -- -----------------------------------------------------------------------
  v_entry := public.post_stock_receipt(
    v_main,
    'P16 bin receipt',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 15,
        'unit_cost', 8,
        'currency', 'USD',
        'valuation_method', 'FIFO',
        'bin_id', v_bin_a
      )
    )
  );

  IF NOT EXISTS (
    SELECT 1 FROM public.stock_entry_lines
    WHERE stock_entry_id = v_entry AND bin_id = v_bin_a
  ) THEN
    RAISE EXCEPTION 'smoke fail: receipt line missing bin_id';
  END IF;

  SELECT bin_id INTO v_level_bin
  FROM public.stock_levels
  WHERE stock_item_id = v_item AND warehouse_id = v_main;

  IF v_level_bin IS DISTINCT FROM v_bin_a THEN
    RAISE EXCEPTION 'smoke fail: stock_levels.bin_id not set (%)', v_level_bin;
  END IF;

  -- -----------------------------------------------------------------------
  -- 3) Cross-warehouse bin assignment rejected
  -- -----------------------------------------------------------------------
  v_bad := false;
  BEGIN
    PERFORM public.set_stock_level_bin(v_item, v_main, v_bin_quar);
  EXCEPTION
    WHEN OTHERS THEN
      v_bad := true;
  END;
  IF NOT v_bad THEN
    RAISE EXCEPTION 'smoke fail: QUAR bin allowed on MAIN stock level';
  END IF;

  v_bad := false;
  BEGIN
    PERFORM public.post_stock_receipt(
      v_main,
      'bad bin',
      jsonb_build_array(
        jsonb_build_object(
          'stock_item_id', v_item,
          'uom_id', v_uom,
          'qty', 1,
          'unit_cost', 1,
          'currency', 'USD',
          'bin_id', v_bin_quar
        )
      )
    );
  EXCEPTION
    WHEN OTHERS THEN
      v_bad := true;
  END;
  IF NOT v_bad THEN
    RAISE EXCEPTION 'smoke fail: receipt accepted cross-warehouse bin';
  END IF;

  -- -----------------------------------------------------------------------
  -- 4) Pick-path hints ordered by pick_path_seq (bin_b seq=5 before bin_a seq=10)
  -- -----------------------------------------------------------------------
  PERFORM public.set_stock_level_bin(v_item, v_main, v_bin_b);

  SELECT pick_path_seq, bin_code
  INTO v_hint_seq, v_hint_code
  FROM public.get_pick_path_hints(v_main, ARRAY[v_item])
  LIMIT 1;

  IF v_hint_code IS DISTINCT FROM 'B-02-03' OR v_hint_seq IS DISTINCT FROM 5 THEN
    RAISE EXCEPTION 'smoke fail: pick hint wrong (code=% seq=%)', v_hint_code, v_hint_seq;
  END IF;

  -- -----------------------------------------------------------------------
  -- 5) Quarantine return protocol unchanged (pending transfer → QUAR)
  -- -----------------------------------------------------------------------
  v_ret := public.post_return_to_quarantine(
    v_main,
    'P16 bin return',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 1,
        'valuation_method', 'FIFO'
      )
    )
  );

  IF NOT EXISTS (
    SELECT 1 FROM public.stock_entries
    WHERE id = v_ret
      AND entry_type = 'transfer'
      AND to_warehouse_id = v_quar
      AND status = 'pending_approval'
  ) THEN
    RAISE EXCEPTION 'smoke fail: return did not create pending QUAR transfer';
  END IF;

  -- -----------------------------------------------------------------------
  -- 6) Deactivate bin
  -- -----------------------------------------------------------------------
  PERFORM public.deactivate_warehouse_bin(v_bin_a);
  IF NOT EXISTS (
    SELECT 1 FROM public.warehouse_bins WHERE id = v_bin_a AND is_active = false
  ) THEN
    RAISE EXCEPTION 'smoke fail: deactivate did not set is_active=false';
  END IF;

  RAISE NOTICE 'phase16_bins_smoke: PASS';
END;
$$;
