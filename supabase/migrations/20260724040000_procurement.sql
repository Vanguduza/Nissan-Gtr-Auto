-- Phase 8: suppliers, PO, GRN, material requests, landed cost vouchers
-- Exclusions: no ZIMRA / payroll tax; GRN reuses post_stock_receipt (Phase 4 QR unchanged)

CREATE TYPE public.procurement_doc_status AS ENUM ('draft', 'submitted', 'cancelled');

-- ---------------------------------------------------------------------------
-- Suppliers
-- ---------------------------------------------------------------------------
CREATE TABLE public.suppliers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID UNIQUE REFERENCES public.profiles (id) ON DELETE SET NULL,
  code VARCHAR(32) NOT NULL UNIQUE,
  name TEXT NOT NULL,
  email TEXT,
  phone_e164 TEXT,
  default_currency public.currency_code NOT NULL DEFAULT 'USD',
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX suppliers_profile_idx ON public.suppliers (profile_id) WHERE profile_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Material requests
-- ---------------------------------------------------------------------------
CREATE TABLE public.material_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  status public.procurement_doc_status NOT NULL DEFAULT 'draft',
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id),
  needed_by DATE,
  notes TEXT,
  created_by UUID REFERENCES auth.users (id),
  submitted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.material_request_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  material_request_id UUID NOT NULL REFERENCES public.material_requests (id) ON DELETE CASCADE,
  line_no INT NOT NULL,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id),
  uom_id UUID NOT NULL REFERENCES public.uoms (id),
  qty NUMERIC(18, 3) NOT NULL CHECK (qty > 0),
  qty_converted NUMERIC(18, 3) NOT NULL DEFAULT 0 CHECK (qty_converted >= 0),
  purchase_order_line_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (material_request_id, line_no),
  CONSTRAINT mr_line_converted_le_qty CHECK (qty_converted <= qty)
);

CREATE INDEX material_request_lines_mr_idx ON public.material_request_lines (material_request_id);

-- ---------------------------------------------------------------------------
-- Purchase orders
-- ---------------------------------------------------------------------------
CREATE TABLE public.purchase_orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  status public.procurement_doc_status NOT NULL DEFAULT 'draft',
  supplier_id UUID NOT NULL REFERENCES public.suppliers (id),
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id),
  material_request_id UUID REFERENCES public.material_requests (id),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  order_date DATE NOT NULL DEFAULT CURRENT_DATE,
  expected_date DATE,
  notes TEXT,
  created_by UUID REFERENCES auth.users (id),
  submitted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.purchase_order_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  purchase_order_id UUID NOT NULL REFERENCES public.purchase_orders (id) ON DELETE CASCADE,
  line_no INT NOT NULL,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id),
  uom_id UUID NOT NULL REFERENCES public.uoms (id),
  qty_ordered NUMERIC(18, 3) NOT NULL CHECK (qty_ordered > 0),
  qty_received NUMERIC(18, 3) NOT NULL DEFAULT 0 CHECK (qty_received >= 0),
  unit_price NUMERIC(18, 4) NOT NULL CHECK (unit_price >= 0),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  material_request_line_id UUID REFERENCES public.material_request_lines (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (purchase_order_id, line_no),
  CONSTRAINT po_line_received_le_ordered CHECK (qty_received <= qty_ordered)
);

CREATE INDEX purchase_order_lines_po_idx ON public.purchase_order_lines (purchase_order_id);

ALTER TABLE public.material_request_lines
  ADD CONSTRAINT material_request_lines_po_line_fk
  FOREIGN KEY (purchase_order_line_id) REFERENCES public.purchase_order_lines (id);

