-- Phase 8c: procurement mutation guards (mirror Phase 4b / 5b).
-- SECURITY DEFINER RPCs set app.procurement_rpc=1 (transaction-local) before table writes.

CREATE OR REPLACE FUNCTION public._procurement_rpc_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.procurement_rpc', true), '') = '1';
$$;

CREATE OR REPLACE FUNCTION public._procurement_begin_rpc()
RETURNS void
LANGUAGE sql
AS $$
  SELECT set_config('app.procurement_rpc', '1', true);
$$;

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
    IF OLD.status = 'cancelled' THEN
      RAISE EXCEPTION '%: cancelled records are immutable', TG_TABLE_NAME;
    END IF;
    RAISE EXCEPTION '%: use procurement RPCs for updates', TG_TABLE_NAME;
  END IF;

  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_material_request_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_parent UUID;
  v_status public.procurement_doc_status;
BEGIN
  IF public._procurement_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  v_parent := COALESCE(NEW.material_request_id, OLD.material_request_id);
  SELECT status INTO v_status FROM public.material_requests WHERE id = v_parent;
  IF v_status IS NULL THEN
    RAISE EXCEPTION 'material_request_lines: parent not found';
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION 'material_request_lines: parent must be draft (status=%)', v_status;
  END IF;
  RAISE EXCEPTION 'material_request_lines: use procurement RPCs';
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_purchase_order_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_parent UUID;
  v_status public.procurement_doc_status;
BEGIN
  IF public._procurement_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  v_parent := COALESCE(NEW.purchase_order_id, OLD.purchase_order_id);
  SELECT status INTO v_status FROM public.purchase_orders WHERE id = v_parent;
  IF v_status IS NULL THEN
    RAISE EXCEPTION 'purchase_order_lines: parent not found';
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION 'purchase_order_lines: parent must be draft (status=%)', v_status;
  END IF;
  RAISE EXCEPTION 'purchase_order_lines: use procurement RPCs';
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_goods_receipt_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_parent UUID;
  v_status public.procurement_doc_status;
BEGIN
  IF public._procurement_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  v_parent := COALESCE(NEW.goods_receipt_id, OLD.goods_receipt_id);
  SELECT status INTO v_status FROM public.goods_receipts WHERE id = v_parent;
  IF v_status IS NULL THEN
    RAISE EXCEPTION 'goods_receipt_lines: parent not found';
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION 'goods_receipt_lines: parent must be draft (status=%)', v_status;
  END IF;
  RAISE EXCEPTION 'goods_receipt_lines: use procurement RPCs';
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_landed_cost_child_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_parent UUID;
  v_status public.procurement_doc_status;
BEGIN
  IF public._procurement_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  v_parent := COALESCE(NEW.landed_cost_voucher_id, OLD.landed_cost_voucher_id);
  SELECT status INTO v_status FROM public.landed_cost_vouchers WHERE id = v_parent;
  IF v_status IS NULL THEN
    RAISE EXCEPTION '%: parent voucher not found', TG_TABLE_NAME;
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION '%: parent must be draft (status=%)', TG_TABLE_NAME, v_status;
  END IF;
  RAISE EXCEPTION '%: use procurement RPCs', TG_TABLE_NAME;
END;
$$;

DROP TRIGGER IF EXISTS material_requests_mutation_guard ON public.material_requests;
CREATE TRIGGER material_requests_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.material_requests
  FOR EACH ROW EXECUTE PROCEDURE public.guard_procurement_doc_mutation();

DROP TRIGGER IF EXISTS material_request_lines_mutation_guard ON public.material_request_lines;
CREATE TRIGGER material_request_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.material_request_lines
  FOR EACH ROW EXECUTE PROCEDURE public.guard_material_request_line_mutation();

DROP TRIGGER IF EXISTS purchase_orders_mutation_guard ON public.purchase_orders;
CREATE TRIGGER purchase_orders_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.purchase_orders
  FOR EACH ROW EXECUTE PROCEDURE public.guard_procurement_doc_mutation();

DROP TRIGGER IF EXISTS purchase_order_lines_mutation_guard ON public.purchase_order_lines;
CREATE TRIGGER purchase_order_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.purchase_order_lines
  FOR EACH ROW EXECUTE PROCEDURE public.guard_purchase_order_line_mutation();

