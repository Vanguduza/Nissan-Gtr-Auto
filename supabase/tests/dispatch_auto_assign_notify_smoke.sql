-- Online dispatch: auto pick + sales_prep notify; pick done → job (+ assign if eligible).
-- Run after seed + migrations, e.g.:
--   docker exec -i <supabase_db> psql -U postgres -d postgres -v ON_ERROR_STOP=1 \
--     < supabase/tests/dispatch_auto_assign_notify_smoke.sql

CREATE OR REPLACE FUNCTION public._test_set_auth_uid(p_uid UUID)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
  IF p_uid IS NULL THEN
    PERFORM set_config('request.jwt.claim.sub', '', true);
    PERFORM set_config('request.jwt.claims', '{}', true);
    PERFORM set_config('request.jwt.claim.role', 'anon', true);
    RETURN;
  END IF;
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
  v_cust_user UUID := 'c0000000-0000-4000-8000-0000000000a1';
  v_main UUID;
  v_uom UUID;
  v_list UUID;
  v_item UUID;
  v_cust UUID;
  v_cart UUID;
  v_inv UUID;
  v_pick UUID;
  v_notify INT;
  v_job UUID;
  v_lines JSONB;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.chart_of_accounts WHERE code = '1110') THEN
    RAISE EXCEPTION 'smoke fail: CoA 1110 missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.chart_of_accounts WHERE code = '1120') THEN
    RAISE EXCEPTION 'smoke fail: CoA 1120 missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.chart_of_accounts WHERE code = '1130') THEN
    RAISE EXCEPTION 'smoke fail: CoA 1130 missing';
  END IF;

  PERFORM public._test_set_auth_uid(v_admin);

  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';
  IF v_main IS NULL OR v_uom IS NULL OR v_list IS NULL THEN
    RAISE EXCEPTION 'smoke fail: seed MAIN/EA/RETAIL missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P-ONLINE-PREP-001', 'Online prep smoke part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P-ONLINE-PREP-001';
  END IF;

  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 15, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 15, core_charge = 0;

  PERFORM public.post_stock_receipt(
    v_main,
    'online prep smoke seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item, 'uom_id', v_uom, 'qty', 10,
        'unit_cost', 4, 'currency', 'USD', 'valuation_method', 'FIFO'
      )
    )
  );

  SELECT id INTO v_cust FROM public.customers WHERE profile_id = v_cust_user
  ORDER BY created_at ASC LIMIT 1;
  IF v_cust IS NULL THEN
    INSERT INTO public.customers (display_name, currency, profile_id)
    VALUES ('Storefront Customer A', 'USD', v_cust_user)
    RETURNING id INTO v_cust;
  END IF;

  PERFORM public._test_set_auth_uid(v_cust_user);
  v_cart := public.create_customer_cart(
    v_main, 'USD'::public.currency_code, 'dispatch'::public.fulfillment_mode, 1
  );
  PERFORM public.add_customer_cart_line(v_cart, v_item, v_uom, 1);
  v_inv := public.checkout_customer_cart(v_cart);

  SELECT id INTO v_pick
  FROM public.pick_lists
  WHERE sales_invoice_id = v_inv AND status = 'draft'
  ORDER BY created_at DESC
  LIMIT 1;
  IF v_pick IS NULL THEN
    RAISE EXCEPTION 'smoke fail: expected auto pick list after storefront dispatch checkout';
  END IF;

  SELECT COUNT(*) INTO v_notify
  FROM public.staff_ops_notifications
  WHERE kind = 'sales_prep' AND sales_invoice_id = v_inv;
  IF v_notify < 1 THEN
    RAISE EXCEPTION 'smoke fail: expected sales_prep notifications';
  END IF;

  SELECT jsonb_agg(
    jsonb_build_object(
      'pick_list_line_id', pll.id,
      'qty_picked', pll.qty_requested
    )
  )
  INTO v_lines
  FROM public.pick_list_lines pll
  WHERE pll.pick_list_id = v_pick;

  PERFORM public._test_set_auth_uid(v_admin);
  PERFORM public.confirm_pick_lines(v_pick, v_lines);

  SELECT dj.id INTO v_job
  FROM public.delivery_jobs dj
  JOIN public.delivery_notes dn ON dn.id = dj.delivery_note_id
  WHERE dn.sales_invoice_id = v_inv
  ORDER BY dj.created_at DESC
  LIMIT 1;

  IF v_job IS NULL THEN
    RAISE EXCEPTION 'smoke fail: expected delivery job after online prep confirm';
  END IF;

  RAISE NOTICE 'dispatch_auto_assign_notify_smoke OK invoice=% pick=% job=% notifies=%',
    v_inv, v_pick, v_job, v_notify;
END;
$$;