-- ---------------------------------------------------------------------------
-- Goods receipts (GRN)
-- ---------------------------------------------------------------------------
CREATE TABLE public.goods_receipts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  status public.procurement_doc_status NOT NULL DEFAULT 'draft',
  purchase_order_id UUID NOT NULL REFERENCES public.purchase_orders (id),
  supplier_id UUID NOT NULL REFERENCES public.suppliers (id),
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id),
  stock_entry_id UUID REFERENCES public.stock_entries (id),
  notes TEXT,
  created_by UUID REFERENCES auth.users (id),
  submitted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.goods_receipt_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  goods_receipt_id UUID NOT NULL REFERENCES public.goods_receipts (id) ON DELETE CASCADE,
  purchase_order_line_id UUID NOT NULL REFERENCES public.purchase_order_lines (id),
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id),
  uom_id UUID NOT NULL REFERENCES public.uoms (id),
  qty NUMERIC(18, 3) NOT NULL CHECK (qty > 0),
  unit_cost NUMERIC(18, 4) NOT NULL CHECK (unit_cost >= 0),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  stock_entry_line_id UUID REFERENCES public.stock_entry_lines (id),
  price_variance_pct NUMERIC(9, 4),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX goods_receipt_lines_grn_idx ON public.goods_receipt_lines (goods_receipt_id);

