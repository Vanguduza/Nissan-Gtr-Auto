-- Phase 8b security: RFQ / quotation RPC-only writes (extends 20260724061000).

CREATE OR REPLACE FUNCTION public.guard_rfq_line_mutation()
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

  v_parent := COALESCE(NEW.rfq_id, OLD.rfq_id);
  SELECT status INTO v_status FROM public.rfqs WHERE id = v_parent;
  IF v_status IS NULL THEN
    RAISE EXCEPTION 'rfq_lines: parent not found';
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION 'rfq_lines: parent must be draft (status=%)', v_status;
  END IF;
  RAISE EXCEPTION 'rfq_lines: use procurement RPCs';
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_rfq_supplier_mutation()
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

  v_parent := COALESCE(NEW.rfq_id, OLD.rfq_id);
  SELECT status INTO v_status FROM public.rfqs WHERE id = v_parent;
  IF v_status IS NULL THEN
    RAISE EXCEPTION 'rfq_suppliers: parent not found';
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION 'rfq_suppliers: parent must be draft (status=%)', v_status;
  END IF;
  RAISE EXCEPTION 'rfq_suppliers: use procurement RPCs';
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_supplier_quotation_line_mutation()
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

  v_parent := COALESCE(NEW.supplier_quotation_id, OLD.supplier_quotation_id);
  SELECT status INTO v_status FROM public.supplier_quotations WHERE id = v_parent;
  IF v_status IS NULL THEN
    RAISE EXCEPTION 'supplier_quotation_lines: parent not found';
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION 'supplier_quotation_lines: parent must be draft (status=%)', v_status;
  END IF;
  RAISE EXCEPTION 'supplier_quotation_lines: use procurement RPCs';
END;
$$;

DROP TRIGGER IF EXISTS rfqs_mutation_guard ON public.rfqs;
CREATE TRIGGER rfqs_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.rfqs
  FOR EACH ROW EXECUTE PROCEDURE public.guard_procurement_doc_mutation();

DROP TRIGGER IF EXISTS rfq_lines_mutation_guard ON public.rfq_lines;
CREATE TRIGGER rfq_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.rfq_lines
  FOR EACH ROW EXECUTE PROCEDURE public.guard_rfq_line_mutation();

DROP TRIGGER IF EXISTS rfq_suppliers_mutation_guard ON public.rfq_suppliers;
CREATE TRIGGER rfq_suppliers_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.rfq_suppliers
  FOR EACH ROW EXECUTE PROCEDURE public.guard_rfq_supplier_mutation();

DROP TRIGGER IF EXISTS supplier_quotations_mutation_guard ON public.supplier_quotations;
CREATE TRIGGER supplier_quotations_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.supplier_quotations
  FOR EACH ROW EXECUTE PROCEDURE public.guard_procurement_doc_mutation();

DROP TRIGGER IF EXISTS supplier_quotation_lines_mutation_guard ON public.supplier_quotation_lines;
CREATE TRIGGER supplier_quotation_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.supplier_quotation_lines
  FOR EACH ROW EXECUTE PROCEDURE public.guard_supplier_quotation_line_mutation();

-- RPC-only writes: staff SELECT; supplier SELECT on own rows.
DROP POLICY IF EXISTS rfqs_staff ON public.rfqs;
CREATE POLICY rfqs_staff_select
  ON public.rfqs FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS rfq_lines_staff ON public.rfq_lines;
CREATE POLICY rfq_lines_staff_select
  ON public.rfq_lines FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS rfq_suppliers_staff ON public.rfq_suppliers;
CREATE POLICY rfq_suppliers_staff_select
  ON public.rfq_suppliers FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS supplier_quotations_staff ON public.supplier_quotations;
CREATE POLICY supplier_quotations_staff_select
  ON public.supplier_quotations FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS supplier_quotations_self ON public.supplier_quotations;
CREATE POLICY supplier_quotations_self_select
  ON public.supplier_quotations FOR SELECT TO authenticated
  USING (supplier_id = public.current_supplier_id());

DROP POLICY IF EXISTS supplier_quotation_lines_staff ON public.supplier_quotation_lines;
CREATE POLICY supplier_quotation_lines_staff_select
  ON public.supplier_quotation_lines FOR SELECT TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

DROP POLICY IF EXISTS supplier_quotation_lines_self ON public.supplier_quotation_lines;
CREATE POLICY supplier_quotation_lines_self_select
  ON public.supplier_quotation_lines FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.supplier_quotations sq
      WHERE sq.id = supplier_quotation_id
        AND sq.supplier_id = public.current_supplier_id()
    )
  );

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

  RETURN v_rfq;
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

  RETURN p_rfq_id;
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
    RETURN p_rfq_id;
  END IF;
  IF v_awarded IS NOT NULL THEN
    RAISE EXCEPTION 'cannot cancel RFQ after award';
  END IF;

  UPDATE public.rfqs
  SET status = 'cancelled', cancelled_at = now(), notes = COALESCE(p_notes, notes), updated_at = now()
  WHERE id = p_rfq_id;

  RETURN p_rfq_id;
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

  RETURN v_sq;
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

  RETURN p_supplier_quotation_id;
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

  RETURN p_supplier_quotation_id;
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

  RETURN v_po;
END;
$$;
