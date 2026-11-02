-- Phase 4b follow-up: block direct table mutation bypassing reconciliation RPC state machine.
-- SECURITY DEFINER RPCs set app.recon_rpc=1 (transaction-local) before mutating headers/lines.

CREATE OR REPLACE FUNCTION public._recon_rpc_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.recon_rpc', true), '') = '1';
$$;

CREATE OR REPLACE FUNCTION public._recon_begin_rpc()
RETURNS void
LANGUAGE sql
AS $$
  SELECT set_config('app.recon_rpc', '1', true);
$$;

CREATE OR REPLACE FUNCTION public.guard_stock_reconciliation_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF public._recon_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF NEW.status IS DISTINCT FROM 'draft' THEN
      RAISE EXCEPTION 'stock_reconciliations: new rows must be draft; use create_stock_reconciliation_draft';
    END IF;
    RAISE EXCEPTION 'stock_reconciliations: use create_stock_reconciliation_draft RPC';
  ELSIF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'stock_reconciliations: direct delete not allowed';
  ELSIF TG_OP = 'UPDATE' THEN
    IF OLD.status IN ('posted', 'cancelled') THEN
      RAISE EXCEPTION 'stock_reconciliations: posted/cancelled records are immutable (use cancel_stock_reconciliation RPC)';
    END IF;
    RAISE EXCEPTION 'stock_reconciliations: use reconciliation RPCs for updates';
  END IF;

  RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.guard_stock_reconciliation_line_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_recon_id UUID;
  v_status public.stock_entry_status;
BEGIN
  IF public._recon_rpc_active() THEN
    IF TG_OP = 'DELETE' THEN
      RETURN OLD;
    END IF;
    RETURN NEW;
  END IF;

  v_recon_id := COALESCE(NEW.stock_reconciliation_id, OLD.stock_reconciliation_id);
  SELECT r.status INTO v_status
  FROM public.stock_reconciliations r
  WHERE r.id = v_recon_id;

  IF v_status IS NULL THEN
    RAISE EXCEPTION 'stock_reconciliation_lines: parent reconciliation not found';
  END IF;

  IF v_status <> 'draft' THEN
    RAISE EXCEPTION 'stock_reconciliation_lines: parent must be draft (status=%)', v_status;
  END IF;

  RAISE EXCEPTION 'stock_reconciliation_lines: use reconciliation RPCs';
END;
$$;

DROP TRIGGER IF EXISTS stock_reconciliations_mutation_guard ON public.stock_reconciliations;
CREATE TRIGGER stock_reconciliations_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.stock_reconciliations
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_stock_reconciliation_mutation();

DROP TRIGGER IF EXISTS stock_reconciliation_lines_mutation_guard ON public.stock_reconciliation_lines;
CREATE TRIGGER stock_reconciliation_lines_mutation_guard
  BEFORE INSERT OR UPDATE OR DELETE ON public.stock_reconciliation_lines
  FOR EACH ROW
  EXECUTE PROCEDURE public.guard_stock_reconciliation_line_mutation();

-- RPC-only writes: SELECT remains for staff; mutations go through SECURITY DEFINER RPCs.
DROP POLICY IF EXISTS stock_recon_write_wh ON public.stock_reconciliations;
DROP POLICY IF EXISTS stock_recon_update_wh ON public.stock_reconciliations;
DROP POLICY IF EXISTS stock_recon_lines_write_wh ON public.stock_reconciliation_lines;

DROP POLICY IF EXISTS app_settings_select_staff ON public.app_settings;
CREATE POLICY app_settings_select_privileged
  ON public.app_settings FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

REVOKE ALL ON FUNCTION public._recon_rpc_active() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._recon_begin_rpc() FROM PUBLIC;

