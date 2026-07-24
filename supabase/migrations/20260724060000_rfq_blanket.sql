-- Phase 8b: RFQ, supplier quotations, blanket POs + call-off releases
-- Exclusions: no ZIMRA / payroll tax; GRN path unchanged (release → child PO → existing GRN)

INSERT INTO public.naming_series (prefix, description, pad_length) VALUES
  ('RFQ-', 'Request for quotation', 5),
  ('SQ-', 'Supplier quotation', 5),
  ('BPO-', 'Blanket purchase order', 5)
ON CONFLICT (prefix) DO NOTHING;

-- ---------------------------------------------------------------------------
-- RFQ
-- ---------------------------------------------------------------------------
CREATE TABLE public.rfqs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  status public.procurement_doc_status NOT NULL DEFAULT 'draft',
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id),
  needed_by DATE,
  notes TEXT,
  awarded_quotation_id UUID,
  created_by UUID REFERENCES auth.users (id),
  submitted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.rfq_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rfq_id UUID NOT NULL REFERENCES public.rfqs (id) ON DELETE CASCADE,
  line_no INT NOT NULL,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id),
  uom_id UUID NOT NULL REFERENCES public.uoms (id),
  qty NUMERIC(18, 3) NOT NULL CHECK (qty > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (rfq_id, line_no)
);

CREATE INDEX rfq_lines_rfq_idx ON public.rfq_lines (rfq_id);

CREATE TABLE public.rfq_suppliers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rfq_id UUID NOT NULL REFERENCES public.rfqs (id) ON DELETE CASCADE,
  supplier_id UUID NOT NULL REFERENCES public.suppliers (id),
  invited_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (rfq_id, supplier_id)
);

CREATE INDEX rfq_suppliers_supplier_idx ON public.rfq_suppliers (supplier_id);

-- ---------------------------------------------------------------------------
-- Supplier quotations
-- ---------------------------------------------------------------------------
CREATE TABLE public.supplier_quotations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  status public.procurement_doc_status NOT NULL DEFAULT 'draft',
  rfq_id UUID NOT NULL REFERENCES public.rfqs (id),
  supplier_id UUID NOT NULL REFERENCES public.suppliers (id),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  valid_until DATE,
  notes TEXT,
  submitted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (rfq_id, supplier_id)
);

CREATE TABLE public.supplier_quotation_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  supplier_quotation_id UUID NOT NULL REFERENCES public.supplier_quotations (id) ON DELETE CASCADE,
  line_no INT NOT NULL,
  rfq_line_id UUID REFERENCES public.rfq_lines (id),
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id),
  uom_id UUID NOT NULL REFERENCES public.uoms (id),
  qty NUMERIC(18, 3) NOT NULL CHECK (qty > 0),
  unit_price NUMERIC(18, 4) NOT NULL CHECK (unit_price >= 0),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (supplier_quotation_id, line_no)
);

CREATE INDEX supplier_quotation_lines_sq_idx ON public.supplier_quotation_lines (supplier_quotation_id);

ALTER TABLE public.rfqs
  ADD CONSTRAINT rfqs_awarded_quotation_fk
  FOREIGN KEY (awarded_quotation_id) REFERENCES public.supplier_quotations (id);

-- ---------------------------------------------------------------------------
-- Blanket PO extensions (child releases reuse purchase_orders + PO- series)
-- ---------------------------------------------------------------------------
ALTER TABLE public.purchase_orders
  ADD COLUMN is_blanket BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN blanket_parent_id UUID REFERENCES public.purchase_orders (id),
  ADD COLUMN blanket_max_value NUMERIC(18, 4),
  ADD COLUMN blanket_value_released NUMERIC(18, 4) NOT NULL DEFAULT 0,
  ADD COLUMN rfq_id UUID REFERENCES public.rfqs (id),
  ADD COLUMN awarded_quotation_id UUID REFERENCES public.supplier_quotations (id),
  ADD CONSTRAINT po_blanket_max_value_chk CHECK (
    (is_blanket AND blanket_max_value IS NOT NULL AND blanket_max_value >= 0)
    OR (NOT is_blanket AND blanket_max_value IS NULL)
  ),
  ADD CONSTRAINT po_blanket_value_released_chk CHECK (blanket_value_released >= 0),
  ADD CONSTRAINT po_blanket_parent_not_self CHECK (blanket_parent_id IS DISTINCT FROM id);

ALTER TABLE public.purchase_order_lines
  ADD COLUMN qty_released NUMERIC(18, 3) NOT NULL DEFAULT 0 CHECK (qty_released >= 0),
  ADD COLUMN blanket_parent_line_id UUID REFERENCES public.purchase_order_lines (id),
  ADD CONSTRAINT po_line_released_le_ordered CHECK (qty_released <= qty_ordered);

CREATE INDEX purchase_orders_blanket_parent_idx
  ON public.purchase_orders (blanket_parent_id)
  WHERE blanket_parent_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- RFQ RPCs (staff)
