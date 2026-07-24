-- Phase 8 procurement smoke (postgres). Requires seed users, MAIN/EA, Phase 4 receipt path.

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
  v_wh UUID := 'a0000000-0000-4000-8000-000000000003';
  v_fin UUID := 'a0000000-0000-4000-8000-000000000002';
  v_supplier UUID;
  v_supplier_user UUID := 'b0000000-0000-4000-8000-000000000010';
  v_other_supplier UUID;
  v_po UUID;
  v_po_line UUID;
  v_grn UUID;
  v_mr UUID;
  v_mr_line UUID;
  v_po2 UUID;
  v_lcv UUID;
  v_batch UUID;
  v_payload TEXT;
  v_cost NUMERIC;
  v_prior NUMERIC;
  v_visible INT;
  v_hidden INT;
  v_journal UUID;
  v_rev UUID;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';

  IF v_main IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN/EA missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id, requires_serial)
  VALUES ('P8-PROC-SMOKE', 'Phase8 procurement part', v_uom, false)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;

  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P8-PROC-SMOKE';
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
    'supplier-smoke@gtr.local',
    crypt('local-dev-supplier', gen_salt('bf')),
    now(),
    '{"provider":"email","providers":["email"]}'::jsonb,
    '{"full_name":"Supplier Smoke"}'::jsonb,
    now(),
    now(),
    '', '', '', ''
  )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.profiles (id, full_name, is_staff)
  VALUES (v_supplier_user, 'Supplier Smoke', false)
  ON CONFLICT (id) DO NOTHING;

  PERFORM public._test_set_auth_uid('a0000000-0000-4000-8000-000000000001');

  v_supplier := public.create_supplier('P8-SUP', 'Phase8 Supplier');
  v_other_supplier := public.create_supplier('P8-OTHER', 'Other Supplier');
  PERFORM public.link_supplier_profile(v_supplier, v_supplier_user);

  v_mr := public.create_material_request(
    v_main,
    CURRENT_DATE + 7,
    jsonb_build_array(
      jsonb_build_object('stock_item_id', v_item, 'uom_id', v_uom, 'qty', 5)
    ),
    'MR smoke'
  );
  PERFORM public.submit_material_request(v_mr);

  SELECT id INTO v_mr_line FROM public.material_request_lines WHERE material_request_id = v_mr LIMIT 1;

  v_po := public.convert_material_request_to_po(v_mr, v_supplier, 'USD', 1, ARRAY[v_mr_line]);

  IF NOT EXISTS (
    SELECT 1 FROM public.material_request_lines
    WHERE id = v_mr_line AND qty_converted = 5 AND purchase_order_line_id IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'smoke fail: MR line not fully converted';
  END IF;

  PERFORM public.submit_purchase_order(v_po);

  SELECT id INTO v_po_line FROM public.purchase_order_lines WHERE purchase_order_id = v_po LIMIT 1;

  PERFORM public._test_set_auth_uid(v_wh);

  v_grn := public.create_goods_receipt(
    v_po,
    jsonb_build_array(
      jsonb_build_object('purchase_order_line_id', v_po_line, 'qty', 5, 'unit_cost', 20)
    ),
    'GRN smoke'
  );

  PERFORM public.submit_goods_receipt(v_grn);

  IF NOT EXISTS (
    SELECT 1 FROM public.goods_receipts gr
    JOIN public.stock_entries se ON se.id = gr.stock_entry_id
    WHERE gr.id = v_grn AND gr.status = 'submitted' AND se.status = 'posted'
  ) THEN
    RAISE EXCEPTION 'smoke fail: GRN not linked to posted stock entry';
  END IF;

  SELECT payload INTO v_payload
  FROM public.inventory_qr_codes
  WHERE stock_item_id = v_item
  ORDER BY generated_at DESC
  LIMIT 1;

  IF v_payload IS NULL OR v_payload NOT LIKE 'gtr://part/P8-PROC-SMOKE?batch=%' THEN
    RAISE EXCEPTION 'smoke fail: QR payload missing (%)', v_payload;
  END IF;

  SELECT sb.id, sb.unit_cost INTO v_batch, v_prior
  FROM public.goods_receipt_lines grl
  JOIN public.stock_entry_lines sel ON sel.id = grl.stock_entry_line_id
  JOIN public.stock_batches sb ON sb.id = sel.stock_batch_id
  WHERE grl.goods_receipt_id = v_grn
  LIMIT 1;

  IF v_batch IS NULL THEN
    RAISE EXCEPTION 'smoke fail: batch not linked from GRN';
  END IF;

  PERFORM public._test_set_auth_uid(v_fin);

  v_lcv := public.create_landed_cost_voucher(
    v_grn,
    'USD',
    1,
    jsonb_build_array(jsonb_build_object('charge_type', 'freight', 'amount', 10, 'currency', 'USD')),
    jsonb_build_array(jsonb_build_object('stock_batch_id', v_batch, 'allocated_amount', 10)),
    'LCV smoke'
  );

  PERFORM public.submit_landed_cost_voucher(v_lcv);

  SELECT unit_cost INTO v_cost FROM public.stock_batches WHERE id = v_batch;
  IF v_cost <> round(v_prior + 10 / 5, 4) THEN
    RAISE EXCEPTION 'smoke fail: batch unit_cost not revalued (was % now %)', v_prior, v_cost;
  END IF;

  SELECT journal_entry_id INTO v_journal FROM public.landed_cost_vouchers WHERE id = v_lcv;

  PERFORM public.cancel_landed_cost_voucher(v_lcv, 'smoke cancel');

  SELECT unit_cost INTO v_cost FROM public.stock_batches WHERE id = v_batch;
  IF v_cost <> v_prior THEN
    RAISE EXCEPTION 'smoke fail: LCV cancel did not restore batch cost';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.landed_cost_vouchers
    WHERE id = v_lcv AND status = 'cancelled' AND reversal_journal_entry_id IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'smoke fail: LCV cancel missing reversal journal';
  END IF;

  -- Supplier RLS: own PO visible, other supplier PO not
  PERFORM public._test_set_auth_uid(v_supplier_user);

  SELECT count(*) INTO v_visible FROM public.purchase_orders WHERE id = v_po;
  IF v_visible <> 1 THEN
    RAISE EXCEPTION 'smoke fail: supplier cannot see own PO (count=%)', v_visible;
  END IF;

  PERFORM public._test_set_auth_uid('a0000000-0000-4000-8000-000000000001');
  v_po2 := public.create_purchase_order(
    v_other_supplier,
    v_main,
    'USD',
    1,
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 1,
        'unit_price', 1
      )
    ),
    'other PO'
  );
  PERFORM public.submit_purchase_order(v_po2);

  PERFORM public._test_set_auth_uid(v_supplier_user);
  SELECT count(*) INTO v_hidden FROM public.purchase_orders WHERE id = v_po2;
  IF v_hidden <> 0 THEN
    RAISE EXCEPTION 'smoke fail: supplier saw other supplier PO';
  END IF;

  RAISE NOTICE 'phase8_procurement_smoke: PASS po=% grn=% lcv=%', v_po, v_grn, v_lcv;
END;
$$;
