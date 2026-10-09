-- Run: psql -v ON_ERROR_STOP=1 -f supabase/tests/tablet_offline_replay_smoke.sql (after db reset + seed).
-- Offline outbox replay as the tablet sends it: pull snapshot, replay one cash sale twice with the
-- same client_sale_id (must post once), then a drifted price (must be refused as a conflict).
DO $$
DECLARE
  v_cashier uuid := gen_random_uuid();
  v_main uuid; v_uom uuid; v_list uuid; v_item uuid;
  v_snap jsonb; v_price numeric; v_client uuid := gen_random_uuid();
  v_inv1 text; v_inv2 text; v_count int; v_before numeric; v_after numeric; v_err text;
  v_payload jsonb;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';
  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('OFFLINE-SMOKE-01', 'Offline smoke oil filter', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description RETURNING id INTO v_item;
  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 12.50, 0) ON CONFLICT (price_list_id, stock_item_id) DO UPDATE SET unit_price = 12.50, core_charge = 0;
  PERFORM public.post_stock_receipt(v_main, 'Offline smoke seed', jsonb_build_array(jsonb_build_object(
    'stock_item_id', v_item, 'uom_id', v_uom, 'qty', 10, 'unit_cost', 5, 'currency', 'USD', 'valuation_method', 'FIFO')));
  INSERT INTO auth.users (id, email) VALUES (v_cashier, 'offline-cashier@smoke.test');
  INSERT INTO public.profiles (id) VALUES (v_cashier) ON CONFLICT (id) DO NOTHING;
  INSERT INTO public.staff_roles (user_id, role) VALUES (v_cashier, 'sales');
  PERFORM set_config('request.jwt.claim.sub', v_cashier::text, true);
  PERFORM set_config('request.jwt.claim.role', 'authenticated', true);

  v_snap := public.pull_pos_offline_snapshot(v_main)::jsonb;
  SELECT (i->>'unit_price')::numeric INTO v_price FROM jsonb_array_elements(v_snap->'items') i WHERE i->>'stock_item_id' = v_item::text;
  IF v_price IS DISTINCT FROM 12.50 THEN RAISE EXCEPTION 'snapshot price % (want 12.50)', v_price; END IF;
  SELECT COALESCE(sum(quantity),0) INTO v_before FROM public.stock_levels WHERE stock_item_id = v_item AND warehouse_id = v_main;

  v_payload := jsonb_build_object('warehouse_id', v_main, 'currency', 'USD', 'exchange_rate', 1.0, 'device_id', 'smoke-tablet',
    'lines', jsonb_build_array(jsonb_build_object('stock_item_id', v_item, 'uom_id', v_uom, 'qty', 2, 'expected_unit_price', 12.50)),
    'tenders', jsonb_build_array(jsonb_build_object('tender', 'cash', 'amount', 25.00, 'currency', 'USD')));
  v_inv1 := public.replay_offline_pos_sale(v_client, v_payload)::text;
  v_inv2 := public.replay_offline_pos_sale(v_client, v_payload)::text;
  IF v_inv1 IS DISTINCT FROM v_inv2 THEN RAISE EXCEPTION 'replay not idempotent: % vs %', v_inv1, v_inv2; END IF;
  SELECT COALESCE(sum(quantity),0) INTO v_after FROM public.stock_levels WHERE stock_item_id = v_item AND warehouse_id = v_main;
  IF v_before - v_after <> 2 THEN RAISE EXCEPTION 'stock issued % (want 2)', v_before - v_after; END IF;
  RAISE NOTICE 'replay ok: invoice % posted once, stock % -> %', v_inv1, v_before, v_after;

  BEGIN
    PERFORM public.replay_offline_pos_sale(gen_random_uuid(), jsonb_set(jsonb_set(v_payload, '{lines,0,expected_unit_price}', '10.00'), '{tenders,0,amount}', '20.00'));
    RAISE EXCEPTION 'drifted price was accepted';
  EXCEPTION WHEN OTHERS THEN
    GET STACKED DIAGNOSTICS v_err = MESSAGE_TEXT;
    IF v_err = 'drifted price was accepted' THEN RAISE; END IF;
    RAISE NOTICE 'drift refused: %', v_err;
  END;
END $$;