-- Patch RPCs: begin recon RPC session flag before table mutations.
CREATE OR REPLACE FUNCTION public._post_stock_reconciliation(p_reconciliation_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_recon public.stock_reconciliations%ROWTYPE;
  v_line RECORD;
  v_wh_quar BOOLEAN;
  v_inv_acct TEXT;
  v_delta NUMERIC;
  v_batch UUID;
  v_batch_code TEXT;
  v_write_up NUMERIC;
  v_write_down NUMERIC;
  v_lines JSONB := '[]'::jsonb;
  v_journal UUID;
  v_rate NUMERIC;
BEGIN
  PERFORM public._recon_begin_rpc();

  SELECT * INTO v_recon
  FROM public.stock_reconciliations
  WHERE id = p_reconciliation_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'reconciliation not found: %', p_reconciliation_id;
  END IF;
  IF v_recon.status NOT IN ('draft', 'pending_approval') THEN
    RAISE EXCEPTION 'reconciliation not submittable (status=%)', v_recon.status;
  END IF;
  IF v_recon.status = 'pending_approval' THEN
    IF v_recon.first_approver_id IS NULL OR v_recon.second_approver_id IS NULL THEN
      RAISE EXCEPTION 'dual-auth approvers required before posting';
    END IF;
    IF v_recon.first_approver_id = v_recon.second_approver_id THEN
      RAISE EXCEPTION 'second approver must be a different staff user';
    END IF;
  END IF;

  IF public.is_period_locked(CURRENT_DATE) THEN
    RAISE EXCEPTION 'accounting period is locked for today';
  END IF;

  PERFORM public._recon_assert_single_currency(p_reconciliation_id);

  SELECT is_quarantine INTO v_wh_quar
  FROM public.warehouses
  WHERE id = v_recon.warehouse_id;

  IF v_wh_quar IS NULL THEN
    RAISE EXCEPTION 'warehouse not found';
  END IF;

  v_inv_acct := CASE WHEN v_wh_quar THEN '1310' ELSE '1300' END;

  FOR v_line IN
    SELECT *
    FROM public.stock_reconciliation_lines
    WHERE stock_reconciliation_id = p_reconciliation_id
      AND variance_qty <> 0
  LOOP
    v_delta := v_line.variance_qty;

    IF v_delta > 0 THEN
      v_batch_code := public.next_series_value('BATCH-');
      INSERT INTO public.stock_batches (
        batch_code, stock_item_id, warehouse_id, valuation_method,
        unit_cost, currency, qty_on_hand
      )
      VALUES (
        v_batch_code,
        v_line.stock_item_id,
        v_recon.warehouse_id,
        v_line.valuation_method,
        v_line.unit_cost,
        v_line.currency,
        v_delta
      )
      RETURNING id INTO v_batch;

      UPDATE public.stock_reconciliation_lines
      SET stock_batch_id = v_batch
      WHERE id = v_line.id;

      PERFORM public._adjust_stock_level(
        v_line.stock_item_id,
        v_recon.warehouse_id,
        v_delta,
        v_line.valuation_method,
        v_line.unit_cost,
        v_line.currency
      );
    ELSE
      PERFORM public._consume_fifo_batches(
        v_line.stock_item_id,
        v_recon.warehouse_id,
        abs(v_delta)
      );

      PERFORM public._adjust_stock_level(
        v_line.stock_item_id,
        v_recon.warehouse_id,
        v_delta,
        v_line.valuation_method,
        v_line.unit_cost,
        v_line.currency
      );
    END IF;
  END LOOP;

  SELECT
    COALESCE(round(SUM(CASE WHEN variance_qty > 0 THEN variance_qty * unit_cost ELSE 0 END), 2), 0),
    COALESCE(round(SUM(CASE WHEN variance_qty < 0 THEN abs(variance_qty) * unit_cost ELSE 0 END), 2), 0)
  INTO v_write_up, v_write_down
  FROM public.stock_reconciliation_lines
  WHERE stock_reconciliation_id = p_reconciliation_id
    AND variance_qty <> 0;

  IF v_write_up > 0 THEN
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object('account_code', v_inv_acct, 'debit', v_write_up, 'credit', 0),
      jsonb_build_object('account_code', '5100', 'debit', 0, 'credit', v_write_up)
    );
  END IF;
  IF v_write_down > 0 THEN
    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object('account_code', '5100', 'debit', v_write_down, 'credit', 0),
      jsonb_build_object('account_code', v_inv_acct, 'debit', 0, 'credit', v_write_down)
    );
  END IF;

  v_rate := CASE
    WHEN v_recon.currency = 'USD' THEN COALESCE(v_recon.exchange_rate_applied, 1)
    ELSE v_recon.exchange_rate_applied
  END;

  IF jsonb_array_length(v_lines) > 0 THEN
    v_journal := public._post_journal_entry_inventory(
      CURRENT_DATE,
      format('Stock reconciliation %s', COALESCE(v_recon.document_number, p_reconciliation_id::text)),
      v_recon.currency,
      v_rate,
      v_lines
    );
  END IF;

  UPDATE public.stock_reconciliations
  SET
    status = 'posted',
    journal_entry_id = v_journal,
    posted_at = now(),
    variance_value_abs = public._recon_compute_variance_value(p_reconciliation_id)
  WHERE id = p_reconciliation_id;

  PERFORM public.emit_domain_event(
    'stock_reconciliation_posted',
    'stock_reconciliation:' || p_reconciliation_id::text,
    jsonb_build_object(
      'stock_reconciliation_id', p_reconciliation_id,
      'warehouse_id', v_recon.warehouse_id,
      'journal_entry_id', v_journal,
      'variance_value_abs', public._recon_compute_variance_value(p_reconciliation_id)
    )
  );

  RETURN p_reconciliation_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_stock_reconciliation_draft(
  p_warehouse_id UUID,
  p_scope public.stock_reconciliation_scope,
  p_item_ids UUID[] DEFAULT NULL,
  p_notes TEXT DEFAULT NULL,
  p_currency public.currency_code DEFAULT 'USD',
  p_exchange_rate NUMERIC DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_item UUID;
  v_sys NUMERIC;
  v_cost NUMERIC;
  v_cur public.currency_code;
  v_val public.valuation_method;
BEGIN
  PERFORM public._recon_begin_rpc();
  PERFORM public._require_warehouse_staff();

  IF NOT EXISTS (SELECT 1 FROM public.warehouses WHERE id = p_warehouse_id AND is_active = true) THEN
    RAISE EXCEPTION 'warehouse not found or inactive: %', p_warehouse_id;
  END IF;

  IF p_scope = 'partial' AND (p_item_ids IS NULL OR cardinality(p_item_ids) = 0) THEN
    RAISE EXCEPTION 'partial reconciliation requires item_ids';
  END IF;

  INSERT INTO public.stock_reconciliations (
    warehouse_id, scope, status, document_number, notes,
    created_by, currency, exchange_rate_applied
  )
  VALUES (
    p_warehouse_id,
    p_scope,
    'draft',
    public.next_series_value('SRE-'),
    p_notes,
    auth.uid(),
    p_currency,
    CASE
      WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1)
      ELSE p_exchange_rate
    END
  )
  RETURNING id INTO v_id;

  IF p_scope = 'full' THEN
    INSERT INTO public.stock_reconciliation_lines (
      stock_reconciliation_id, stock_item_id, system_qty,
      counted_qty, unit_cost, currency, valuation_method
    )
    SELECT
      v_id,
      sl.stock_item_id,
      sl.quantity,
      sl.quantity,
      COALESCE(sl.unit_cost, 0),
      sl.currency,
      sl.valuation_method
    FROM public.stock_levels sl
    WHERE sl.warehouse_id = p_warehouse_id;
  ELSE
    FOREACH v_item IN ARRAY p_item_ids
    LOOP
      SELECT quantity, unit_cost, currency, valuation_method
      INTO v_sys, v_cost, v_cur, v_val
      FROM public.stock_levels
      WHERE stock_item_id = v_item AND warehouse_id = p_warehouse_id;

      INSERT INTO public.stock_reconciliation_lines (
        stock_reconciliation_id, stock_item_id, system_qty,
        counted_qty, unit_cost, currency, valuation_method
      )
      VALUES (
        v_id,
        v_item,
        COALESCE(v_sys, 0),
        COALESCE(v_sys, 0),
        COALESCE(v_cost, 0),
        COALESCE(v_cur, p_currency),
        COALESCE(v_val, 'FIFO')
      );
    END LOOP;
  END IF;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.upsert_stock_reconciliation_lines(
  p_reconciliation_id UUID,
  p_lines JSONB
)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_status public.stock_entry_status;
  v_line JSONB;
  v_item UUID;
  v_counted NUMERIC;
  v_updated INT := 0;
