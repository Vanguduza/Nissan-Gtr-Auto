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

-- PATCH_MARKER_RPCS
