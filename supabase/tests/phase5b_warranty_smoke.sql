-- Phase 5b warranty smoke (postgres). Requires MAIN/QUAR, Phase 4/5 seeds.

DO $$
DECLARE
  v_main UUID;
  v_quar UUID;
  v_uom UUID;
  v_item UUID;
  v_cart UUID;
  v_inv UUID;
  v_claim UUID;
  v_entry UUID;
  v_bad UUID;
BEGIN
  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN';
  SELECT id INTO v_quar FROM public.warehouses WHERE code = 'QUAR';
  SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';

  IF v_main IS NULL OR v_quar IS NULL THEN
    RAISE EXCEPTION 'smoke fail: MAIN/QUAR missing';
  END IF;

  -- open without linkage must fail
  BEGIN
    PERFORM public.open_warranty_claim(NULL, NULL, NULL, 'bad');
    RAISE EXCEPTION 'smoke fail: open without linkage should throw';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLERRM NOT LIKE '%requires stock_serial_id%' THEN
        RAISE;
      END IF;
  END;

  INSERT INTO public.stock_items (oem_part_number, description, base_uom_id)
  VALUES ('P5B-WC-SMOKE', 'Warranty smoke part', v_uom)
  ON CONFLICT (oem_part_number) DO UPDATE SET description = EXCLUDED.description
  RETURNING id INTO v_item;
  IF v_item IS NULL THEN
    SELECT id INTO v_item FROM public.stock_items WHERE oem_part_number = 'P5B-WC-SMOKE';
  END IF;

  PERFORM public.post_stock_receipt(
    v_main,
    'P5b seed',
    jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_item,
        'uom_id', v_uom,
        'qty', 5,
        'unit_cost', 15,
        'currency', 'USD',
        'valuation_method', 'FIFO'
      )
    )
  );

  v_cart := public.create_pos_cart(v_main, NULL, 'USD');
  PERFORM public.add_cart_line(v_cart, v_item, v_uom, 1);
  v_inv := public.checkout_pos_cart(v_cart);

  v_claim := public.open_warranty_claim(NULL, v_inv, NULL, 'Invoice-linked smoke');
  IF NOT EXISTS (
    SELECT 1 FROM public.warranty_claims
    WHERE id = v_claim AND status = 'open' AND document_number LIKE 'WC-%'
  ) THEN
    RAISE EXCEPTION 'smoke fail: claim not open with WC- number';
  END IF;

  v_claim := public.approve_warranty_claim(
    v_claim,
    'return_only',
    NULL,
    NULL
  );

  SELECT quarantine_stock_entry_id INTO v_entry
  FROM public.warranty_claims WHERE id = v_claim;

  IF v_entry IS NULL THEN
    RAISE EXCEPTION 'smoke fail: approve return_only missing quarantine transfer';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.stock_entries
    WHERE id = v_entry AND to_warehouse_id = v_quar AND entry_type = 'transfer'
  ) THEN
    RAISE EXCEPTION 'smoke fail: return did not target QUAR';
  END IF;

  PERFORM public.close_warranty_claim(v_claim);

  -- reject → close on second claim
  v_claim := public.open_warranty_claim(NULL, v_inv, NULL, 'Reject path');
  PERFORM public.reject_warranty_claim(v_claim, 'Not covered');
  PERFORM public.close_warranty_claim(v_claim);

  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'warranty_claims'
      AND column_name ILIKE '%fiscal%'
  ) THEN
    RAISE EXCEPTION 'smoke fail: fiscal columns on warranty_claims';
  END IF;

  RAISE NOTICE 'phase5b_warranty_smoke: PASS claim=% quarantine_entry=%', v_claim, v_entry;
END;
$$;
