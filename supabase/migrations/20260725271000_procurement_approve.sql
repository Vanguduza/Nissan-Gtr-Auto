-- Phase 1: PO / MR approve + reject RPCs; gate GRN / blanket release / MR→PO on approved.
-- Plan: docs/plans/2026-07-25-professional-finance-workflows.md
-- Mutations via SECURITY DEFINER RPCs + existing procurement mutation guards.
-- Exclusions: no ZIMRA / payroll tax; journals unchanged (append-only).

-- ---------------------------------------------------------------------------
-- Audit columns (status remains procurement_doc_status)
-- ---------------------------------------------------------------------------
ALTER TABLE public.purchase_orders
  ADD COLUMN IF NOT EXISTS approved_by UUID REFERENCES auth.users (id),
  ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS rejected_by UUID REFERENCES auth.users (id),
  ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

ALTER TABLE public.material_requests
  ADD COLUMN IF NOT EXISTS approved_by UUID REFERENCES auth.users (id),
  ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS rejected_by UUID REFERENCES auth.users (id),
  ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

COMMENT ON COLUMN public.purchase_orders.approved_at IS
  'Set by approve_purchase_order; required before GRN / blanket release.';
COMMENT ON COLUMN public.material_requests.approved_at IS
  'Set by approve_material_request; required before convert_material_request_to_po.';

-- ---------------------------------------------------------------------------
-- Guard: rejected docs are immutable (mirror cancelled)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.guard_procurement_doc_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._procurement_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.status IS DISTINCT FROM 'draft' THEN
      RAISE EXCEPTION '%: new rows must be draft; use procurement RPCs', TG_TABLE_NAME;
    END IF;
    RAISE EXCEPTION '%: use procurement RPCs for insert', TG_TABLE_NAME;
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION '%: direct delete not allowed', TG_TABLE_NAME;
  ELSIF TG_OP = 'UPDATE' THEN
    IF OLD.status IN ('cancelled', 'rejected') THEN
      RAISE EXCEPTION '%: % records are immutable', TG_TABLE_NAME, OLD.status;
    END IF;
    RAISE EXCEPTION '%: use procurement RPCs for updates', TG_TABLE_NAME;
  END IF;

  RETURN NULL;
END;
$$;

