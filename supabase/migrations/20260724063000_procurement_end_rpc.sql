-- Phase 8c follow-up: clear app.procurement_rpc after SECURITY DEFINER RPCs.
-- Transaction-local GUC leaked across RPC returns inside long smoke DO blocks.
-- Nested RPC->RPC (convert_material_request_to_po / award_quotation_to_po -> create_purchase_order)
-- uses app.procurement_rpc_depth so only the outermost end clears the flag.

CREATE OR REPLACE FUNCTION public._procurement_begin_rpc()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  v_depth integer;
BEGIN
  v_depth := COALESCE(NULLIF(current_setting('app.procurement_rpc_depth', true), '')::integer, 0) + 1;
  PERFORM set_config('app.procurement_rpc_depth', v_depth::text, true);
  PERFORM set_config('app.procurement_rpc', '1', true);
END;
$$;

CREATE OR REPLACE FUNCTION public._procurement_end_rpc()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  v_depth integer;
BEGIN
  v_depth := COALESCE(NULLIF(current_setting('app.procurement_rpc_depth', true), '')::integer, 0);
  IF v_depth <= 1 THEN
    PERFORM set_config('app.procurement_rpc_depth', '0', true);
    PERFORM set_config('app.procurement_rpc', '', true);
  ELSE
    PERFORM set_config('app.procurement_rpc_depth', (v_depth - 1)::text, true);
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public._procurement_begin_rpc() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._procurement_end_rpc() FROM PUBLIC;

