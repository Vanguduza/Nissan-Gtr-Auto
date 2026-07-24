-- Phase 8b RFQ + blanket smoke (postgres). Requires seed users, MAIN/EA, Phase 8 procurement.

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
  v_main UUID;
  v_uom UUID;
  v_item UUID;
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_supplier_user UUID := 'b0000000-0000-4000-8000-000000000010';
  v_other_user UUID := 'b0000000-0000-4000-8000-000000000011';
  v_supplier UUID;
  v_other_supplier UUID;
  v_rfq UUID;
  v_rfq_line UUID;
  v_quote UUID;
  v_other_quote UUID;
  v_po UUID;
  v_blanket UUID;
  v_blanket_line UUID;
  v_release UUID;
  v_visible INT;
  v_hidden INT;
  v_remaining NUMERIC;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';

  IF v_main IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN/EA missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id, requires_serial)
  VALUES ('P8B-RFQ-SMOKE', 'Phase8b RFQ part', v_uom, false)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;

  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P8B-RFQ-SMOKE';
  END IF;

  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, recovery_token, email_change_token_new, email_change
  )
  VALUES (
    '00000000-0000-0000-0000-000000000000',
    v_supplier_user,
    'authenticated',
    'authenticated',
    'supplier-p8b@gtr.local',
    crypt('local-dev-supplier', gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"full_name":"Supplier P8b"}'::jsonb,
    now(),
    now(),
    '', '', '', ''
  )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (v_supplier_user, 'Supplier P8b', false)
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, recovery_token, email_change_token_new, email_change
  )
  VALUES (
    '00000000-0000-0000-0000-000000000000',
    v_other_user,
    'authenticated',
    'authenticated',
    'supplier-other-p8b@gtr.local',
    crypt('local-dev-supplier', gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"full_name":"Other Supplier P8b"}'::jsonb,
    now(),
    now(),
    '', '', '', ''
  )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (v_other_user, 'Other Supplier P8b', false)
  ON CONFLICT (id) DO NOTHING;

  PERFORM public._test_set_auth_uid(v_admin);

  v_supplier := public.create_supplier('P8B-SUP', 'P8b Primary Supplier');
  v_other_supplier := public.create_supplier('P8B-OTH', 'P8b Other Supplier');
  PERFORM public.link_supplier_profile(v_supplier, v_supplier_user);
  PERFORM public.link_supplier_profile(v_other_supplier, v_other_user);

  v_rfq := public.create_rfq(
    v_main,
    CURRENT_DATE + 14,
    jsonb_build_array(
      jsonb_build_object('stock_item_id', v_item, 'uom_id', v_uom, 'qty', 10)
    ),
    ARRAY[v_supplier, v_other_supplier],
    'RFQ smoke'
  );
  PERFORM public.submit_rfq(v_rfq);

  SELECT id INTO v_rfq_line FROM public.rfq_lines WHERE rfq_id = v_rfq LIMIT 1;

  PERFORM public._test_set_auth_uid(v_supplier_user);
  v_quote := public.upsert_supplier_quotation(
    v_rfq,
    'USD',
    1,
    jsonb_build_array(
      jsonb_build_object(
        'rfq_line_id', v_rfq_line,
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 10,
        'unit_price', 12.5
      )
    ),
    CURRENT_DATE + 7,
    'Primary quote'
  );
  PERFORM public.submit_supplier_quotation(v_quote);

  PERFORM public._test_set_auth_uid(v_other_user);
  v_other_quote := public.upsert_supplier_quotation(
    v_rfq,
    'USD',
    1,
    jsonb_build_array(
      jsonb_build_object(
        'rfq_line_id', v_rfq_line,
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 10,
        'unit_price', 11
      )
    ),
    CURRENT_DATE + 7,
    'Other quote'
  );
  PERFORM public.submit_supplier_quotation(v_other_quote);

  -- Supplier RLS: own quote visible, peer quote hidden
  PERFORM public._test_set_auth_uid(v_supplier_user);
  SET LOCAL role authenticated;

  SELECT count(*) INTO v_visible FROM public.supplier_quotations WHERE id = v_quote;
  IF v_visible <> 1 THEN
    RAISE EXCEPTION 'smoke fail: supplier cannot see own quote';
  END IF;

  SELECT count(*) INTO v_hidden FROM public.supplier_quotations WHERE id = v_other_quote;
  IF v_hidden <> 0 THEN
    RAISE EXCEPTION 'smoke fail: supplier saw peer quote';
  END IF;

  RESET ROLE;

  PERFORM public._test_set_auth_uid(v_admin);
  v_po := public.award_quotation_to_po(v_quote, 'Award smoke');

  IF NOT EXISTS (
    SELECT 1
    FROM public.purchase_orders po
    JOIN public.purchase_order_lines pol ON pol.purchase_order_id = po.id
    WHERE po.id = v_po
      AND po.awarded_quotation_id = v_quote
      AND pol.qty_ordered = 10
      AND pol.unit_price = 12.5
      AND po.currency = 'USD'
  ) THEN
    RAISE EXCEPTION 'smoke fail: award did not create PO with quote lines/prices';
  END IF;

  -- Blanket PO + release + over-release deny + cancel restore
  v_blanket := public.create_blanket_purchase_order(
    v_supplier,
    v_main,
    'USD',
    1,
    50,
    jsonb_build_array(
      jsonb_build_object('stock_item_id', v_item, 'uom_id', v_uom, 'qty', 20, 'unit_price', 5)
    ),
    'Blanket smoke'
  );
  PERFORM public.submit_purchase_order(v_blanket);

  SELECT id INTO v_blanket_line FROM public.purchase_order_lines WHERE purchase_order_id = v_blanket LIMIT 1;

  v_release := public.create_blanket_release(
    v_blanket,
    jsonb_build_array(jsonb_build_object('blanket_line_id', v_blanket_line, 'qty', 8)),
    'Release 8'
  );

  SELECT qty_released INTO v_remaining FROM public.purchase_order_lines WHERE id = v_blanket_line;
  IF v_remaining <> 8 THEN
    RAISE EXCEPTION 'smoke fail: qty_released expected 8 got %', v_remaining;
  END IF;

  BEGIN
    PERFORM public.create_blanket_release(
      v_blanket,
      jsonb_build_array(jsonb_build_object('blanket_line_id', v_blanket_line, 'qty', 15)),
      'Should fail qty'
    );
    RAISE EXCEPTION 'smoke fail: over-qty release was allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%remaining qty%' THEN
        RAISE;
      END IF;
  END;

  BEGIN
    PERFORM public.create_blanket_release(
      v_blanket,
      jsonb_build_array(jsonb_build_object('blanket_line_id', v_blanket_line, 'qty', 3)),
      'Should fail value'
    );
    RAISE EXCEPTION 'smoke fail: over-value release was allowed';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%remaining blanket value%' THEN
        RAISE;
      END IF;
  END;

  PERFORM public.cancel_purchase_order(v_release, 'restore remaining');

  SELECT qty_released INTO v_remaining FROM public.purchase_order_lines WHERE id = v_blanket_line;
  IF v_remaining <> 0 THEN
    RAISE EXCEPTION 'smoke fail: cancel release did not restore qty (got %)', v_remaining;
  END IF;

  SELECT blanket_value_released INTO v_remaining FROM public.purchase_orders WHERE id = v_blanket;
  IF v_remaining <> 0 THEN
    RAISE EXCEPTION 'smoke fail: cancel release did not restore value (got %)', v_remaining;
  END IF;

  RAISE NOTICE 'phase8b_rfq_blanket_smoke: OK';
END;
$$;