BEGIN
  PERFORM public._recon_begin_rpc();
  PERFORM public._require_warehouse_staff();

  SELECT status INTO v_status
  FROM public.stock_reconciliations
  WHERE id = p_reconciliation_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'reconciliation not found: %', p_reconciliation_id;
  END IF;
  IF v_status <> 'draft' THEN
    RAISE EXCEPTION 'lines editable only while draft (status=%)', v_status;
  END IF;

  IF p_lines IS NULL OR jsonb_typeof(p_lines) <> 'array' OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'lines required';
  END IF;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    v_item := (v_line ->> 'stock_item_id')::uuid;
    v_counted := (v_line ->> 'counted_qty')::numeric;

    IF v_counted IS NULL OR v_counted < 0 THEN
      RAISE EXCEPTION 'counted_qty required and must be >= 0 for item %', v_item;
    END IF;

    UPDATE public.stock_reconciliation_lines
    SET counted_qty = v_counted
    WHERE stock_reconciliation_id = p_reconciliation_id
      AND stock_item_id = v_item;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'item % not on reconciliation draft', v_item;
    END IF;

    v_updated := v_updated + 1;
  END LOOP;

  RETURN v_updated;
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_stock_reconciliation(p_reconciliation_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_recon public.stock_reconciliations%ROWTYPE;
  v_variance NUMERIC;
  v_threshold NUMERIC;
BEGIN
  PERFORM public._recon_begin_rpc();
  PERFORM public._require_warehouse_staff();

  SELECT * INTO v_recon
  FROM public.stock_reconciliations
  WHERE id = p_reconciliation_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'reconciliation not found: %', p_reconciliation_id;
  END IF;
  IF v_recon.status <> 'draft' THEN
    RAISE EXCEPTION 'only draft reconciliations can be submitted (status=%)', v_recon.status;
  END IF;

  IF public.is_period_locked(CURRENT_DATE) THEN
    RAISE EXCEPTION 'accounting period is locked for today';
  END IF;

  PERFORM public._recon_assert_single_currency(p_reconciliation_id);

  v_variance := public._recon_compute_variance_value(p_reconciliation_id);
  v_threshold := public._recon_dual_auth_threshold(v_recon.currency);

  UPDATE public.stock_reconciliations
  SET
    variance_value_abs = v_variance,
    first_approver_id = auth.uid(),
    first_approved_at = now()
  WHERE id = p_reconciliation_id;

  IF v_variance >= v_threshold THEN
    UPDATE public.stock_reconciliations
    SET status = 'pending_approval'
    WHERE id = p_reconciliation_id;

    PERFORM public.emit_domain_event(
      'stock_reconciliation_pending_approval',
      'stock_reconciliation:pending:' || p_reconciliation_id::text,
      jsonb_build_object(
        'stock_reconciliation_id', p_reconciliation_id,
        'variance_value_abs', v_variance,
        'threshold', v_threshold
      )
    );

    RETURN p_reconciliation_id;
  END IF;

  RETURN public._post_stock_reconciliation(p_reconciliation_id);
END;
$$;

CREATE OR REPLACE FUNCTION public.approve_stock_reconciliation(p_reconciliation_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_recon public.stock_reconciliations%ROWTYPE;
BEGIN
  PERFORM public._recon_begin_rpc();
  PERFORM public._require_warehouse_staff();

  SELECT * INTO v_recon
  FROM public.stock_reconciliations
  WHERE id = p_reconciliation_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'reconciliation not found: %', p_reconciliation_id;
  END IF;
  IF v_recon.status <> 'pending_approval' THEN
    RAISE EXCEPTION 'reconciliation not pending approval (status=%)', v_recon.status;
  END IF;
  IF v_recon.first_approver_id IS NULL THEN
    RAISE EXCEPTION 'first approver missing';
  END IF;
  IF auth.uid() IS NOT NULL AND auth.uid() = v_recon.first_approver_id THEN
    RAISE EXCEPTION 'second approver must be a different staff user';
  END IF;

  UPDATE public.stock_reconciliations
  SET
    second_approver_id = auth.uid(),
    second_approved_at = now()
  WHERE id = p_reconciliation_id;

  RETURN public._post_stock_reconciliation(p_reconciliation_id);
END;
$$;

CREATE OR REPLACE FUNCTION public.cancel_stock_reconciliation(
  p_reconciliation_id UUID,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_recon public.stock_reconciliations%ROWTYPE;
  v_line RECORD;
  v_delta NUMERIC;
  v_batch UUID;
  v_batch_code TEXT;
  v_rev UUID;
BEGIN
  PERFORM public._recon_begin_rpc();
  PERFORM public._require_warehouse_staff();

  SELECT * INTO v_recon
  FROM public.stock_reconciliations
  WHERE id = p_reconciliation_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'reconciliation not found: %', p_reconciliation_id;
  END IF;
  IF v_recon.status <> 'posted' THEN
    RAISE EXCEPTION 'only posted reconciliations can be cancelled (status=%)', v_recon.status;
  END IF;

  IF public.is_period_locked(CURRENT_DATE) THEN
    RAISE EXCEPTION 'accounting period is locked for today';
  END IF;

  FOR v_line IN
    SELECT *
    FROM public.stock_reconciliation_lines
    WHERE stock_reconciliation_id = p_reconciliation_id
      AND variance_qty <> 0
  LOOP
    v_delta := -v_line.variance_qty;

    IF v_delta > 0 THEN
      v_batch_code := public.next_series_value('BATCH-');
      INSERT INTO public.stock_batches (
        batch_code, stock_item_id, warehouse_id, valuation_method,
        unit_cost, currency, qty_on_hand
      )
      VALUES (
        v_batch_code,
        v_line.stock_item_id,
        v_recon.warehouse_id,
        v_line.valuation_method,
        v_line.unit_cost,
        v_line.currency,
        v_delta
      )
      RETURNING id INTO v_batch;

      PERFORM public._adjust_stock_level(
        v_line.stock_item_id,
        v_recon.warehouse_id,
        v_delta,
        v_line.valuation_method,
        v_line.unit_cost,
        v_line.currency
      );
    ELSIF v_delta < 0 THEN
      PERFORM public._consume_fifo_batches(
        v_line.stock_item_id,
        v_recon.warehouse_id,
        abs(v_delta)
      );

      PERFORM public._adjust_stock_level(
        v_line.stock_item_id,
        v_recon.warehouse_id,
        v_delta,
        v_line.valuation_method,
        v_line.unit_cost,
        v_line.currency
      );
    END IF;
  END LOOP;

  IF v_recon.journal_entry_id IS NOT NULL THEN
    v_rev := public._reverse_journal_inventory(
      v_recon.journal_entry_id,
      COALESCE(
        p_notes,
        format('Cancel stock reconciliation %s', COALESCE(v_recon.document_number, p_reconciliation_id::text))
      )
    );
  END IF;

  UPDATE public.stock_reconciliations
  SET
    status = 'cancelled',
    reversal_journal_entry_id = v_rev,
    notes = COALESCE(p_notes, notes)
  WHERE id = p_reconciliation_id;

  PERFORM public.emit_domain_event(
    'stock_reconciliation_cancelled',
    'stock_reconciliation:cancelled:' || p_reconciliation_id::text,
    jsonb_build_object(
      'stock_reconciliation_id', p_reconciliation_id,
      'reversal_journal_entry_id', v_rev
    )
  );

  RETURN p_reconciliation_id;
END;
$$;