DROP TRIGGER IF EXISTS goods_receipts_mutation_guard ON public.goods_receipts;
CREATE TRIGGER goods_receipts_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.goods_receipts
  FOR EACH ROW EXECUTE PROCEDURE public.guard_procurement_doc_mutation();

DROP TRIGGER IF EXISTS goods_receipt_lines_mutation_guard ON public.goods_receipt_lines;
CREATE TRIGGER goods_receipt_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.goods_receipt_lines
  FOR EACH ROW EXECUTE PROCEDURE public.guard_goods_receipt_line_mutation();

DROP TRIGGER IF EXISTS landed_cost_vouchers_mutation_guard ON public.landed_cost_vouchers;
CREATE TRIGGER landed_cost_vouchers_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.landed_cost_vouchers
  FOR EACH ROW EXECUTE PROCEDURE public.guard_procurement_doc_mutation();

DROP TRIGGER IF EXISTS landed_cost_charges_mutation_guard ON public.landed_cost_charges;
CREATE TRIGGER landed_cost_charges_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.landed_cost_charges
  FOR EACH ROW EXECUTE PROCEDURE public.guard_landed_cost_child_mutation();

DROP TRIGGER IF EXISTS landed_cost_allocations_mutation_guard ON public.landed_cost_allocations;
CREATE TRIGGER landed_cost_allocations_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.landed_cost_allocations
  FOR EACH ROW EXECUTE PROCEDURE public.guard_landed_cost_child_mutation();

-- RPC-only writes: keep staff/supplier SELECT; drop direct DML policies.
DROP POLICY IF EXISTS material_requests_staff ON public.material_requests;
CREATE POLICY material_requests_staff_select
  ON public.material_requests FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS material_request_lines_staff ON public.material_request_lines;
CREATE POLICY material_request_lines_staff_select
  ON public.material_request_lines FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS purchase_orders_staff ON public.purchase_orders;
CREATE POLICY purchase_orders_staff_select
  ON public.purchase_orders FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS purchase_order_lines_staff ON public.purchase_order_lines;
CREATE POLICY purchase_order_lines_staff_select
  ON public.purchase_order_lines FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS goods_receipts_staff ON public.goods_receipts;
CREATE POLICY goods_receipts_staff_select
  ON public.goods_receipts FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS goods_receipt_lines_staff ON public.goods_receipt_lines;
CREATE POLICY goods_receipt_lines_staff_select
  ON public.goods_receipt_lines FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS landed_cost_staff ON public.landed_cost_vouchers;
CREATE POLICY landed_cost_staff_select
  ON public.landed_cost_vouchers FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS landed_cost_charges_staff ON public.landed_cost_charges;
CREATE POLICY landed_cost_charges_staff_select
  ON public.landed_cost_charges FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS landed_cost_allocations_staff ON public.landed_cost_allocations;
CREATE POLICY landed_cost_allocations_staff_select
  ON public.landed_cost_allocations FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  );

REVOKE ALL ON FUNCTION public._procurement_rpc_active() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._procurement_begin_rpc() FROM PUBLIC;

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

  RETURN v_mr;
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

  RETURN p_material_request_id;
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

  RETURN p_material_request_id;
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

  RETURN v_po;
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

  RETURN p_purchase_order_id;
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

  RETURN p_purchase_order_id;
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

  RETURN v_po;
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

  RETURN v_grn;
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
    RETURN p_goods_receipt_id;
  END IF;
  IF v_st <> 'draft' THEN
    RAISE EXCEPTION 'submitted GRNs are immutable; reverse stock separately';
  END IF;

  UPDATE public.goods_receipts
  SET status = 'cancelled', cancelled_at = now(), updated_at = now()
  WHERE id = p_goods_receipt_id;

  RETURN p_goods_receipt_id;
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

  RETURN p_goods_receipt_id;
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

  RETURN v_lcv;
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

  RETURN p_landed_cost_voucher_id;
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

  RETURN p_landed_cost_voucher_id;
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

  RETURN v_po;
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

  RETURN v_release;
END;
$$;

-- PATCH_MARKER_RPCS