-- ---------------------------------------------------------------------------
-- Approve / reject purchase orders (finance|admin)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.approve_purchase_order(p_purchase_order_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_po public.purchase_orders%ROWTYPE;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_finance();
  PERFORM public._assert_procurement_period_open();

  SELECT * INTO v_po
  FROM public.purchase_orders
  WHERE id = p_purchase_order_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'purchase order not found: %', p_purchase_order_id;
  END IF;
  IF v_po.status <> 'submitted' THEN
    RAISE EXCEPTION 'only submitted purchase orders can be approved (status=%)', v_po.status;
  END IF;

  UPDATE public.purchase_orders
  SET
    status = 'approved',
    approved_by = auth.uid(),
    approved_at = now(),
    rejected_by = NULL,
    rejected_at = NULL,
    rejection_reason = NULL,
    updated_at = now()
  WHERE id = p_purchase_order_id;

  PERFORM public.emit_domain_event(
    'po_approved',
    'purchase_order_approved:' || p_purchase_order_id::text,
    jsonb_build_object(
      'purchase_order_id', p_purchase_order_id,
      'document_number', v_po.document_number,
      'currency', v_po.currency,
      'supplier_id', v_po.supplier_id
    ),
    auth.uid(),
    format(
      'GTR Auto: PO %s approved',
      COALESCE(v_po.document_number, left(p_purchase_order_id::text, 8))
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

CREATE OR REPLACE FUNCTION public.reject_purchase_order(
  p_purchase_order_id UUID,
  p_reason TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_po public.purchase_orders%ROWTYPE;
  v_recv NUMERIC;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_finance();
  PERFORM public._assert_procurement_period_open();

  SELECT * INTO v_po
  FROM public.purchase_orders
  WHERE id = p_purchase_order_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'purchase order not found: %', p_purchase_order_id;
  END IF;
  IF v_po.status NOT IN ('submitted', 'approved') THEN
    RAISE EXCEPTION 'only submitted/approved purchase orders can be rejected (status=%)', v_po.status;
  END IF;

  SELECT COALESCE(SUM(qty_received), 0) INTO v_recv
  FROM public.purchase_order_lines
  WHERE purchase_order_id = p_purchase_order_id;

  IF v_recv > 0 THEN
    RAISE EXCEPTION 'cannot reject PO with receipts';
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.goods_receipts gr
    WHERE gr.purchase_order_id = p_purchase_order_id
      AND gr.status = 'submitted'
  ) THEN
    RAISE EXCEPTION 'cannot reject PO with submitted GRNs';
  END IF;

  UPDATE public.purchase_orders
  SET
    status = 'rejected',
    rejected_by = auth.uid(),
    rejected_at = now(),
    rejection_reason = nullif(trim(p_reason), ''),
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

-- ---------------------------------------------------------------------------
-- Approve / reject material requests (finance|admin)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.approve_material_request(p_material_request_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_st public.procurement_doc_status;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_finance();
  PERFORM public._assert_procurement_period_open();

  SELECT status INTO v_st
  FROM public.material_requests
  WHERE id = p_material_request_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'material request not found: %', p_material_request_id;
  END IF;
  IF v_st <> 'submitted' THEN
    RAISE EXCEPTION 'only submitted material requests can be approved (status=%)', v_st;
  END IF;

  UPDATE public.material_requests
  SET
    status = 'approved',
    approved_by = auth.uid(),
    approved_at = now(),
    rejected_by = NULL,
    rejected_at = NULL,
    rejection_reason = NULL,
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

CREATE OR REPLACE FUNCTION public.reject_material_request(
  p_material_request_id UUID,
  p_reason TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_st public.procurement_doc_status;
  v_converted NUMERIC;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_finance();
  PERFORM public._assert_procurement_period_open();

  SELECT status INTO v_st
  FROM public.material_requests
  WHERE id = p_material_request_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'material request not found: %', p_material_request_id;
  END IF;
  IF v_st NOT IN ('submitted', 'approved') THEN
    RAISE EXCEPTION 'only submitted/approved material requests can be rejected (status=%)', v_st;
  END IF;

  SELECT COALESCE(SUM(qty_converted), 0) INTO v_converted
  FROM public.material_request_lines
  WHERE material_request_id = p_material_request_id;

  IF v_converted > 0 THEN
    RAISE EXCEPTION 'cannot reject MR with converted lines';
  END IF;

  UPDATE public.material_requests
  SET
    status = 'rejected',
    rejected_by = auth.uid(),
    rejected_at = now(),
    rejection_reason = nullif(trim(p_reason), ''),
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

-- ---------------------------------------------------------------------------
-- Downstream gates: approved required
-- ---------------------------------------------------------------------------
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
  IF v_po.status <> 'approved' THEN
    RAISE EXCEPTION 'purchase order must be approved before GRN (status=%)', v_po.status;
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
  IF v_mr.status <> 'approved' THEN
    RAISE EXCEPTION 'material request must be approved before conversion (status=%)', v_mr.status;
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
  IF v_blanket.status <> 'approved' THEN
    RAISE EXCEPTION 'blanket PO must be approved before release (status=%)', v_blanket.status;
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

-- Allow cancel of rejected? treat as no-op success when already rejected/cancelled
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
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT * INTO v_po FROM public.purchase_orders WHERE id = p_purchase_order_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'purchase order not found: %', p_purchase_order_id;
  END IF;
  v_st := v_po.status;

  IF v_st IN ('cancelled', 'rejected') THEN
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
  v_total NUMERIC;
BEGIN
  PERFORM public._procurement_begin_rpc();
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT status INTO v_st FROM public.material_requests WHERE id = p_material_request_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'material request not found: %', p_material_request_id;
  END IF;
  IF v_st IN ('cancelled', 'rejected') THEN
    RETURN p_material_request_id;
  END IF;

  SELECT
    COALESCE(SUM(qty - qty_converted), 0),
    COALESCE(SUM(qty), 0)
  INTO v_open, v_total
  FROM public.material_request_lines
  WHERE material_request_id = p_material_request_id;

  IF v_st IN ('submitted', 'approved') AND v_open < v_total THEN
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

-- ---------------------------------------------------------------------------
-- Grants (RLS already on purchase_orders / material_requests; mutations via RPC)
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.approve_purchase_order(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.reject_purchase_order(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.approve_material_request(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.reject_material_request(UUID, TEXT) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.approve_purchase_order(UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.reject_purchase_order(UUID, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.approve_material_request(UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.reject_material_request(UUID, TEXT)
  TO authenticated, service_role;
