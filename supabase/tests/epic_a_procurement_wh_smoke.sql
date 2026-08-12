-- Epic A evidence (E3–E4): preferred suppliers, fund release + domain event,
-- master stock (Total/WH1/WH2), GRN invoice attach, amount_minor dual-write.
-- Requires seed staff users + MAIN/EA; run after db reset with 20260812* migrations.

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
  v_wh1 UUID;
  v_wh2 UUID;
  v_uom UUID;
  v_item UUID;
  v_admin UUID := 'a0000000-0000-4000-8000-000000000001';
  v_wh UUID := 'a0000000-0000-4000-8000-000000000003';
  v_fin UUID := 'a0000000-0000-4000-8000-000000000002';
  v_supplier UUID;
  v_po UUID;
  v_po_line UUID;
  v_grn UUID;
  v_release UUID;
  v_amount NUMERIC;
  v_amount_minor BIGINT;
  v_progress TEXT;
  v_funds_at TIMESTAMPTZ;
  v_evt INT;
  v_ms RECORD;
  v_resolved UUID;
  v_invoice_path TEXT := 'smoke/epic-a/invoice.pdf';
BEGIN
  SELECT id INTO v_wh1 FROM public.warehouses
  WHERE role_code = 'WH1' OR code IN ('WH1', 'MAIN')
  ORDER BY CASE WHEN code = 'WH1' THEN 0 WHEN code = 'MAIN' THEN 1 ELSE 2 END
  LIMIT 1;
  SELECT id INTO v_wh2 FROM public.warehouses
  WHERE role_code = 'WH2' OR code = 'WH2'
  LIMIT 1;
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';

  IF v_wh1 IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'epic A smoke fail: WH1/MAIN or EA missing';
  END IF;
  IF v_wh2 IS NULL THEN
    RAISE EXCEPTION 'epic A smoke fail: WH2 warehouse missing (role_code/code)';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.warehouses WHERE id = v_wh1 AND role_code = 'WH1'
  ) THEN
    RAISE EXCEPTION 'epic A smoke fail: receiving warehouse role_code not WH1';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.warehouses WHERE id = v_wh2 AND role_code = 'WH2'
  ) THEN
    RAISE EXCEPTION 'epic A smoke fail: storefloor warehouse role_code not WH2';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id, requires_serial)
  VALUES ('EPIC-A-SMOKE', 'Epic A procurement/WH smoke part', v_uom, false)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'EPIC-A-SMOKE';
  END IF;

  -- Preferred supplier upsert / deactivate
  PERFORM public._test_set_auth_uid(v_admin);
  v_supplier := public.upsert_preferred_supplier(
    'EPIC-A-SUP',
    'Epic A Preferred Supplier',
    'epic-a-sup@gtr.local',
    NULL,
    'USD',
    'relationship smoke',
    '1 Smoke Ave',
    NULL,
    'Net 30',
    ARRAY['filters']::text[]
  );
  IF v_supplier IS NULL THEN
    RAISE EXCEPTION 'epic A smoke fail: upsert_preferred_supplier returned null';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.suppliers
    WHERE id = v_supplier AND is_preferred AND is_active AND code = 'EPIC-A-SUP'
  ) THEN
    RAISE EXCEPTION 'epic A smoke fail: preferred supplier flags not set';
  END IF;

  -- Idempotent upsert by code
  IF public.upsert_preferred_supplier(
    'EPIC-A-SUP',
    'Epic A Preferred Supplier Updated',
    'epic-a-sup@gtr.local',
    NULL,
    'USD',
    'updated notes',
    NULL,
    NULL,
    NULL,
    '{}'::text[]
  ) <> v_supplier THEN
    RAISE EXCEPTION 'epic A smoke fail: upsert should return same supplier id';
  END IF;

  -- Manual PO → submit → finance approve → fund release + events
  v_po := public.create_purchase_order(
    v_supplier,
    v_wh1,
    'USD',
    1,
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 4,
        'unit_price', 12.50,
        'currency', 'USD'
      )
    ),
    'Epic A fund-release smoke'
  );
  PERFORM public.submit_purchase_order(v_po);

  SELECT id, unit_price_minor INTO v_po_line, v_amount_minor
  FROM public.purchase_order_lines
  WHERE purchase_order_id = v_po
  LIMIT 1;
  IF v_po_line IS NULL THEN
    RAISE EXCEPTION 'epic A smoke fail: PO line missing';
  END IF;
  IF v_amount_minor IS DISTINCT FROM 1250 THEN
    RAISE EXCEPTION 'epic A smoke fail: unit_price_minor dual-write expected 1250 got %', v_amount_minor;
  END IF;

  PERFORM public._test_set_auth_uid(v_fin);
  PERFORM public.approve_purchase_order(v_po);

  SELECT id, amount, amount_minor
  INTO v_release, v_amount, v_amount_minor
  FROM public.procurement_fund_releases
  WHERE purchase_order_id = v_po;
  IF v_release IS NULL THEN
    RAISE EXCEPTION 'epic A smoke fail: procurement_fund_releases row missing';
  END IF;
  IF v_amount IS DISTINCT FROM 50.00 THEN
    RAISE EXCEPTION 'epic A smoke fail: fund release amount expected 50 got %', v_amount;
  END IF;
  IF v_amount_minor IS DISTINCT FROM 5000 THEN
    RAISE EXCEPTION 'epic A smoke fail: amount_minor dual-write expected 5000 got %', v_amount_minor;
  END IF;

  SELECT progress_step, funds_released_at
  INTO v_progress, v_funds_at
  FROM public.purchase_orders
  WHERE id = v_po;
  IF v_progress IS DISTINCT FROM 'funds_released' THEN
    RAISE EXCEPTION 'epic A smoke fail: progress_step=% (want funds_released)', v_progress;
  END IF;
  IF v_funds_at IS NULL THEN
    RAISE EXCEPTION 'epic A smoke fail: funds_released_at not set';
  END IF;

  SELECT COUNT(*)::int INTO v_evt
  FROM public.domain_events
  WHERE event_code = 'procurement_funds_released'
    AND (
      dedupe_key = 'procurement_fund_release:' || v_release::text
      OR payload->>'purchase_order_id' = v_po::text
    );
  IF v_evt < 1 THEN
    RAISE EXCEPTION 'epic A smoke fail: procurement_funds_released domain event missing';
  END IF;

  SELECT COUNT(*)::int INTO v_evt
  FROM public.domain_events
  WHERE event_code = 'po_approved'
    AND dedupe_key = 'purchase_order_approved:' || v_po::text;
  IF v_evt < 1 THEN
    RAISE EXCEPTION 'epic A smoke fail: po_approved domain event missing';
  END IF;

  -- GRN + invoice attach
  PERFORM public._test_set_auth_uid(v_wh);
  v_grn := public.create_goods_receipt(
    v_po,
    jsonb_build_array(
      jsonb_build_object('purchase_order_line_id', v_po_line, 'qty', 4, 'unit_cost', 12.50)
    ),
    'Epic A GRN invoice smoke'
  );
  PERFORM public.attach_goods_receipt_invoice(v_grn, v_invoice_path);
  IF NOT EXISTS (
    SELECT 1 FROM public.goods_receipts
    WHERE id = v_grn
      AND supplier_invoice_path = v_invoice_path
      AND supplier_invoice_uploaded_at IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'epic A smoke fail: attach_goods_receipt_invoice did not set path';
  END IF;
  PERFORM public.submit_goods_receipt(v_grn);

  -- Master stock columns (Total + WH1 + WH2) + OEM resolve
  v_resolved := public.resolve_stock_item_by_oem('epic-a-smoke');
  IF v_resolved IS DISTINCT FROM v_item THEN
    RAISE EXCEPTION 'epic A smoke fail: resolve_stock_item_by_oem mismatch';
  END IF;

  SELECT * INTO v_ms
  FROM public.list_master_stock(50, 'EPIC-A-SMOKE')
  WHERE stock_item_id = v_item;
  IF v_ms.stock_item_id IS NULL THEN
    RAISE EXCEPTION 'epic A smoke fail: list_master_stock missing EPIC-A-SMOKE';
  END IF;
  IF v_ms.qty_total IS NULL OR v_ms.qty_wh1 IS NULL OR v_ms.qty_wh2 IS NULL THEN
    RAISE EXCEPTION 'epic A smoke fail: list_master_stock missing Total/WH1/WH2 columns';
  END IF;
  IF v_ms.qty_total < 4 OR v_ms.qty_wh1 < 4 THEN
    RAISE EXCEPTION
      'epic A smoke fail: expected GRN qty on WH1 (total=% wh1=% wh2=%)',
      v_ms.qty_total, v_ms.qty_wh1, v_ms.qty_wh2;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.v_master_stock
    WHERE stock_item_id = v_item
      AND qty_total = v_ms.qty_total
      AND qty_wh1 = v_ms.qty_wh1
      AND qty_wh2 = v_ms.qty_wh2
  ) THEN
    RAISE EXCEPTION 'epic A smoke fail: v_master_stock columns mismatch list_master_stock';
  END IF;

  -- Deactivate preferred supplier (after PO path so supplier still usable above)
  PERFORM public._test_set_auth_uid(v_admin);
  PERFORM public.deactivate_preferred_supplier(v_supplier);
  IF NOT EXISTS (
    SELECT 1 FROM public.suppliers
    WHERE id = v_supplier AND is_preferred = false AND is_active = false
  ) THEN
    RAISE EXCEPTION 'epic A smoke fail: deactivate_preferred_supplier did not clear flags';
  END IF;

  RAISE NOTICE 'epic A procurement/WH smoke OK';
END;
$$;
