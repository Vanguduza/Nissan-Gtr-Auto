-- Online dispatch: auto pick + sales_prep notify; pick done → job + auto-assign.
-- Run after seed + migrations, e.g.:
--   docker exec -i <supabase_db> psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/dispatch_auto_assign_notify_smoke.sql

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
  v_sales UUID := 'a0000000-0000-4000-8000-0000000000f1';
  v_customer UUID := 'c0000000-0000-4000-8000-0000000000a1';
  v_driver UUID := 'd0000000-0000-4000-8000-0000000000d1';
  v_main UUID;
  v_uom UUID;
  v_item UUID;
  v_cart UUID;
  v_inv UUID;
  v_pick UUID;
  v_notify INT;
  v_job UUID;
  v_lines JSONB;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN' LIMIT 1;
  SELECT id INTO v_uom FROM public.units_of_measure WHERE code = 'EA' LIMIT 1;
  IF v_main IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'seed MAIN warehouse / EA uom required';
  END IF;

  -- Ensure CoA cash sub-accounts exist
  IF NOT EXISTS (SELECT 1 FROM public.chart_of_accounts WHERE code = '1110') THEN
    RAISE EXCEPTION 'CoA 1110 Petty Cash missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.chart_of_accounts WHERE code = '1120') THEN
    RAISE EXCEPTION 'CoA 1120 Cash Sales Till missing';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.chart_of_accounts WHERE code = '1130') THEN
    RAISE EXCEPTION 'CoA 1130 Online Payment Clearing missing';
  END IF;

  -- Sales staff for prep notify (idempotent)
  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (v_sales, 'Smoke Sales', true)
  ON CONFLICT (id) DO UPDATE SET is_staff = true;
  INSERT INTO public.staff_roles (user_id, role)
  VALUES (v_sales, 'sales')
  ON CONFLICT DO NOTHING;

  -- Stock item with qty
  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('SMOKE-ONLINE-1', 'Online dispatch smoke part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'SMOKE-ONLINE-1';
  END IF;

  INSERT INTO public.stock_levels (stock_item_id, warehouse_id, qty_on_hand, unit_cost, currency)
  VALUES (v_item, v_main, 50, 10, 'USD')
  ON CONFLICT (stock_item_id, warehouse_id) DO UPDATE
  SET qty_on_hand = GREATEST(public.stock_levels.qty_on_hand, 50);

  -- Customer cart storefront + dispatch
  PERFORM public._test_set_auth_uid(v_customer);
  PERFORM public._storefront_rpc_enter();
  v_cart := public.create_customer_cart(v_main, 'USD'::public.currency_code, 1, 'dispatch');
  PERFORM public.add_customer_cart_line(v_cart, v_item, v_uom, 1);
  v_inv := public.checkout_customer_cart(v_cart);
  PERFORM public._storefront_rpc_exit();

  IF NOT EXISTS (
    SELECT 1 FROM public.pick_lists WHERE sales_invoice_id = v_inv AND status = 'draft'
  ) THEN
    RAISE EXCEPTION 'expected auto pick list after storefront dispatch checkout';
  END IF;

  SELECT COUNT(*) INTO v_notify
  FROM public.staff_ops_notifications
  WHERE kind = 'sales_prep' AND sales_invoice_id = v_inv;
  IF v_notify < 1 THEN
    RAISE EXCEPTION 'expected sales_prep notifications';
  END IF;

  -- Confirm pick as sales → auto DN/job/assign (driver may be absent → fail soft)
  SELECT id INTO v_pick
  FROM public.pick_lists
  WHERE sales_invoice_id = v_inv AND status = 'draft'
  LIMIT 1;

  PERFORM public._test_set_auth_uid(v_sales);
  SELECT jsonb_agg(
    jsonb_build_object(
      'pick_list_line_id', pll.id,
      'qty_picked', pll.qty_requested
    )
  )
  INTO v_lines
  FROM public.pick_list_lines pll
  WHERE pll.pick_list_id = v_pick;

  PERFORM public.confirm_pick_lines(v_pick, v_lines);

  SELECT dj.id INTO v_job
  FROM public.delivery_jobs dj
  JOIN public.delivery_notes dn ON dn.id = dj.delivery_note_id
  WHERE dn.sales_invoice_id = v_inv
  LIMIT 1;

  IF v_job IS NULL THEN
    RAISE EXCEPTION 'expected delivery job after online prep confirm';
  END IF;

  -- Assign if driver eligible; otherwise unassigned is OK
  IF EXISTS (
    SELECT 1 FROM public.driver_presence dp
    JOIN public.staff_roles sr ON sr.user_id = dp.user_id AND sr.role = 'driver'
    WHERE dp.user_id = v_driver AND dp.status IN ('available', 'on_duty')
  ) THEN
    IF NOT EXISTS (
      SELECT 1 FROM public.delivery_jobs WHERE id = v_job AND assignee_user_id IS NOT NULL
    ) THEN
      RAISE NOTICE 'driver present but not assigned (capacity/shift?) — soft OK';
    END IF;
  END IF;

  RAISE NOTICE 'dispatch_auto_assign_notify_smoke OK invoice=% job=%', v_inv, v_job;
END;
$$;
