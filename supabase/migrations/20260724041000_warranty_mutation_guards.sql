-- Phase 5b follow-up: block direct warranty_claims mutation bypassing RPC state machine.
-- SECURITY DEFINER RPCs set app.warranty_rpc=1 (transaction-local) before table writes.

CREATE OR REPLACE FUNCTION public._warranty_rpc_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.warranty_rpc', true), '') = '1';
$$;

CREATE OR REPLACE FUNCTION public._warranty_begin_rpc()
RETURNS void
LANGUAGE sql
AS $$
  SELECT set_config('app.warranty_rpc', '1', true);
$$;

CREATE OR REPLACE FUNCTION public.guard_warranty_claim_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._warranty_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.status IS DISTINCT FROM 'open' THEN
      RAISE EXCEPTION 'warranty_claims: new rows must be open; use open_warranty_claim';
    END IF;
    RAISE EXCEPTION 'warranty_claims: use open_warranty_claim RPC';
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'warranty_claims: direct delete not allowed';
  ELSIF TG_OP = 'UPDATE' THEN
    IF OLD.status = 'closed' THEN
      RAISE EXCEPTION 'warranty_claims: closed claims are immutable';
    END IF;
    IF OLD.status IN ('approved', 'rejected') THEN
      RAISE EXCEPTION 'warranty_claims: use close_warranty_claim RPC (decided claims are immutable)';
    END IF;
    RAISE EXCEPTION 'warranty_claims: use warranty claim RPCs for updates';
  END IF;

  RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS warranty_claims_mutation_guard ON public.warranty_claims;
CREATE TRIGGER warranty_claims_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.warranty_claims
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_warranty_claim_mutation();

DROP POLICY IF EXISTS warranty_claims_insert_staff ON public.warranty_claims;
DROP POLICY IF EXISTS warranty_claims_update_staff ON public.warranty_claims;
DROP POLICY IF EXISTS warranty_claims_delete_admin ON public.warranty_claims;

REVOKE ALL ON FUNCTION public._warranty_rpc_active() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._warranty_begin_rpc() FROM PUBLIC;
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
  PERFORM public._warranty_begin_rpc();
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
      RAISE EXCEPTION 'serial % is % â€” cannot open warranty claim', p_stock_serial_id, v_serial.status;
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
  PERFORM public._warranty_begin_rpc();
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

  -- Physical return â†’ Quarantine (skip when CN path will receive stock for same lines)
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
  PERFORM public._warranty_begin_rpc();
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
  PERFORM public._warranty_begin_rpc();
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

