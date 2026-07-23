-- Phase 4 inventory smoke (postgres). Requires MAIN/QUAR warehouses + UOM seed from migrations.

DO $$
DECLARE
  v_main UUID;
  v_quar UUID;
  v_uom UUID;
  v_item UUID;
  v_entry UUID;
  v_payload TEXT;
  v_qty NUMERIC;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_quar FROM public.warehouses WHERE code = 'QUAR';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';

  IF v_main IS NULL OR v_quar IS NULL OR v_uom IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN/QUAR/EA missing';
  END IF;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id, requires_serial)
  VALUES ('21410-JF00A', 'Smoke water pump', v_uom, false)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;

  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = '21410-JF00A';
  END IF;

  v_entry := public.post_stock_receipt(
    v_main,
    'Smoke receipt',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 10,
        'unit_cost', 25,
        'currency', 'USD',
        'valuation_method', 'FIFO'
      )
    )
  );

  SELECT payload INTO v_payload
  FROM public.inventory_qr_codes
  WHERE stock_item_id = v_item
  ORDER BY generated_at DESC
  LIMIT 1;

  IF v_payload IS NULL OR v_payload NOT LIKE 'gtr://part/21410-JF00A?batch=%' THEN
    RAISE EXCEPTION 'smoke fail: QR payload missing/invalid (%)', v_payload;
  END IF;

  SELECT quantity INTO v_qty
  FROM public.stock_levels
  WHERE stock_item_id = v_item AND warehouse_id = v_main;

  IF v_qty IS NULL OR v_qty < 10 THEN
    RAISE EXCEPTION 'smoke fail: stock level not updated (%)', v_qty;
  END IF;

  -- Return path must target quarantine warehouse via pending transfer
  v_entry := public.post_return_to_quarantine(
    v_main,
    'Smoke return',
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
    WHERE id = v_entry
      AND entry_type = 'transfer'
      AND to_warehouse_id = v_quar
      AND status = 'pending_approval'
  ) THEN
    RAISE EXCEPTION 'smoke fail: return did not create pending QUAR transfer';
  END IF;

  RAISE NOTICE 'phase4_inventory_smoke: PASS';
END;
$$;
