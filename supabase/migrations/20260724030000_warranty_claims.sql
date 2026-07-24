-- Phase 5b: Warranty / serial claims (WC- series)
-- Exclusions: no ZIMRA / fiscal / warranty-authority payloads; no payroll tax.
-- Returns always Quarantine-first (post_return_to_quarantine or CN quarantine path).

CREATE TYPE public.warranty_claim_status AS ENUM (
  'open',
  'approved',
  'rejected',
  'closed'
);

CREATE TYPE public.warranty_claim_resolution AS ENUM (
  'replacement',
  'credit_note',
  'return_only',
  'reject_only'
);

CREATE TABLE public.warranty_claims (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT NOT NULL UNIQUE,
  status public.warranty_claim_status NOT NULL DEFAULT 'open',
  stock_serial_id UUID REFERENCES public.stock_serials (id) ON DELETE RESTRICT,
  sales_invoice_id UUID REFERENCES public.sales_invoices (id) ON DELETE RESTRICT,
  stock_batch_id UUID REFERENCES public.stock_batches (id) ON DELETE SET NULL,
  stock_item_id UUID REFERENCES public.stock_items (id) ON DELETE RESTRICT,
  customer_id UUID REFERENCES public.customers (id) ON DELETE SET NULL,
  resolution public.warranty_claim_resolution,
  credit_note_id UUID REFERENCES public.sales_invoices (id) ON DELETE SET NULL,
  replacement_stock_entry_id UUID REFERENCES public.stock_entries (id) ON DELETE SET NULL,
  quarantine_stock_entry_id UUID REFERENCES public.stock_entries (id) ON DELETE SET NULL,
  currency public.currency_code,
  notes TEXT,
  reject_reason TEXT,
  created_by UUID REFERENCES auth.users (id),
  decided_by UUID REFERENCES auth.users (id),
  decided_at TIMESTAMPTZ,
  closed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT warranty_claim_linkage CHECK (
    stock_serial_id IS NOT NULL OR sales_invoice_id IS NOT NULL
  ),
  CONSTRAINT warranty_claim_resolution_when_decided CHECK (
    (status IN ('open', 'closed') AND resolution IS NULL)
    OR (status = 'rejected' AND resolution = 'reject_only')
    OR (
      status = 'approved'
      AND resolution IN ('replacement', 'credit_note', 'return_only')
    )
  )
);

CREATE INDEX warranty_claims_status_idx ON public.warranty_claims (status);
CREATE INDEX warranty_claims_serial_idx ON public.warranty_claims (stock_serial_id);
CREATE INDEX warranty_claims_invoice_idx ON public.warranty_claims (sales_invoice_id);

CREATE UNIQUE INDEX warranty_claims_one_open_per_serial
  ON public.warranty_claims (stock_serial_id)
  WHERE status = 'open' AND stock_serial_id IS NOT NULL;

-- Domain events for warranty workflow
INSERT INTO public.sms_event_catalog (code, description, category, priority) VALUES
  ('warranty_claim_opened', 'Warranty claim opened', 'sales', 'normal'),
  ('warranty_claim_approved', 'Warranty claim approved', 'sales', 'high'),
  ('warranty_claim_rejected', 'Warranty claim rejected', 'sales', 'normal'),
  ('warranty_claim_closed', 'Warranty claim closed', 'sales', 'low')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.naming_series (prefix, description, pad_length) VALUES
  ('WC-', 'Warranty claim', 5)
ON CONFLICT (prefix) DO NOTHING;

CREATE OR REPLACE FUNCTION public._require_warranty_staff()
RETURNS void
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'sales, warehouse, or admin role required for warranty claims';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public._warranty_claim_touch_updated()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

CREATE TRIGGER warranty_claims_updated
  BEFORE UPDATE ON public.warranty_claims
  FOR EACH ROW EXECUTE PROCEDURE public._warranty_claim_touch_updated();

