-- Phase 1 procurement approve/reject smoke (postgres).
-- Requires seed staff users, MAIN/EA, prior Phase 8 procurement.

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
  v_wh UUID := 'a0000000-0000-4000-8000-000000000003';
  v_fin UUID := 'a0000000-0000-4000-8000-000000000002';
  v_supplier UUID;
  v_mr UUID;
  v_po UUID;
  v_po_reject UUID;
  v_po_line UUID;
  v_grn UUID;
  v_st public.procurement_doc_status;
  v_evt INT;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';

  IF v_main IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'approve smoke fail: MAIN/EA missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id, requires_serial)
  VALUES ('P8-APPROVE-SMOKE', 'Phase1 approve part', v_uom, false)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;

  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P8-APPROVE-SMOKE';
  END IF;

  PERFORM public._test_set_auth_uid(v_admin);

  SELECT id INTO v_supplier FROM public.suppliers WHERE code = 'P8-SUP' LIMIT 1;
  IF v_supplier IS NULL THEN
    v_supplier := public.create_supplier('P8-APV', 'Approve Smoke Supplier');
  END IF;

  -- MR: submit → approve → convert
  v_mr := public.create_material_request(
    v_main,
    CURRENT_DATE + 7,
    jsonb_build_array(
      jsonb_build_object('stock_item_id', v_item, 'uom_id', v_uom, 'qty', 3)
    ),
    'MR approve smoke'
  );
  PERFORM public.submit_material_request(v_mr);

  BEGIN
    PERFORM public.convert_material_request_to_po(v_mr, v_supplier, 'USD', 1, NULL);
    RAISE EXCEPTION 'approve smoke fail: convert should require approved MR';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%must be approved%' THEN
        RAISE;
      END IF;
  END;

  PERFORM public._test_set_auth_uid(v_fin);
  PERFORM public.approve_material_request(v_mr);

  SELECT status INTO v_st FROM public.material_requests WHERE id = v_mr;
  IF v_st <> 'approved' THEN
    RAISE EXCEPTION 'approve smoke fail: MR status=%', v_st;
  END IF;

  PERFORM public._test_set_auth_uid(v_wh);
  v_po := public.convert_material_request_to_po(v_mr, v_supplier, 'USD', 1, NULL);
  PERFORM public.submit_purchase_order(v_po);

  -- GRN blocked until PO approved
  SELECT id INTO v_po_line FROM public.purchase_order_lines WHERE purchase_order_id = v_po LIMIT 1;

  BEGIN
    PERFORM public.create_goods_receipt(
      v_po,
      jsonb_build_array(
        jsonb_build_object('purchase_order_line_id', v_po_line, 'qty', 3, 'unit_cost', 12)
      ),
      'should fail'
    );
    RAISE EXCEPTION 'approve smoke fail: GRN should require approved PO';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%must be approved%' THEN
        RAISE;
      END IF;
  END;

  -- Warehouse cannot approve
  BEGIN
    PERFORM public.approve_purchase_order(v_po);
    RAISE EXCEPTION 'approve smoke fail: warehouse should not approve PO';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%admin or finance%' THEN
        RAISE;
      END IF;
  END;

  PERFORM public._test_set_auth_uid(v_fin);
  PERFORM public.approve_purchase_order(v_po);

  SELECT status INTO v_st FROM public.purchase_orders WHERE id = v_po;
  IF v_st <> 'approved' THEN
    RAISE EXCEPTION 'approve smoke fail: PO status=%', v_st;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.procurement_fund_releases WHERE purchase_order_id = v_po
  ) THEN
    RAISE EXCEPTION 'approve smoke fail: procurement_fund_releases row missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.purchase_orders
    WHERE id = v_po AND progress_step = 'funds_released' AND funds_released_at IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'approve smoke fail: progress_step/funds_released_at not set';
  END IF;

  SELECT COUNT(*)::int INTO v_evt
  FROM public.domain_events
  WHERE event_code = 'po_approved'
    AND dedupe_key = 'purchase_order_approved:' || v_po::text;
  IF v_evt < 1 THEN
    RAISE EXCEPTION 'approve smoke fail: po_approved domain event missing';
  END IF;

  SELECT COUNT(*)::int INTO v_evt
  FROM public.domain_events
  WHERE event_code = 'procurement_funds_released'
    AND payload->>'purchase_order_id' = v_po::text;
  IF v_evt < 1 THEN
    RAISE EXCEPTION 'approve smoke fail: procurement_funds_released domain event missing';
  END IF;

  PERFORM public._test_set_auth_uid(v_wh);
  v_grn := public.create_goods_receipt(
    v_po,
    jsonb_build_array(
      jsonb_build_object('purchase_order_line_id', v_po_line, 'qty', 3, 'unit_cost', 12)
    ),
    'GRN after approve'
  );
  IF v_grn IS NULL THEN
    RAISE EXCEPTION 'approve smoke fail: GRN not created';
  END IF;

  -- Reject path
  PERFORM public._test_set_auth_uid(v_admin);
  v_po_reject := public.create_purchase_order(
    v_supplier,
    v_main,
    'USD',
    1,
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 1,
        'unit_price', 5,
        'currency', 'USD'
      )
    ),
    'reject smoke'
  );
  PERFORM public.submit_purchase_order(v_po_reject);

  PERFORM public._test_set_auth_uid(v_fin);
  PERFORM public.reject_purchase_order(v_po_reject, 'not needed');

  SELECT status INTO v_st FROM public.purchase_orders WHERE id = v_po_reject;
  IF v_st <> 'rejected' THEN
    RAISE EXCEPTION 'approve smoke fail: reject status=%', v_st;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.purchase_orders
    WHERE id = v_po_reject AND rejection_reason = 'not needed'
  ) THEN
    RAISE EXCEPTION 'approve smoke fail: rejection_reason not stored';
  END IF;

  RAISE NOTICE 'procurement approve smoke OK';
END;
$$;