CREATE OR REPLACE FUNCTION public.create_material_request(
  p_warehouse_id UUID,
  p_needed_by DATE,
  p_lines JSONB,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_mr UUID;
  v_line JSONB;
  v_no INT := 0;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();

  IF p_lines IS NULL OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'material request lines required';
  END IF;

  INSERT INTO public.material_requests (
    document_number, warehouse_id, needed_by, notes, created_by
  )
  VALUES (
    public.next_series_value('MR-'), p_warehouse_id, p_needed_by, p_notes, auth.uid()
  )
  RETURNING id INTO v_mr;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_no := v_no + 1;
    INSERT INTO public.material_request_lines (
      material_request_id, line_no, stock_item_id, uom_id, qty
    )
    VALUES (
      v_mr,
      v_no,
      (v_line ->> 'stock_item_id')::uuid,
      (v_line ->> 'uom_id')::uuid,
      (v_line ->> 'qty')::numeric
    );
  END LOOP;

  PERFORM public._procurement_end_rpc();
  RETURN v_mr;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_material_request(p_material_request_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_st public.procurement_doc_status;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT status INTO v_st FROM public.material_requests WHERE id = p_material_request_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'material request not found: %', p_material_request_id;
  END IF;
  IF v_st <> 'draft' THEN
    RAISE EXCEPTION 'only draft material requests can be submitted';
  END IF;

  UPDATE public.material_requests
  SET status = 'submitted', submitted_at = now(), updated_at = now()
  WHERE id = p_material_request_id;

  PERFORM public._procurement_end_rpc();
  RETURN p_material_request_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_material_request(
  p_material_request_id UUID,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_st public.procurement_doc_status;
  v_open NUMERIC;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT status INTO v_st FROM public.material_requests WHERE id = p_material_request_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'material request not found: %', p_material_request_id;
  END IF;
  IF v_st = 'cancelled' THEN
    PERFORM public._procurement_end_rpc();
    RETURN p_material_request_id;
  END IF;

  SELECT COALESCE(SUM(qty - qty_converted), 0) INTO v_open
  FROM public.material_request_lines
  WHERE material_request_id = p_material_request_id;

  IF v_st = 'submitted' AND v_open < (
    SELECT COALESCE(SUM(qty), 0) FROM public.material_request_lines WHERE material_request_id = p_material_request_id
  ) THEN
    RAISE EXCEPTION 'cannot cancel MR with converted lines';
  END IF;

  UPDATE public.material_requests
  SET
    status = 'cancelled',
    cancelled_at = now(),
    notes = COALESCE(p_notes, notes),
    updated_at = now()
  WHERE id = p_material_request_id;

  PERFORM public._procurement_end_rpc();
  RETURN p_material_request_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_purchase_order(
  p_supplier_id UUID,
  p_warehouse_id UUID,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC,
  p_lines JSONB,
  p_notes TEXT DEFAULT NULL,
  p_expected_date DATE DEFAULT NULL,
  p_material_request_id UUID DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_po UUID;
  v_line JSONB;
  v_no INT := 0;
  v_rate NUMERIC;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();

  IF p_lines IS NULL OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'purchase order lines required';
  END IF;

  v_rate := CASE
    WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1)
    ELSE p_exchange_rate
  END;
  IF p_currency = 'ZIG' AND (v_rate IS NULL OR v_rate <= 0) THEN
    RAISE EXCEPTION 'positive exchange_rate required for ZIG';
  END IF;

  INSERT INTO public.purchase_orders (
    document_number, supplier_id, warehouse_id, material_request_id,
    currency, exchange_rate_applied, expected_date, notes, created_by
  )
  VALUES (
    public.next_series_value('PO-'),
    p_supplier_id,
    p_warehouse_id,
    p_material_request_id,
    p_currency,
    v_rate,
    p_expected_date,
    p_notes,
    auth.uid()
  )
  RETURNING id INTO v_po;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_no := v_no + 1;
    INSERT INTO public.purchase_order_lines (
      purchase_order_id, line_no, stock_item_id, uom_id,
      qty_ordered, unit_price, currency, material_request_line_id
    )
    VALUES (
      v_po,
      v_no,
      (v_line ->> 'stock_item_id')::uuid,
      (v_line ->> 'uom_id')::uuid,
      (v_line ->> 'qty')::numeric,
      (v_line ->> 'unit_price')::numeric,
      COALESCE((v_line ->> 'currency')::public.currency_code, p_currency),
      NULLIF(v_line ->> 'material_request_line_id', '')::uuid
    );
  END LOOP;

  PERFORM public._procurement_end_rpc();
  RETURN v_po;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_purchase_order(p_purchase_order_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_st public.procurement_doc_status;
  v_doc TEXT;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT status, document_number INTO v_st, v_doc
  FROM public.purchase_orders WHERE id = p_purchase_order_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'purchase order not found: %', p_purchase_order_id;
  END IF;
  IF v_st <> 'draft' THEN
    RAISE EXCEPTION 'only draft purchase orders can be submitted';
  END IF;

  UPDATE public.purchase_orders
  SET status = 'submitted', submitted_at = now(), updated_at = now()
  WHERE id = p_purchase_order_id;

  PERFORM public.emit_domain_event(
    'po_created',
    'purchase_order:' || p_purchase_order_id::text,
    jsonb_build_object(
      'purchase_order_id', p_purchase_order_id,
      'document_number', v_doc
    )
  );

  PERFORM public._procurement_end_rpc();
  RETURN p_purchase_order_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_purchase_order(
  p_purchase_order_id UUID,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_st public.procurement_doc_status;
  v_recv NUMERIC;
  v_po public.purchase_orders%ROWTYPE;
  v_rel RECORD;
  v_restore_value NUMERIC := 0;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT * INTO v_po FROM public.purchase_orders WHERE id = p_purchase_order_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'purchase order not found: %', p_purchase_order_id;
  END IF;
  v_st := v_po.status;

  IF v_st = 'cancelled' THEN
    PERFORM public._procurement_end_rpc();
    RETURN p_purchase_order_id;
  END IF;

  SELECT COALESCE(SUM(qty_received), 0) INTO v_recv
  FROM public.purchase_order_lines
  WHERE purchase_order_id = p_purchase_order_id;

  IF v_recv > 0 THEN
    RAISE EXCEPTION 'cannot cancel PO with receipts';
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.goods_receipts gr
    WHERE gr.purchase_order_id = p_purchase_order_id
      AND gr.status = 'submitted'
  ) THEN
    RAISE EXCEPTION 'cannot cancel PO with submitted GRNs';
  END IF;

  IF v_po.blanket_parent_id IS NOT NULL THEN
    PERFORM 1 FROM public.purchase_orders WHERE id = v_po.blanket_parent_id FOR UPDATE;

    FOR v_rel IN
      SELECT pol.qty_ordered, pol.blanket_parent_line_id
      FROM public.purchase_order_lines pol
      WHERE pol.purchase_order_id = p_purchase_order_id
        AND pol.blanket_parent_line_id IS NOT NULL
    LOOP
      UPDATE public.purchase_order_lines
      SET qty_released = qty_released - v_rel.qty_ordered
      WHERE id = v_rel.blanket_parent_line_id;

      UPDATE public.purchase_orders
      SET
        blanket_value_released = GREATEST(0, blanket_value_released - (v_rel.qty_ordered * (
          SELECT unit_price FROM public.purchase_order_lines WHERE id = v_rel.blanket_parent_line_id
        ))),
        updated_at = now()
      WHERE id = v_po.blanket_parent_id;
    END LOOP;
  END IF;

  UPDATE public.purchase_orders
  SET
    status = 'cancelled',
    cancelled_at = now(),
    notes = COALESCE(p_notes, notes),
    updated_at = now()
  WHERE id = p_purchase_order_id;

  PERFORM public._procurement_end_rpc();
  RETURN p_purchase_order_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.convert_material_request_to_po(
  p_material_request_id UUID,
  p_supplier_id UUID,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC,
  p_line_ids UUID[] DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_mr public.material_requests%ROWTYPE;
  v_po UUID;
  v_lines JSONB := '[]'::jsonb;
  v_row RECORD;
  v_po_line UUID;
  v_remaining NUMERIC;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT * INTO v_mr FROM public.material_requests WHERE id = p_material_request_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'material request not found: %', p_material_request_id;
  END IF;
  IF v_mr.status <> 'submitted' THEN
    RAISE EXCEPTION 'material request must be submitted before conversion';
  END IF;

  FOR v_row IN
    SELECT mrl.*
    FROM public.material_request_lines mrl
    WHERE mrl.material_request_id = p_material_request_id
      AND mrl.qty > mrl.qty_converted
      AND (p_line_ids IS NULL OR mrl.id = ANY (p_line_ids))
    ORDER BY mrl.line_no
  LOOP
    v_remaining := v_row.qty - v_row.qty_converted;
    IF v_remaining <= 0 THEN
      CONTINUE;
    END IF;

    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_row.stock_item_id,
        'uom_id', v_row.uom_id,
        'qty', v_remaining,
        'unit_price', COALESCE(
          (SELECT pli.unit_price
           FROM public.price_list_items pli
           JOIN public.price_lists pl ON pl.id = pli.price_list_id
           WHERE pl.code = 'B2B' AND pli.stock_item_id = v_row.stock_item_id
           LIMIT 1),
          0
        ),
        'currency', p_currency,
        'material_request_line_id', v_row.id
      )
    );
  END LOOP;

  IF jsonb_array_length(v_lines) = 0 THEN
    RAISE EXCEPTION 'no open MR lines to convert';
  END IF;

  v_po := public.create_purchase_order(
    p_supplier_id,
    v_mr.warehouse_id,
    p_currency,
    p_exchange_rate,
    v_lines,
    format('Converted from %s', COALESCE(v_mr.document_number, v_mr.id::text)),
    v_mr.needed_by,
    p_material_request_id
  );

  FOR v_row IN
    SELECT mrl.*
    FROM public.material_request_lines mrl
    WHERE mrl.material_request_id = p_material_request_id
      AND mrl.qty > mrl.qty_converted
      AND (p_line_ids IS NULL OR mrl.id = ANY (p_line_ids))
    ORDER BY mrl.line_no
  LOOP
    v_remaining := v_row.qty - v_row.qty_converted;
    SELECT pol.id INTO v_po_line
    FROM public.purchase_order_lines pol
    WHERE pol.purchase_order_id = v_po
      AND pol.material_request_line_id = v_row.id
    LIMIT 1;

    IF v_po_line IS NULL THEN
      RAISE EXCEPTION 'orphan PO line mapping for MR line %', v_row.id;
    END IF;

    UPDATE public.material_request_lines
    SET
      qty_converted = qty_converted + v_remaining,
      purchase_order_line_id = v_po_line
    WHERE id = v_row.id;
  END LOOP;

  IF EXISTS (
    SELECT 1
    FROM public.purchase_order_lines pol
    WHERE pol.purchase_order_id = v_po
      AND pol.material_request_line_id IS NULL
  ) THEN
    RAISE EXCEPTION 'PO contains lines without MR linkage';
  END IF;

  PERFORM public._procurement_end_rpc();
  RETURN v_po;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_goods_receipt(
  p_purchase_order_id UUID,
  p_lines JSONB,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_po public.purchase_orders%ROWTYPE;
  v_grn UUID;
  v_line JSONB;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();

  SELECT * INTO v_po FROM public.purchase_orders WHERE id = p_purchase_order_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'purchase order not found: %', p_purchase_order_id;
  END IF;
  IF v_po.status <> 'submitted' THEN
    RAISE EXCEPTION 'purchase order must be submitted';
  END IF;

  IF p_lines IS NULL OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'GRN lines required';
  END IF;

  INSERT INTO public.goods_receipts (
    document_number, purchase_order_id, supplier_id, warehouse_id, notes, created_by
  )
  VALUES (
    public.next_series_value('GRN-'),
    p_purchase_order_id,
    v_po.supplier_id,
    v_po.warehouse_id,
    p_notes,
    auth.uid()
  )
  RETURNING id INTO v_grn;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    IF NOT EXISTS (
      SELECT 1 FROM public.purchase_order_lines pol
      WHERE pol.id = (v_line ->> 'purchase_order_line_id')::uuid
        AND pol.purchase_order_id = p_purchase_order_id
    ) THEN
      RAISE EXCEPTION 'invalid PO line for GRN: %', v_line ->> 'purchase_order_line_id';
    END IF;

    INSERT INTO public.goods_receipt_lines (
      goods_receipt_id,
      purchase_order_line_id,
      stock_item_id,
      uom_id,
      qty,
      unit_cost,
      currency
    )
    SELECT
      v_grn,
      pol.id,
      pol.stock_item_id,
      pol.uom_id,
      (v_line ->> 'qty')::numeric,
      COALESCE((v_line ->> 'unit_cost')::numeric, pol.unit_price),
      COALESCE((v_line ->> 'currency')::public.currency_code, pol.currency)
    FROM public.purchase_order_lines pol
    WHERE pol.id = (v_line ->> 'purchase_order_line_id')::uuid
      AND pol.purchase_order_id = p_purchase_order_id;
  END LOOP;

  PERFORM public._procurement_end_rpc();
  RETURN v_grn;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_goods_receipt(p_goods_receipt_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_st public.procurement_doc_status;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT status INTO v_st FROM public.goods_receipts WHERE id = p_goods_receipt_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'goods receipt not found: %', p_goods_receipt_id;
  END IF;
  IF v_st = 'cancelled' THEN
    PERFORM public._procurement_end_rpc();
    RETURN p_goods_receipt_id;
  END IF;
  IF v_st <> 'draft' THEN
    RAISE EXCEPTION 'submitted GRNs are immutable; reverse stock separately';
  END IF;

  UPDATE public.goods_receipts
  SET status = 'cancelled', cancelled_at = now(), updated_at = now()
  WHERE id = p_goods_receipt_id;

  PERFORM public._procurement_end_rpc();
  RETURN p_goods_receipt_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_goods_receipt(p_goods_receipt_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_grn public.goods_receipts%ROWTYPE;
  v_receipt_lines JSONB := '[]'::jsonb;
  v_row RECORD;
  v_entry UUID;
  v_var_pct NUMERIC;
  v_tolerance CONSTANT NUMERIC := 0.05;
  v_mismatch BOOLEAN := false;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT * INTO v_grn FROM public.goods_receipts WHERE id = p_goods_receipt_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'goods receipt not found: %', p_goods_receipt_id;
  END IF;
  IF v_grn.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft GRNs can be submitted';
  END IF;

  FOR v_row IN
    SELECT grl.*, pol.unit_price AS po_unit_price, pol.qty_ordered, pol.qty_received
    FROM public.goods_receipt_lines grl
    JOIN public.purchase_order_lines pol ON pol.id = grl.purchase_order_line_id
    WHERE grl.goods_receipt_id = p_goods_receipt_id
  LOOP
    IF v_row.qty_received + v_row.qty > v_row.qty_ordered THEN
      RAISE EXCEPTION 'over-receipt on PO line %', v_row.purchase_order_line_id;
    END IF;

    IF v_row.po_unit_price > 0 THEN
      v_var_pct := abs(v_row.unit_cost - v_row.po_unit_price) / v_row.po_unit_price;
      IF v_var_pct > v_tolerance THEN
        v_mismatch := true;
      END IF;
    END IF;

    v_receipt_lines := v_receipt_lines || jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_row.stock_item_id,
        'uom_id', v_row.uom_id,
        'qty', v_row.qty,
        'unit_cost', v_row.unit_cost,
        'currency', v_row.currency,
        'valuation_method', 'FIFO'
      )
    );
  END LOOP;

  v_entry := public.post_stock_receipt(
    v_grn.warehouse_id,
    COALESCE(v_grn.notes, 'GRN ' || v_grn.document_number),
    v_receipt_lines
  );

  FOR v_row IN
    SELECT grl.*, pol.unit_price AS po_unit_price
    FROM public.goods_receipt_lines grl
    JOIN public.purchase_order_lines pol ON pol.id = grl.purchase_order_line_id
    WHERE grl.goods_receipt_id = p_goods_receipt_id
    ORDER BY grl.created_at, grl.id
  LOOP
    UPDATE public.goods_receipt_lines grl
    SET
      stock_entry_line_id = (
        SELECT sel.id
        FROM public.stock_entry_lines sel
        WHERE sel.stock_entry_id = v_entry
          AND sel.stock_item_id = grl.stock_item_id
          AND sel.qty = grl.qty
          AND sel.id NOT IN (
            SELECT g2.stock_entry_line_id
            FROM public.goods_receipt_lines g2
            WHERE g2.goods_receipt_id = p_goods_receipt_id
              AND g2.stock_entry_line_id IS NOT NULL
          )
        ORDER BY sel.created_at
        LIMIT 1
      ),
      price_variance_pct = CASE
        WHEN v_row.po_unit_price > 0 THEN
          abs(grl.unit_cost - v_row.po_unit_price) / v_row.po_unit_price
        ELSE NULL
      END
    WHERE grl.id = v_row.id;

    UPDATE public.purchase_order_lines pol
    SET qty_received = qty_received + v_row.qty
    WHERE pol.id = v_row.purchase_order_line_id;
  END LOOP;

  UPDATE public.goods_receipts
  SET
    status = 'submitted',
    stock_entry_id = v_entry,
    submitted_at = now(),
    updated_at = now()
  WHERE id = p_goods_receipt_id;

  IF v_mismatch THEN
    PERFORM public.emit_domain_event(
      'supplier_mismatch',
      'goods_receipt:' || p_goods_receipt_id::text,
      jsonb_build_object(
        'goods_receipt_id', p_goods_receipt_id,
        'purchase_order_id', v_grn.purchase_order_id,
        'tolerance_pct', v_tolerance
      )
    );
  END IF;

  PERFORM public.emit_domain_event(
    'po_received',
    'goods_receipt:' || p_goods_receipt_id::text,
    jsonb_build_object(
      'goods_receipt_id', p_goods_receipt_id,
      'purchase_order_id', v_grn.purchase_order_id,
      'stock_entry_id', v_entry
    )
  );

  PERFORM public._procurement_end_rpc();
  RETURN p_goods_receipt_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_landed_cost_voucher(
  p_goods_receipt_id UUID,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC,
  p_charges JSONB,
  p_allocations JSONB,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_lcv UUID;
  v_rate NUMERIC;
  v_charge JSONB;
  v_alloc JSONB;
  v_batch UUID;
  v_prior NUMERIC;
  v_qty NUMERIC;
  v_total_charges NUMERIC := 0;
  v_total_alloc NUMERIC := 0;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_finance();

  IF p_charges IS NULL OR jsonb_array_length(p_charges) = 0 THEN
    RAISE EXCEPTION 'landed cost charges required';
  END IF;
  IF p_allocations IS NULL OR jsonb_array_length(p_allocations) = 0 THEN
    RAISE EXCEPTION 'landed cost allocations required';
  END IF;

  v_rate := CASE
    WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1)
    ELSE p_exchange_rate
  END;

  INSERT INTO public.landed_cost_vouchers (
    document_number, goods_receipt_id, currency, exchange_rate_applied, notes, created_by
  )
  VALUES (
    public.next_series_value('LCV-'),
    p_goods_receipt_id,
    p_currency,
    v_rate,
    p_notes,
    auth.uid()
  )
  RETURNING id INTO v_lcv;

  FOR v_charge IN SELECT * FROM jsonb_array_elements(p_charges)
  LOOP
    INSERT INTO public.landed_cost_charges (
      landed_cost_voucher_id, charge_type, amount, currency
    )
    VALUES (
      v_lcv,
      v_charge ->> 'charge_type',
      (v_charge ->> 'amount')::numeric,
      COALESCE((v_charge ->> 'currency')::public.currency_code, p_currency)
    );
    v_total_charges := v_total_charges + (v_charge ->> 'amount')::numeric;
  END LOOP;

  FOR v_alloc IN SELECT * FROM jsonb_array_elements(p_allocations)
  LOOP
    v_batch := (v_alloc ->> 'stock_batch_id')::uuid;
    SELECT unit_cost, qty_on_hand INTO v_prior, v_qty
    FROM public.stock_batches WHERE id = v_batch;
    IF NOT FOUND OR v_qty <= 0 THEN
      RAISE EXCEPTION 'invalid batch for allocation: %', v_batch;
    END IF;

    INSERT INTO public.landed_cost_allocations (
      landed_cost_voucher_id, stock_batch_id, allocated_amount,
      prior_unit_cost, qty_on_hand_snapshot
    )
    VALUES (
      v_lcv,
      v_batch,
      (v_alloc ->> 'allocated_amount')::numeric,
      v_prior,
      v_qty
    );
    v_total_alloc := v_total_alloc + (v_alloc ->> 'allocated_amount')::numeric;
  END LOOP;

  IF round(v_total_charges, 4) <> round(v_total_alloc, 4) THEN
    RAISE EXCEPTION 'charge total % must equal allocation total %', v_total_charges, v_total_alloc;
  END IF;

  PERFORM public._procurement_end_rpc();
  RETURN v_lcv;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_landed_cost_voucher(p_landed_cost_voucher_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_lcv public.landed_cost_vouchers%ROWTYPE;
  v_total NUMERIC := 0;
  v_row RECORD;
  v_journal UUID;
  v_delta NUMERIC;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_finance();
  PERFORM public._assert_procurement_period_open();

  SELECT * INTO v_lcv FROM public.landed_cost_vouchers WHERE id = p_landed_cost_voucher_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'landed cost voucher not found: %', p_landed_cost_voucher_id;
  END IF;
  IF v_lcv.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft landed cost vouchers can be submitted';
  END IF;

  SELECT COALESCE(SUM(amount), 0) INTO v_total
  FROM public.landed_cost_charges
  WHERE landed_cost_voucher_id = p_landed_cost_voucher_id;

  FOR v_row IN
    SELECT * FROM public.landed_cost_allocations
    WHERE landed_cost_voucher_id = p_landed_cost_voucher_id
  LOOP
    v_delta := v_row.allocated_amount / v_row.qty_on_hand_snapshot;
    UPDATE public.stock_batches
    SET unit_cost = round(v_row.prior_unit_cost + v_delta, 4)
    WHERE id = v_row.stock_batch_id;
  END LOOP;

  v_journal := public._post_journal_entry_inventory(
    CURRENT_DATE,
    format('Landed cost %s', v_lcv.document_number),
    v_lcv.currency,
    v_lcv.exchange_rate_applied,
    jsonb_build_array(
      jsonb_build_object(
        'account_code', '1300',
        'debit', v_total,
        'credit', 0,
        'currency', v_lcv.currency
      ),
      jsonb_build_object(
        'account_code', '2100',
        'debit', 0,
        'credit', v_total,
        'currency', v_lcv.currency
      )
    )
  );

  UPDATE public.landed_cost_vouchers
  SET
    status = 'submitted',
    journal_entry_id = v_journal,
    submitted_at = now(),
    updated_at = now()
  WHERE id = p_landed_cost_voucher_id;

  PERFORM public._procurement_end_rpc();
  RETURN p_landed_cost_voucher_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_landed_cost_voucher(
  p_landed_cost_voucher_id UUID,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_lcv public.landed_cost_vouchers%ROWTYPE;
  v_row RECORD;
  v_rev UUID;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_finance();
  PERFORM public._assert_procurement_period_open();

  SELECT * INTO v_lcv FROM public.landed_cost_vouchers WHERE id = p_landed_cost_voucher_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'landed cost voucher not found: %', p_landed_cost_voucher_id;
  END IF;
  IF v_lcv.status = 'cancelled' THEN
    PERFORM public._procurement_end_rpc();
    RETURN p_landed_cost_voucher_id;
  END IF;
  IF v_lcv.status <> 'submitted' THEN
    RAISE EXCEPTION 'only submitted landed cost vouchers can be cancelled';
  END IF;

  FOR v_row IN
    SELECT * FROM public.landed_cost_allocations
    WHERE landed_cost_voucher_id = p_landed_cost_voucher_id
  LOOP
    UPDATE public.stock_batches
    SET unit_cost = v_row.prior_unit_cost
    WHERE id = v_row.stock_batch_id;
  END LOOP;

  IF v_lcv.journal_entry_id IS NOT NULL THEN
    v_rev := public._reverse_journal_inventory(
      v_lcv.journal_entry_id,
      COALESCE(p_notes, format('Cancel %s', v_lcv.document_number))
    );
  END IF;

  UPDATE public.landed_cost_vouchers
  SET
    status = 'cancelled',
    reversal_journal_entry_id = v_rev,
    cancelled_at = now(),
    notes = COALESCE(p_notes, notes),
    updated_at = now()
  WHERE id = p_landed_cost_voucher_id;

  PERFORM public._procurement_end_rpc();
  RETURN p_landed_cost_voucher_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_blanket_purchase_order(
  p_supplier_id UUID,
  p_warehouse_id UUID,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC,
  p_blanket_max_value NUMERIC,
  p_lines JSONB,
  p_notes TEXT DEFAULT NULL,
  p_expected_date DATE DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_po UUID;
  v_line JSONB;
  v_no INT := 0;
  v_rate NUMERIC;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();

  IF p_blanket_max_value IS NULL OR p_blanket_max_value < 0 THEN
    RAISE EXCEPTION 'blanket_max_value required';
  END IF;
  IF p_lines IS NULL OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'blanket lines required';
  END IF;

  v_rate := CASE
    WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1)
    ELSE p_exchange_rate
  END;
  IF p_currency = 'ZIG' AND (v_rate IS NULL OR v_rate <= 0) THEN
    RAISE EXCEPTION 'positive exchange_rate required for ZIG';
  END IF;

  INSERT INTO public.purchase_orders (
    document_number, supplier_id, warehouse_id, currency, exchange_rate_applied,
    expected_date, notes, created_by, is_blanket, blanket_max_value
  )
  VALUES (
    public.next_series_value('BPO-'),
    p_supplier_id,
    p_warehouse_id,
    p_currency,
    v_rate,
    p_expected_date,
    p_notes,
    auth.uid(),
    true,
    p_blanket_max_value
  )
  RETURNING id INTO v_po;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_no := v_no + 1;
    INSERT INTO public.purchase_order_lines (
      purchase_order_id, line_no, stock_item_id, uom_id, qty_ordered, unit_price, currency
    )
    VALUES (
      v_po,
      v_no,
      (v_line ->> 'stock_item_id')::uuid,
      (v_line ->> 'uom_id')::uuid,
      (v_line ->> 'qty')::numeric,
      (v_line ->> 'unit_price')::numeric,
      COALESCE((v_line ->> 'currency')::public.currency_code, p_currency)
    );
  END LOOP;

  PERFORM public._procurement_end_rpc();
  RETURN v_po;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_blanket_release(
  p_blanket_purchase_order_id UUID,
  p_lines JSONB,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_blanket public.purchase_orders%ROWTYPE;
  v_release UUID;
  v_line JSONB;
  v_no INT := 0;
  v_parent_line public.purchase_order_lines%ROWTYPE;
  v_release_value NUMERIC := 0;
  v_total_release NUMERIC := 0;
  v_qty NUMERIC;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT * INTO v_blanket FROM public.purchase_orders WHERE id = p_blanket_purchase_order_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'blanket PO not found: %', p_blanket_purchase_order_id;
  END IF;
  IF NOT v_blanket.is_blanket THEN
    RAISE EXCEPTION 'purchase order is not a blanket contract';
  END IF;
  IF v_blanket.status <> 'submitted' THEN
    RAISE EXCEPTION 'blanket PO must be submitted before release';
  END IF;

  IF p_lines IS NULL OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'release lines required';
  END IF;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_qty := (v_line ->> 'qty')::numeric;
    IF v_qty IS NULL OR v_qty <= 0 THEN
      RAISE EXCEPTION 'release qty must be positive';
    END IF;

    SELECT * INTO v_parent_line
    FROM public.purchase_order_lines
    WHERE id = (v_line ->> 'blanket_line_id')::uuid
      AND purchase_order_id = p_blanket_purchase_order_id
    FOR UPDATE;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'blanket line not found: %', v_line ->> 'blanket_line_id';
    END IF;

    IF v_parent_line.qty_released + v_qty > v_parent_line.qty_ordered THEN
      RAISE EXCEPTION 'release exceeds remaining qty on line %', v_parent_line.line_no;
    END IF;

    v_release_value := v_qty * v_parent_line.unit_price;
    v_total_release := v_total_release + v_release_value;
  END LOOP;

  IF v_blanket.blanket_value_released + v_total_release > v_blanket.blanket_max_value THEN
    RAISE EXCEPTION 'release exceeds remaining blanket value';
  END IF;

  INSERT INTO public.purchase_orders (
    document_number, supplier_id, warehouse_id, currency, exchange_rate_applied,
    notes, created_by, blanket_parent_id
  )
  VALUES (
    public.next_series_value('PO-'),
    v_blanket.supplier_id,
    v_blanket.warehouse_id,
    v_blanket.currency,
    v_blanket.exchange_rate_applied,
    p_notes,
    auth.uid(),
    p_blanket_purchase_order_id
  )
  RETURNING id INTO v_release;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_qty := (v_line ->> 'qty')::numeric;

    SELECT * INTO v_parent_line
    FROM public.purchase_order_lines
    WHERE id = (v_line ->> 'blanket_line_id')::uuid
      AND purchase_order_id = p_blanket_purchase_order_id
    FOR UPDATE;

    v_no := v_no + 1;
    INSERT INTO public.purchase_order_lines (
      purchase_order_id, line_no, stock_item_id, uom_id,
      qty_ordered, unit_price, currency, blanket_parent_line_id
    )
    VALUES (
      v_release,
      v_no,
      v_parent_line.stock_item_id,
      v_parent_line.uom_id,
      v_qty,
      v_parent_line.unit_price,
      v_parent_line.currency,
      v_parent_line.id
    );

    UPDATE public.purchase_order_lines
    SET qty_released = qty_released + v_qty
    WHERE id = v_parent_line.id;
  END LOOP;

  UPDATE public.purchase_orders
  SET blanket_value_released = blanket_value_released + v_total_release, updated_at = now()
  WHERE id = p_blanket_purchase_order_id;

  PERFORM public._procurement_end_rpc();
  RETURN v_release;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_rfq(
  p_warehouse_id UUID,
  p_needed_by DATE,
  p_lines JSONB,
  p_supplier_ids UUID[],
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_rfq UUID;
  v_line JSONB;
  v_no INT := 0;
  v_sup UUID;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();

  IF p_lines IS NULL OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'RFQ lines required';
  END IF;
  IF p_supplier_ids IS NULL OR array_length(p_supplier_ids, 1) IS NULL THEN
    RAISE EXCEPTION 'at least one supplier invite required';
  END IF;

  INSERT INTO public.rfqs (document_number, warehouse_id, needed_by, notes, created_by)
  VALUES (public.next_series_value('RFQ-'), p_warehouse_id, p_needed_by, p_notes, auth.uid())
  RETURNING id INTO v_rfq;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_no := v_no + 1;
    INSERT INTO public.rfq_lines (rfq_id, line_no, stock_item_id, uom_id, qty)
    VALUES (
      v_rfq,
      v_no,
      (v_line ->> 'stock_item_id')::uuid,
      (v_line ->> 'uom_id')::uuid,
      (v_line ->> 'qty')::numeric
    );
  END LOOP;

  FOREACH v_sup IN ARRAY p_supplier_ids
  LOOP
    INSERT INTO public.rfq_suppliers (rfq_id, supplier_id)
    VALUES (v_rfq, v_sup);
  END LOOP;

  PERFORM public._procurement_end_rpc();
  RETURN v_rfq;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_rfq(p_rfq_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_st public.procurement_doc_status;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT status INTO v_st FROM public.rfqs WHERE id = p_rfq_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'RFQ not found: %', p_rfq_id;
  END IF;
  IF v_st <> 'draft' THEN
    RAISE EXCEPTION 'only draft RFQs can be submitted';
  END IF;

  UPDATE public.rfqs
  SET status = 'submitted', submitted_at = now(), updated_at = now()
  WHERE id = p_rfq_id;

  PERFORM public._procurement_end_rpc();
  RETURN p_rfq_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_rfq(p_rfq_id UUID, p_notes TEXT DEFAULT NULL)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_st public.procurement_doc_status;
  v_awarded UUID;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT status, awarded_quotation_id INTO v_st, v_awarded FROM public.rfqs WHERE id = p_rfq_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'RFQ not found: %', p_rfq_id;
  END IF;
  IF v_st = 'cancelled' THEN
    PERFORM public._procurement_end_rpc();
    RETURN p_rfq_id;
  END IF;
  IF v_awarded IS NOT NULL THEN
    RAISE EXCEPTION 'cannot cancel RFQ after award';
  END IF;

  UPDATE public.rfqs
  SET status = 'cancelled', cancelled_at = now(), notes = COALESCE(p_notes, notes), updated_at = now()
  WHERE id = p_rfq_id;

  PERFORM public._procurement_end_rpc();
  RETURN p_rfq_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.upsert_supplier_quotation(
  p_rfq_id UUID,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC,
  p_lines JSONB,
  p_valid_until DATE DEFAULT NULL,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_supplier UUID;
  v_rfq_st public.procurement_doc_status;
  v_sq UUID;
  v_st public.procurement_doc_status;
  v_line JSONB;
  v_no INT := 0;
  v_rate NUMERIC;
BEGIN
  PERFORM public._procurement_begin_rpc();
  v_supplier := public.current_supplier_id();
  IF v_supplier IS NULL THEN
    RAISE EXCEPTION 'supplier profile required';
  END IF;

  SELECT status INTO v_rfq_st FROM public.rfqs WHERE id = p_rfq_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'RFQ not found: %', p_rfq_id;
  END IF;
  IF v_rfq_st <> 'submitted' THEN
    RAISE EXCEPTION 'RFQ must be submitted before quoting';
  END IF;

  PERFORM public._assert_supplier_invited_to_rfq(p_rfq_id, v_supplier);

  IF p_lines IS NULL OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'quotation lines required';
  END IF;

  v_rate := CASE
    WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1)
    ELSE p_exchange_rate
  END;
  IF p_currency = 'ZIG' AND (v_rate IS NULL OR v_rate <= 0) THEN
    RAISE EXCEPTION 'positive exchange_rate required for ZIG';
  END IF;

  SELECT id, status INTO v_sq, v_st
  FROM public.supplier_quotations
  WHERE rfq_id = p_rfq_id AND supplier_id = v_supplier;

  IF v_sq IS NULL THEN
    INSERT INTO public.supplier_quotations (
      document_number, rfq_id, supplier_id, currency, exchange_rate_applied, valid_until, notes
    )
    VALUES (
      public.next_series_value('SQ-'), p_rfq_id, v_supplier, p_currency, v_rate, p_valid_until, p_notes
    )
    RETURNING id INTO v_sq;
  ELSE
    IF v_st <> 'draft' THEN
      RAISE EXCEPTION 'only draft quotations can be edited';
    END IF;
    UPDATE public.supplier_quotations
    SET
      currency = p_currency,
      exchange_rate_applied = v_rate,
      valid_until = p_valid_until,
      notes = COALESCE(p_notes, notes),
      updated_at = now()
    WHERE id = v_sq;
    DELETE FROM public.supplier_quotation_lines WHERE supplier_quotation_id = v_sq;
  END IF;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_no := v_no + 1;
    INSERT INTO public.supplier_quotation_lines (
      supplier_quotation_id, line_no, rfq_line_id, stock_item_id, uom_id, qty, unit_price, currency
    )
    VALUES (
      v_sq,
      v_no,
      NULLIF(v_line ->> 'rfq_line_id', '')::uuid,
      (v_line ->> 'stock_item_id')::uuid,
      (v_line ->> 'uom_id')::uuid,
      (v_line ->> 'qty')::numeric,
      (v_line ->> 'unit_price')::numeric,
      COALESCE((v_line ->> 'currency')::public.currency_code, p_currency)
    );
  END LOOP;

  PERFORM public._procurement_end_rpc();
  RETURN v_sq;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_supplier_quotation(p_supplier_quotation_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_sq public.supplier_quotations%ROWTYPE;
BEGIN
  PERFORM public._procurement_begin_rpc();
  SELECT * INTO v_sq FROM public.supplier_quotations WHERE id = p_supplier_quotation_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'quotation not found: %', p_supplier_quotation_id;
  END IF;

  IF v_sq.supplier_id <> public.current_supplier_id() AND auth.role() <> 'service_role' THEN
    RAISE EXCEPTION 'not your quotation';
  END IF;
  IF v_sq.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft quotations can be submitted';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.supplier_quotation_lines WHERE supplier_quotation_id = p_supplier_quotation_id
  ) THEN
    RAISE EXCEPTION 'quotation lines required';
  END IF;

  UPDATE public.supplier_quotations
  SET status = 'submitted', submitted_at = now(), updated_at = now()
  WHERE id = p_supplier_quotation_id;

  PERFORM public._procurement_end_rpc();
  RETURN p_supplier_quotation_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_supplier_quotation(
  p_supplier_quotation_id UUID,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_sq public.supplier_quotations%ROWTYPE;
BEGIN
  PERFORM public._procurement_begin_rpc();
  SELECT * INTO v_sq FROM public.supplier_quotations WHERE id = p_supplier_quotation_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'quotation not found: %', p_supplier_quotation_id;
  END IF;

  IF v_sq.status = 'cancelled' THEN
    PERFORM public._procurement_end_rpc();
    RETURN p_supplier_quotation_id;
  END IF;

  IF v_sq.supplier_id = public.current_supplier_id() THEN
    IF v_sq.status <> 'draft' THEN
      RAISE EXCEPTION 'suppliers can only cancel draft quotations';
    END IF;
  ELSE
    PERFORM public._require_procurement_staff();
    PERFORM public._assert_procurement_period_open();
  END IF;

  UPDATE public.supplier_quotations
  SET status = 'cancelled', cancelled_at = now(), notes = COALESCE(p_notes, notes), updated_at = now()
  WHERE id = p_supplier_quotation_id;

  PERFORM public._procurement_end_rpc();
  RETURN p_supplier_quotation_id;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.award_quotation_to_po(
  p_supplier_quotation_id UUID,
  p_notes TEXT DEFAULT NULL,
  p_expected_date DATE DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_sq public.supplier_quotations%ROWTYPE;
  v_rfq public.rfqs%ROWTYPE;
  v_po UUID;
  v_lines JSONB := '[]'::jsonb;
  v_row RECORD;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT * INTO v_sq FROM public.supplier_quotations WHERE id = p_supplier_quotation_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'quotation not found: %', p_supplier_quotation_id;
  END IF;
  IF v_sq.status <> 'submitted' THEN
    RAISE EXCEPTION 'quotation must be submitted before award';
  END IF;

  SELECT * INTO v_rfq FROM public.rfqs WHERE id = v_sq.rfq_id;
  IF v_rfq.status <> 'submitted' THEN
    RAISE EXCEPTION 'RFQ must be submitted';
  END IF;
  IF v_rfq.awarded_quotation_id IS NOT NULL THEN
    RAISE EXCEPTION 'RFQ already awarded';
  END IF;

  FOR v_row IN
    SELECT sql.*
    FROM public.supplier_quotation_lines sql
    WHERE sql.supplier_quotation_id = p_supplier_quotation_id
    ORDER BY sql.line_no
  LOOP
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'stock_item_id', v_row.stock_item_id,
        'uom_id', v_row.uom_id,
        'qty', v_row.qty,
        'unit_price', v_row.unit_price,
        'currency', v_row.currency
      )
    );
  END LOOP;

  v_po := public.create_purchase_order(
    v_sq.supplier_id,
    v_rfq.warehouse_id,
    v_sq.currency,
    v_sq.exchange_rate_applied,
    v_lines,
    COALESCE(p_notes, format('Awarded from %s', COALESCE(v_sq.document_number, v_sq.id::text))),
    p_expected_date,
    NULL
  );

  UPDATE public.purchase_orders
  SET rfq_id = v_rfq.id, awarded_quotation_id = p_supplier_quotation_id
  WHERE id = v_po;

  UPDATE public.rfqs
  SET awarded_quotation_id = p_supplier_quotation_id, updated_at = now()
  WHERE id = v_rfq.id;

  PERFORM public._procurement_end_rpc();
  RETURN v_po;
EXCEPTION
  WHEN OTHERS THEN
    PERFORM public._procurement_end_rpc();
    RAISE;
END;
$$;