-- Posted stock issue from saleable warehouse (replacement outbound; no sale journal)
CREATE OR REPLACE FUNCTION public.post_stock_issue(
  p_from_warehouse_id UUID,
  p_notes TEXT,
  p_lines JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_entry UUID;
  v_line JSONB;
  v_item UUID;
  v_uom UUID;
  v_qty NUMERIC;
  v_qty_base NUMERIC;
  v_cost NUMERIC;
  v_currency public.currency_code;
  v_val public.valuation_method;
  v_is_quar BOOLEAN;
  v_serial UUID;
BEGIN
  PERFORM public._require_warehouse_staff();

  SELECT is_quarantine INTO v_is_quar
  FROM public.warehouses WHERE id = p_from_warehouse_id;
  IF COALESCE(v_is_quar, false) THEN
    RAISE EXCEPTION 'issue source must be saleable warehouse, not quarantine';
  END IF;

  INSERT INTO public.stock_entries (
    entry_type, status, from_warehouse_id, notes, created_by,
    posted_at, document_number
  )
  VALUES (
    'issue', 'posted', p_from_warehouse_id, p_notes, auth.uid(),
    now(), public.next_series_value('ISS-')
  )
  RETURNING id INTO v_entry;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_item := (v_line ->> 'stock_item_id')::uuid;
    v_uom := (v_line ->> 'uom_id')::uuid;
    v_qty := (v_line ->> 'qty')::numeric;
    v_qty_base := public.convert_to_base_uom(v_item, v_uom, v_qty);
    v_val := COALESCE((v_line ->> 'valuation_method')::public.valuation_method, 'FIFO');

    SELECT unit_cost, currency INTO v_cost, v_currency
    FROM public.stock_levels
    WHERE stock_item_id = v_item AND warehouse_id = p_from_warehouse_id;

    PERFORM public._consume_fifo_batches(v_item, p_from_warehouse_id, v_qty_base);
    PERFORM public._adjust_stock_level(
      v_item, p_from_warehouse_id, -v_qty_base, v_val,
      COALESCE(v_cost, 0), COALESCE(v_currency, 'USD')
    );

    INSERT INTO public.stock_entry_lines (
      stock_entry_id, stock_item_id, uom_id, qty, qty_base,
      unit_cost, currency, valuation_method
    )
    VALUES (
      v_entry, v_item, v_uom, v_qty, v_qty_base,
      COALESCE(v_cost, 0), COALESCE(v_currency, 'USD'), v_val
    );

    IF v_line ? 'replacement_serial_id' THEN
      v_serial := (v_line ->> 'replacement_serial_id')::uuid;
      UPDATE public.stock_serials
      SET status = 'sold', warehouse_id = NULL
      WHERE id = v_serial AND stock_item_id = v_item AND status = 'in_stock';
      IF FOUND THEN
        PERFORM public.emit_domain_event(
          'serial_moved',
          'warranty:issue:serial:' || v_serial::text || ':' || v_entry::text,
          jsonb_build_object(
            'stock_serial_id', v_serial,
            'stock_entry_id', v_entry,
            'reason', 'warranty_replacement'
          )
        );
      END IF;
    END IF;
  END LOOP;

  RETURN v_entry;
END;
$$;

CREATE OR REPLACE FUNCTION public.open_warranty_claim(
  p_stock_serial_id UUID DEFAULT NULL,
  p_sales_invoice_id UUID DEFAULT NULL,
  p_stock_batch_id UUID DEFAULT NULL,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_serial public.stock_serials%ROWTYPE;
  v_inv public.sales_invoices%ROWTYPE;
  v_item UUID;
  v_customer UUID;
  v_currency public.currency_code;
BEGIN
  PERFORM public._require_warranty_staff();

  IF p_stock_serial_id IS NULL AND p_sales_invoice_id IS NULL THEN
    RAISE EXCEPTION 'warranty claim requires stock_serial_id and/or sales_invoice_id';
  END IF;

  IF p_stock_serial_id IS NOT NULL THEN
    SELECT * INTO v_serial FROM public.stock_serials WHERE id = p_stock_serial_id;
    IF NOT FOUND THEN
      RAISE EXCEPTION 'serial not found';
    END IF;
    IF v_serial.status IN ('quarantine', 'scrapped') THEN
      RAISE EXCEPTION 'serial % is % — cannot open warranty claim', p_stock_serial_id, v_serial.status;
    END IF;
    IF EXISTS (
      SELECT 1 FROM public.warranty_claims
      WHERE stock_serial_id = p_stock_serial_id AND status = 'open'
    ) THEN
      RAISE EXCEPTION 'open warranty claim already exists for serial %', p_stock_serial_id;
    END IF;
    v_item := v_serial.stock_item_id;
  END IF;

  IF p_sales_invoice_id IS NOT NULL THEN
    SELECT * INTO v_inv
    FROM public.sales_invoices
    WHERE id = p_sales_invoice_id AND doc_type = 'invoice' AND status = 'posted';
    IF NOT FOUND THEN
      RAISE EXCEPTION 'posted sales invoice required for invoice linkage';
    END IF;
    v_customer := v_inv.customer_id;
    v_currency := v_inv.currency;
    IF v_item IS NULL THEN
      SELECT stock_item_id INTO v_item
      FROM public.sales_invoice_lines
      WHERE invoice_id = p_sales_invoice_id AND NOT is_core_charge
      ORDER BY created_at
      LIMIT 1;
    END IF;
  END IF;

  INSERT INTO public.warranty_claims (
    document_number, status, stock_serial_id, sales_invoice_id,
    stock_batch_id, stock_item_id, customer_id, currency, notes, created_by
  )
  VALUES (
    public.next_series_value('WC-'), 'open', p_stock_serial_id, p_sales_invoice_id,
    p_stock_batch_id, v_item, v_customer, v_currency, p_notes, auth.uid()
  )
  RETURNING id INTO v_id;

  PERFORM public.emit_domain_event(
    'warranty_claim_opened',
    'warranty:open:' || v_id::text,
    jsonb_build_object(
      'warranty_claim_id', v_id,
      'stock_serial_id', p_stock_serial_id,
      'sales_invoice_id', p_sales_invoice_id
    )
  );

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.approve_warranty_claim(
  p_claim_id UUID,
  p_resolution public.warranty_claim_resolution,
  p_lines JSONB DEFAULT NULL,
  p_replacement_lines JSONB DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_claim public.warranty_claims%ROWTYPE;
  v_serial public.stock_serials%ROWTYPE;
  v_from_wh UUID;
  v_uom UUID;
  v_return_lines JSONB;
  v_entry UUID;
  v_cn UUID;
  v_main UUID;
BEGIN
  PERFORM public._require_warranty_staff();

  IF p_resolution NOT IN ('replacement', 'credit_note', 'return_only') THEN
    RAISE EXCEPTION 'approve resolution must be replacement, credit_note, or return_only';
  END IF;

  SELECT * INTO v_claim FROM public.warranty_claims WHERE id = p_claim_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'warranty claim not found';
  END IF;
  IF v_claim.status <> 'open' THEN
    RAISE EXCEPTION 'only open claims can be approved (status=%)', v_claim.status;
  END IF;

  IF p_resolution = 'credit_note' AND v_claim.sales_invoice_id IS NULL THEN
    RAISE EXCEPTION 'credit_note resolution requires sales_invoice_id on claim';
  END IF;
  IF p_resolution = 'replacement' AND (
    p_replacement_lines IS NULL OR jsonb_array_length(p_replacement_lines) = 0
  ) THEN
    RAISE EXCEPTION 'replacement resolution requires p_replacement_lines';
  END IF;

  SELECT id INTO v_main FROM public.warehouses WHERE code = 'MAIN' AND is_active LIMIT 1;

  -- Physical return → Quarantine (skip when CN path will receive stock for same lines)
  IF p_resolution <> 'credit_note' THEN
    v_return_lines := p_lines;
    IF v_return_lines IS NULL AND v_claim.stock_serial_id IS NOT NULL THEN
      SELECT * INTO v_serial FROM public.stock_serials WHERE id = v_claim.stock_serial_id;
      SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
      v_from_wh := COALESCE(
        v_serial.warehouse_id,
        (SELECT warehouse_id FROM public.sales_invoices WHERE id = v_claim.sales_invoice_id),
        v_main
      );
      IF v_from_wh IS NULL THEN
        RAISE EXCEPTION 'cannot determine source warehouse for return';
      END IF;
      v_return_lines := jsonb_build_array(
        jsonb_build_object(
          'stock_item_id', v_serial.stock_item_id,
          'uom_id', v_uom,
          'qty', 1,
          'valuation_method', 'FIFO'
        )
      );
    ELSIF v_return_lines IS NULL AND v_claim.stock_item_id IS NOT NULL THEN
      SELECT id INTO v_uom FROM public.uoms WHERE code = 'EA';
      v_from_wh := COALESCE(
        (SELECT warehouse_id FROM public.sales_invoices WHERE id = v_claim.sales_invoice_id),
        v_main
      );
      v_return_lines := jsonb_build_array(
        jsonb_build_object(
          'stock_item_id', v_claim.stock_item_id,
          'uom_id', v_uom,
          'qty', 1,
          'valuation_method', 'FIFO'
        )
      );
    END IF;

    IF v_return_lines IS NOT NULL THEN
      IF v_from_wh IS NULL THEN
        v_from_wh := COALESCE(
          (SELECT warehouse_id FROM public.sales_invoices WHERE id = v_claim.sales_invoice_id),
          v_main
        );
      END IF;
      v_entry := public.post_return_to_quarantine(
        v_from_wh,
        format('Warranty return %s', v_claim.document_number),
        v_return_lines
      );
      UPDATE public.warranty_claims
      SET quarantine_stock_entry_id = v_entry
      WHERE id = p_claim_id;

      IF v_claim.stock_serial_id IS NOT NULL THEN
        UPDATE public.stock_serials
        SET status = 'quarantine',
            warehouse_id = (
              SELECT id FROM public.warehouses WHERE is_quarantine AND is_active ORDER BY code LIMIT 1
            )
        WHERE id = v_claim.stock_serial_id;
        PERFORM public.emit_domain_event(
          'serial_moved',
          'warranty:quar:serial:' || v_claim.stock_serial_id::text || ':' || p_claim_id::text,
          jsonb_build_object(
            'warranty_claim_id', p_claim_id,
            'stock_serial_id', v_claim.stock_serial_id,
            'quarantine_stock_entry_id', v_entry
          )
        );
      END IF;
    END IF;
  END IF;

  IF p_resolution = 'credit_note' THEN
    IF p_lines IS NULL OR jsonb_array_length(p_lines) = 0 THEN
      RAISE EXCEPTION 'credit_note resolution requires p_lines';
    END IF;
    v_cn := public.post_return_credit_note(v_claim.sales_invoice_id, p_lines);
    UPDATE public.warranty_claims SET credit_note_id = v_cn WHERE id = p_claim_id;

    IF v_claim.stock_serial_id IS NOT NULL THEN
      UPDATE public.stock_serials
      SET status = 'quarantine',
          warehouse_id = (
            SELECT id FROM public.warehouses WHERE is_quarantine AND is_active ORDER BY code LIMIT 1
          )
      WHERE id = v_claim.stock_serial_id;
      PERFORM public.emit_domain_event(
        'serial_moved',
        'warranty:cn:serial:' || v_claim.stock_serial_id::text || ':' || p_claim_id::text,
        jsonb_build_object(
          'warranty_claim_id', p_claim_id,
          'credit_note_id', v_cn,
          'stock_serial_id', v_claim.stock_serial_id
        )
      );
    END IF;
  END IF;

  IF p_resolution = 'replacement' THEN
    v_entry := public.post_stock_issue(
      COALESCE(
        (SELECT warehouse_id FROM public.sales_invoices WHERE id = v_claim.sales_invoice_id),
        v_main
      ),
      format('Warranty replacement %s', v_claim.document_number),
      p_replacement_lines
    );
    UPDATE public.warranty_claims
    SET replacement_stock_entry_id = v_entry
    WHERE id = p_claim_id;
  END IF;

  UPDATE public.warranty_claims
  SET
    status = 'approved',
    resolution = p_resolution,
    decided_by = auth.uid(),
    decided_at = now()
  WHERE id = p_claim_id;

  PERFORM public.emit_domain_event(
    'warranty_claim_approved',
    'warranty:approved:' || p_claim_id::text,
    jsonb_build_object(
      'warranty_claim_id', p_claim_id,
      'resolution', p_resolution,
      'credit_note_id', v_cn,
      'quarantine_stock_entry_id', (
        SELECT quarantine_stock_entry_id FROM public.warranty_claims WHERE id = p_claim_id
      ),
      'replacement_stock_entry_id', (
        SELECT replacement_stock_entry_id FROM public.warranty_claims WHERE id = p_claim_id
      )
    )
  );

  RETURN p_claim_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.reject_warranty_claim(
  p_claim_id UUID,
  p_reason TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_status public.warranty_claim_status;
BEGIN
  PERFORM public._require_warranty_staff();

  SELECT status INTO v_status FROM public.warranty_claims WHERE id = p_claim_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'warranty claim not found';
  END IF;
  IF v_status <> 'open' THEN
    RAISE EXCEPTION 'only open claims can be rejected (status=%)', v_status;
  END IF;

  UPDATE public.warranty_claims
  SET
    status = 'rejected',
    resolution = 'reject_only',
    reject_reason = p_reason,
    decided_by = auth.uid(),
    decided_at = now()
  WHERE id = p_claim_id;

  PERFORM public.emit_domain_event(
    'warranty_claim_rejected',
    'warranty:rejected:' || p_claim_id::text,
    jsonb_build_object('warranty_claim_id', p_claim_id, 'reason', p_reason)
  );

  RETURN p_claim_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.close_warranty_claim(p_claim_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_status public.warranty_claim_status;
BEGIN
  PERFORM public._require_warranty_staff();

  SELECT status INTO v_status FROM public.warranty_claims WHERE id = p_claim_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'warranty claim not found';
  END IF;

  IF v_status = 'closed' THEN
    RETURN p_claim_id;
  END IF;

  IF v_status NOT IN ('approved', 'rejected') THEN
    RAISE EXCEPTION 'only approved or rejected claims can be closed (status=%)', v_status;
  END IF;

  UPDATE public.warranty_claims
  SET status = 'closed', closed_at = now()
  WHERE id = p_claim_id;

  PERFORM public.emit_domain_event(
    'warranty_claim_closed',
    'warranty:closed:' || p_claim_id::text,
    jsonb_build_object('warranty_claim_id', p_claim_id)
  );

  RETURN p_claim_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- RLS: staff write; finance read-only
-- ---------------------------------------------------------------------------
ALTER TABLE public.warranty_claims ENABLE ROW LEVEL SECURITY;

CREATE POLICY warranty_claims_select_staff
  ON public.warranty_claims FOR SELECT TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'sales', 'warehouse', 'finance']::public.staff_role[]
    )
  );

CREATE POLICY warranty_claims_insert_staff
  ON public.warranty_claims FOR INSERT TO authenticated
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[])
  );

CREATE POLICY warranty_claims_update_staff
  ON public.warranty_claims FOR UPDATE TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[])
  )
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'sales', 'warehouse']::public.staff_role[])
  );

CREATE POLICY warranty_claims_delete_admin
  ON public.warranty_claims FOR DELETE TO authenticated
  USING (public.has_staff_role(ARRAY['admin']::public.staff_role[]));

REVOKE ALL ON FUNCTION public.post_stock_issue(UUID, TEXT, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.open_warranty_claim(UUID, UUID, UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.approve_warranty_claim(UUID, public.warranty_claim_resolution, JSONB, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.reject_warranty_claim(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.close_warranty_claim(UUID) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.post_stock_issue(UUID, TEXT, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.open_warranty_claim(UUID, UUID, UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.approve_warranty_claim(UUID, public.warranty_claim_resolution, JSONB, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.reject_warranty_claim(UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.close_warranty_claim(UUID) TO authenticated, service_role;
