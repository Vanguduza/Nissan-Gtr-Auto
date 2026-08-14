-- Smoke: get_delivery_job_settlement authz + dual-read minors.
-- Creates minimal dispatch fixtures (invoice → pick → DN → assigned job) so the
-- assert does not depend on seed having an assignee.
-- Soft-skips only when the RPC or MAIN/EA baselines are missing.
-- Run: docker exec -i <db> psql -U postgres -d postgres -v ON_ERROR_STOP=1 < this file

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

BEGIN;

DO $$
DECLARE
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_driver UUID := 'd0000000-0000-4000-8000-0000000000d1';
  v_main UUID;
  v_uom UUID;
  v_list UUID;
  v_item UUID;
  v_cart UUID;
  v_inv UUID;
  v_inv_line UUID;
  v_pick UUID;
  v_dn UUID;
  v_job UUID;
  v_total NUMERIC;
  v_paid NUMERIC;
  v_exp_due NUMERIC;
  v_row RECORD;
  v_count INT;
BEGIN
  IF to_regprocedure('public.get_delivery_job_settlement(uuid)') IS NULL
     OR to_regprocedure('public._major_to_minor(numeric)') IS NULL THEN
    RAISE NOTICE 'SKIP get_delivery_job_settlement_smoke: RPC not applied';
    RETURN;
  END IF;

  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
  SELECT id INTO v_list FROM public.price_lists WHERE code = 'RETAIL';

  IF v_main IS NULL OR v_uom IS NULL OR v_list IS NULL THEN
    RAISE NOTICE 'SKIP get_delivery_job_settlement_smoke: MAIN/EA/RETAIL missing';
    RETURN;
  END IF;

  PERFORM public._test_set_auth_uid(v_admin);

  -- Driver user + staff role (RLS / assign_delivery_job require driver role)
  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password,
    email_confirmed_at, raw_app_meta_data, raw_user_meta_data,
    created_at, updated_at, confirmation_token, recovery_token,
    email_change_token_new, email_change
  )
  VALUES (
    '00000000-0000-0000-0000-000000000000', v_driver, 'authenticated', 'authenticated',
    'driver-settle@gtr.local', crypt('local-dev-driver', gen_salt('bf')),
    now(), '{"provider":"email","providers":["email"]}'::jsonb, '{}'::jsonb,
    now(), now(), '', '', '', ''
  )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (v_driver, 'Settlement Smoke Driver', false)
  ON CONFLICT (id) DO UPDATE SET full_name = EXCLUDED.full_name;

  INSERT INTO public.staff_roles (user_id, role)
  VALUES (v_driver, 'driver')
  ON CONFLICT DO NOTHING;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('SETTLE-SMOKE-001', 'Delivery settlement smoke part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'SETTLE-SMOKE-001';
  END IF;

  -- unit_price 40 → qty 2 → invoice total 80.00
  INSERT INTO public.price_list_items (price_list_id, stock_item_id, unit_price, core_charge)
  VALUES (v_list, v_item, 40, 0)
  ON CONFLICT (price_list_id, stock_item_id) DO UPDATE
  SET unit_price = 40, core_charge = 0;

  PERFORM public.post_stock_receipt(
    v_main,
    'SETTLE seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 10,
        'unit_cost', 12,
        'currency', 'USD',
        'valuation_method', 'FIFO'
      )
    )
  );

  v_cart := public.create_pos_cart(v_main, NULL, 'USD', 'dispatch');
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 2);
  v_inv := public.checkout_pos_cart(v_cart);

  -- Partial COD paid so amount_due is non-trivial (80 − 15 = 65)
  UPDATE public.sales_invoices
  SET amount_paid = 15.00
  WHERE id = v_inv;

  SELECT total, amount_paid
  INTO v_total, v_paid
  FROM public.sales_invoices
  WHERE id = v_inv;

  IF v_total IS DISTINCT FROM 80.00 OR v_paid IS DISTINCT FROM 15.00 THEN
    RAISE EXCEPTION 'fixture invoice money unexpected total=% paid=%', v_total, v_paid;
  END IF;

  SELECT id INTO v_inv_line
  FROM public.sales_invoice_lines
  WHERE invoice_id = v_inv AND NOT is_core_charge
  LIMIT 1;

  v_pick := public.create_pick_list(
    v_inv,
    jsonb_build_array(
      jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 2)
    )
  );
  PERFORM public.confirm_pick_lines(
    v_pick,
    jsonb_build_array(
      jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty_picked', 2)
    )
  );
  v_dn := public.create_delivery_note(
    v_inv,
    jsonb_build_array(
      jsonb_build_object('sales_invoice_line_id', v_inv_line, 'qty', 2)
    ),
    v_pick
  );
  PERFORM public.submit_delivery_note(v_dn);

  v_job := public.create_delivery_job(
    v_dn, NULL, now() + interval '2 hours', 'settlement smoke'
  );
  -- force=true bypasses presence eligibility
  PERFORM public.assign_delivery_job(v_job, v_driver, true);

  v_exp_due := GREATEST(COALESCE(v_total, 0) - COALESCE(v_paid, 0), 0);

  -- As assignee driver: dual-read settlement fields
  PERFORM public._test_set_auth_uid(v_driver);

  SELECT COUNT(*) INTO v_count
  FROM public.get_delivery_job_settlement(v_job);
  IF v_count <> 1 THEN
    RAISE EXCEPTION 'assignee should see settlement row (got %)', v_count;
  END IF;

  SELECT * INTO v_row
  FROM public.get_delivery_job_settlement(v_job);

  IF v_row.delivery_job_id IS DISTINCT FROM v_job THEN
    RAISE EXCEPTION 'delivery_job_id mismatch';
  END IF;
  IF v_row.currency IS DISTINCT FROM 'USD'::public.currency_code THEN
    RAISE EXCEPTION 'currency mismatch got %', v_row.currency;
  END IF;
  IF v_row.invoice_total IS DISTINCT FROM v_total THEN
    RAISE EXCEPTION 'invoice_total mismatch got % want %', v_row.invoice_total, v_total;
  END IF;
  IF v_row.amount_paid IS DISTINCT FROM v_paid THEN
    RAISE EXCEPTION 'amount_paid mismatch got % want %', v_row.amount_paid, v_paid;
  END IF;
  IF v_row.amount_due IS DISTINCT FROM v_exp_due THEN
    RAISE EXCEPTION 'amount_due mismatch got % want %', v_row.amount_due, v_exp_due;
  END IF;
  IF v_row.invoice_total_minor IS DISTINCT FROM public._major_to_minor(v_total) THEN
    RAISE EXCEPTION 'invoice_total_minor mismatch';
  END IF;
  IF v_row.amount_paid_minor IS DISTINCT FROM public._major_to_minor(v_paid) THEN
    RAISE EXCEPTION 'amount_paid_minor mismatch';
  END IF;
  IF v_row.amount_due_minor IS DISTINCT FROM public._major_to_minor(v_exp_due) THEN
    RAISE EXCEPTION 'amount_due_minor mismatch';
  END IF;
  -- Explicit COD dual-read: 80.00 / 15.00 / 65.00 → 8000 / 1500 / 6500
  IF v_row.invoice_total_minor IS DISTINCT FROM 8000
     OR v_row.amount_paid_minor IS DISTINCT FROM 1500
     OR v_row.amount_due_minor IS DISTINCT FROM 6500 THEN
    RAISE EXCEPTION
      'expected minors 8000/1500/6500 got %/%/%',
      v_row.invoice_total_minor, v_row.amount_paid_minor, v_row.amount_due_minor;
  END IF;

  -- Non-assignee random uid denied
  PERFORM public._test_set_auth_uid('00000000-0000-4000-8000-000000009999');
  BEGIN
    PERFORM * FROM public.get_delivery_job_settlement(v_job);
    RAISE EXCEPTION 'non-assignee must be denied';
  EXCEPTION
    WHEN others THEN
      IF SQLERRM NOT LIKE '%not authorized%' THEN
        RAISE;
      END IF;
  END;

  RAISE NOTICE 'PASS get_delivery_job_settlement_smoke job=% due=% due_minor=%',
    v_job, v_exp_due, v_row.amount_due_minor;
END $$;

ROLLBACK;