-- ---------------------------------------------------------------------------
-- Landed cost vouchers
-- ---------------------------------------------------------------------------
CREATE TABLE public.landed_cost_vouchers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT,
  status public.procurement_doc_status NOT NULL DEFAULT 'draft',
  goods_receipt_id UUID REFERENCES public.goods_receipts (id),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  journal_entry_id UUID REFERENCES public.journal_entries (id),
  reversal_journal_entry_id UUID REFERENCES public.journal_entries (id),
  notes TEXT,
  created_by UUID REFERENCES auth.users (id),
  submitted_at TIMESTAMPTZ,
  cancelled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.landed_cost_charges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  landed_cost_voucher_id UUID NOT NULL REFERENCES public.landed_cost_vouchers (id) ON DELETE CASCADE,
  charge_type TEXT NOT NULL CHECK (charge_type IN ('freight', 'duty', 'other')),
  amount NUMERIC(18, 4) NOT NULL CHECK (amount > 0),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.landed_cost_allocations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  landed_cost_voucher_id UUID NOT NULL REFERENCES public.landed_cost_vouchers (id) ON DELETE CASCADE,
  stock_batch_id UUID NOT NULL REFERENCES public.stock_batches (id),
  allocated_amount NUMERIC(18, 4) NOT NULL CHECK (allocated_amount > 0),
  prior_unit_cost NUMERIC(18, 4) NOT NULL,
  qty_on_hand_snapshot NUMERIC(18, 3) NOT NULL CHECK (qty_on_hand_snapshot > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX landed_cost_allocations_lcv_idx ON public.landed_cost_allocations (landed_cost_voucher_id);

INSERT INTO public.naming_series (prefix, description, pad_length) VALUES
  ('PO-', 'Purchase order', 5),
  ('MR-', 'Material request', 5),
  ('GRN-', 'Goods receipt', 5),
  ('LCV-', 'Landed cost voucher', 5)
ON CONFLICT (prefix) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Auth helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.current_supplier_id()
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT s.id
  FROM public.suppliers s
  WHERE s.profile_id = auth.uid()
    AND s.is_active
  LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public._require_procurement_staff()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin, warehouse, or finance role required';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public._require_procurement_finance()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin or finance role required';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public._assert_procurement_period_open()
RETURNS void
LANGUAGE plpgsql
STABLE
SET search_path = public
AS $$
BEGIN
  IF public.is_period_locked(CURRENT_DATE) THEN
    RAISE EXCEPTION 'accounting period is locked for today';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Supplier admin RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_supplier(
  p_code TEXT,
  p_name TEXT,
  p_email TEXT DEFAULT NULL,
  p_phone_e164 TEXT DEFAULT NULL,
  p_default_currency public.currency_code DEFAULT 'USD'
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  IF auth.role() = 'authenticated'
     AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin role required';
  END IF;

  INSERT INTO public.suppliers (code, name, email, phone_e164, default_currency)
  VALUES (upper(trim(p_code)), trim(p_name), p_email, p_phone_e164, p_default_currency)
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.link_supplier_profile(
  p_supplier_id UUID,
  p_profile_id UUID
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.role() = 'authenticated'
     AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin role required';
  END IF;

  UPDATE public.suppliers
  SET profile_id = p_profile_id, updated_at = now()
  WHERE id = p_supplier_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'supplier not found: %', p_supplier_id;
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Material request RPCs
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- Purchase order RPCs
-- ---------------------------------------------------------------------------
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
BEGIN
  PERFORM public._require_procurement_staff();
  PERFORM public._assert_procurement_period_open();

  SELECT status INTO v_st FROM public.purchase_orders WHERE id = p_purchase_order_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'purchase order not found: %', p_purchase_order_id;
  END IF;
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
  v_line_no INT := 0;
  v_remaining NUMERIC;
BEGIN
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

-- ---------------------------------------------------------------------------
-- Goods receipt (GRN) — submit calls post_stock_receipt
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

    IF NOT FOUND THEN
      RAISE EXCEPTION 'invalid PO line for GRN';
    END IF;
  END LOOP;

  RETURN v_grn;
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
  v_st public.procurement_doc_status;
  v_receipt_lines JSONB := '[]'::jsonb;
  v_row RECORD;
  v_entry UUID;
  v_sel RECORD;
  v_po_price NUMERIC;
  v_var_pct NUMERIC;
  v_tolerance CONSTANT NUMERIC := 0.05;
  v_mismatch BOOLEAN := false;
BEGIN
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
    SELECT grl.*
    FROM public.goods_receipt_lines grl
    WHERE grl.goods_receipt_id = p_goods_receipt_id
    ORDER BY grl.created_at, grl.id
  LOOP
    SELECT sel.id INTO v_sel
    FROM public.stock_entry_lines sel
    WHERE sel.stock_entry_id = v_entry
      AND sel.stock_item_id = v_row.stock_item_id
      AND sel.qty = v_row.qty
      AND sel.stock_entry_line_id IS NULL
    ORDER BY sel.created_at
    LIMIT 1;

    -- match without alias typo: use v_sel.id
    SELECT sel.id INTO v_sel.id
    FROM public.stock_entry_lines sel
    WHERE sel.stock_entry_id = v_entry
      AND sel.stock_item_id = v_row.stock_item_id
      AND sel.qty = v_row.qty
    ORDER BY sel.created_at
    LIMIT 1;

    UPDATE public.goods_receipt_lines grl
    SET
      stock_entry_line_id = (
        SELECT sel.id
        FROM public.stock_entry_lines sel
        WHERE sel.stock_entry_id = v_entry
          AND sel.stock_item_id = grl.stock_item_id
          AND sel.qty = grl.qty
          AND sel.id NOT IN (
            SELECT stock_entry_line_id FROM public.goods_receipt_lines
            WHERE goods_receipt_id = p_goods_receipt_id
              AND stock_entry_line_id IS NOT NULL
          )
        ORDER BY sel.created_at
        LIMIT 1
      ),
      price_variance_pct = CASE
        WHEN pol.unit_price > 0 THEN
          abs(grl.unit_cost - pol.unit_price) / pol.unit_price
        ELSE NULL
      END
    FROM public.purchase_order_lines pol
    WHERE grl.id = v_row.id
      AND pol.id = grl.purchase_order_line_id;

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

CREATE OR REPLACE FUNCTION public.cancel_goods_receipt(p_goods_receipt_id UUID)
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

-- ---------------------------------------------------------------------------
-- Landed cost voucher RPCs
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.suppliers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.material_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.material_request_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchase_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.purchase_order_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.goods_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.goods_receipt_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.landed_cost_vouchers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.landed_cost_charges ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.landed_cost_allocations ENABLE ROW LEVEL SECURITY;

CREATE POLICY suppliers_staff_all
  ON public.suppliers FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY suppliers_self_select
  ON public.suppliers FOR SELECT TO authenticated
  USING (profile_id = auth.uid());

CREATE POLICY material_requests_staff
  ON public.material_requests FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY material_request_lines_staff
  ON public.material_request_lines FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY purchase_orders_staff
  ON public.purchase_orders FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY purchase_orders_supplier_select
  ON public.purchase_orders FOR SELECT TO authenticated
  USING (supplier_id = public.current_supplier_id());

CREATE POLICY purchase_order_lines_staff
  ON public.purchase_order_lines FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY purchase_order_lines_supplier_select
  ON public.purchase_order_lines FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.purchase_orders po
      WHERE po.id = purchase_order_id
        AND po.supplier_id = public.current_supplier_id()
    )
  );

CREATE POLICY goods_receipts_staff
  ON public.goods_receipts FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY goods_receipts_supplier_select
  ON public.goods_receipts FOR SELECT TO authenticated
  USING (supplier_id = public.current_supplier_id());

CREATE POLICY goods_receipt_lines_staff
  ON public.goods_receipt_lines FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY goods_receipt_lines_supplier_select
  ON public.goods_receipt_lines FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.goods_receipts gr
      WHERE gr.id = goods_receipt_id
        AND gr.supplier_id = public.current_supplier_id()
    )
  );