-- ---------------------------------------------------------------------------
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
BEGIN
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT status INTO v_st FROM public.rfqs WHERE id = p_rfq_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'RFQ not found: %', p_rfq_id;
  END IF;
  IF v_st = 'cancelled' THEN
    RETURN p_rfq_id;
  END IF;
  IF v_st = 'submitted' AND awarded_quotation_id IS NOT NULL FROM public.rfqs WHERE id = p_rfq_id THEN
    RAISE EXCEPTION 'cannot cancel RFQ after award';
  END IF;

  UPDATE public.rfqs
  SET status = 'cancelled', cancelled_at = now(), notes = COALESCE(p_notes, notes), updated_at = now()
  WHERE id = p_rfq_id;

  RETURN p_rfq_id;
END;
$$;

-- Fix cancel_rfq syntax - the IF check was wrong
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

-- ---------------------------------------------------------------------------
-- Supplier quotation RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._assert_supplier_invited_to_rfq(p_rfq_id UUID, p_supplier_id UUID)
RETURNS void
LANGUAGE plpgsql
STABLE
SET search_path = public
AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.rfq_suppliers rs
    WHERE rs.rfq_id = p_rfq_id AND rs.supplier_id = p_supplier_id
  ) THEN
    RAISE EXCEPTION 'supplier not invited to RFQ';
  END IF;
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

-- ---------------------------------------------------------------------------
-- Award winner → PO
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- Blanket PO + releases
-- ---------------------------------------------------------------------------
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

    v_release_value := v_qty * v_parent_line.unit_price;
  END LOOP;

  UPDATE public.purchase_orders
  SET blanket_value_released = blanket_value_released + v_total_release, updated_at = now()
  WHERE id = p_blanket_purchase_order_id;

  RETURN v_release;
END;
$$;

-- Restore blanket remaining when a release PO is cancelled (no receipts)
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

      SELECT qty_ordered * unit_price INTO v_restore_value
      FROM public.purchase_order_lines WHERE id = v_rel.blanket_parent_line_id;

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

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.rfqs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rfq_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rfq_suppliers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.supplier_quotations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.supplier_quotation_lines ENABLE ROW LEVEL SECURITY;

CREATE POLICY rfqs_staff
  ON public.rfqs FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY rfqs_supplier_select
  ON public.rfqs FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.rfq_suppliers rs
      WHERE rs.rfq_id = id
        AND rs.supplier_id = public.current_supplier_id()
    )
  );

CREATE POLICY rfq_lines_staff
  ON public.rfq_lines FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY rfq_lines_supplier_select
  ON public.rfq_lines FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.rfq_suppliers rs
      WHERE rs.rfq_id = rfq_id
        AND rs.supplier_id = public.current_supplier_id()
    )
  );

CREATE POLICY rfq_suppliers_staff
  ON public.rfq_suppliers FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY rfq_suppliers_self_select
  ON public.rfq_suppliers FOR SELECT TO authenticated
  USING (supplier_id = public.current_supplier_id());

CREATE POLICY supplier_quotations_staff
  ON public.supplier_quotations FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY supplier_quotations_self
  ON public.supplier_quotations FOR ALL TO authenticated
  USING (supplier_id = public.current_supplier_id())
  WITH CHECK (supplier_id = public.current_supplier_id());

CREATE POLICY supplier_quotation_lines_staff
  ON public.supplier_quotation_lines FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY supplier_quotation_lines_self
  ON public.supplier_quotation_lines FOR ALL TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.supplier_quotations sq
      WHERE sq.id = supplier_quotation_id
        AND sq.supplier_id = public.current_supplier_id()
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.supplier_quotations sq
      WHERE sq.id = supplier_quotation_id
        AND sq.supplier_id = public.current_supplier_id()
    )
  );

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON public.rfqs TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.rfq_lines TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.rfq_suppliers TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.supplier_quotations TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.supplier_quotation_lines TO authenticated, service_role;

REVOKE ALL ON FUNCTION public._assert_supplier_invited_to_rfq(UUID, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_rfq(UUID, DATE, JSONB, UUID[], TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_rfq(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cancel_rfq(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.upsert_supplier_quotation(UUID, public.currency_code, NUMERIC, JSONB, DATE, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_supplier_quotation(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cancel_supplier_quotation(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.award_quotation_to_po(UUID, TEXT, DATE) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_blanket_purchase_order(UUID, UUID, public.currency_code, NUMERIC, NUMERIC, JSONB, TEXT, DATE) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_blanket_release(UUID, JSONB, TEXT) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.create_rfq(UUID, DATE, JSONB, UUID[], TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_rfq(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cancel_rfq(UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.upsert_supplier_quotation(UUID, public.currency_code, NUMERIC, JSONB, DATE, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_supplier_quotation(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cancel_supplier_quotation(UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.award_quotation_to_po(UUID, TEXT, DATE) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_blanket_purchase_order(UUID, UUID, public.currency_code, NUMERIC, NUMERIC, JSONB, TEXT, DATE) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_blanket_release(UUID, JSONB, TEXT) TO authenticated, service_role;

-- @web_agent follow-on: RFQ compare UI; supplier quote via upsert_supplier_quotation + submit;
-- blanket remaining = qty_ordered - qty_released per line; header blanket_max_value - blanket_value_released
-- Smoke: supabase/tests/phase8b_rfq_blanket_smoke.sql