CREATE POLICY landed_cost_staff
  ON public.landed_cost_vouchers FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  );

CREATE POLICY landed_cost_charges_staff
  ON public.landed_cost_charges FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  );

CREATE POLICY landed_cost_allocations_staff
  ON public.landed_cost_allocations FOR ALL TO authenticated
  USING (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  );

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON public.suppliers TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.material_requests TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.material_request_lines TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.purchase_orders TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.purchase_order_lines TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.goods_receipts TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.goods_receipt_lines TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.landed_cost_vouchers TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.landed_cost_charges TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.landed_cost_allocations TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.current_supplier_id() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_supplier(TEXT, TEXT, TEXT, TEXT, public.currency_code) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.link_supplier_profile(UUID, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_material_request(UUID, DATE, JSONB, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_material_request(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cancel_material_request(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_purchase_order(UUID, UUID, public.currency_code, NUMERIC, JSONB, TEXT, DATE, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_purchase_order(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cancel_purchase_order(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.convert_material_request_to_po(UUID, UUID, public.currency_code, NUMERIC, UUID[]) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_goods_receipt(UUID, JSONB, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_goods_receipt(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cancel_goods_receipt(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_landed_cost_voucher(UUID, public.currency_code, NUMERIC, JSONB, JSONB, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_landed_cost_voucher(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cancel_landed_cost_voucher(UUID, TEXT) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.current_supplier_id() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_supplier(TEXT, TEXT, TEXT, TEXT, public.currency_code) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.link_supplier_profile(UUID, UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_material_request(UUID, DATE, JSONB, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_material_request(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cancel_material_request(UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_purchase_order(UUID, UUID, public.currency_code, NUMERIC, JSONB, TEXT, DATE, UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_purchase_order(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cancel_purchase_order(UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.convert_material_request_to_po(UUID, UUID, public.currency_code, NUMERIC, UUID[]) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_goods_receipt(UUID, JSONB, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_goods_receipt(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cancel_goods_receipt(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_landed_cost_voucher(UUID, public.currency_code, NUMERIC, JSONB, JSONB, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_landed_cost_voucher(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cancel_landed_cost_voucher(UUID, TEXT) TO authenticated, service_role;

-- @web_agent follow-on: supplier portal read-only via RLS policies above + RPC list:
-- create_purchase_order / submit / cancel / create_goods_receipt / submit_goods_receipt
-- convert_material_request_to_po / landed cost finance RPCs (finance role only)
