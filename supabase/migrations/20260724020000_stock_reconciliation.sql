-- Phase 4b: Stock reconciliation / cycle count
-- Exclusions: no ZIMRA / payroll tax; Bridge-First (no HTML5 QR)
-- Dual-auth threshold: app_settings key inventory.reconciliation_variance_dual_auth_threshold
--   default {"USD": 500.00, "ZIG": 5000.00} — absolute |variance value| in header currency

CREATE TYPE public.stock_reconciliation_scope AS ENUM ('full', 'partial');

-- ---------------------------------------------------------------------------
-- Config (variance dual-auth threshold)
-- ---------------------------------------------------------------------------
CREATE TABLE public.app_settings (
  key TEXT PRIMARY KEY,
  value_json JSONB NOT NULL,
  description TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO public.app_settings (key, value_json, description) VALUES
  (
    'inventory.reconciliation_variance_dual_auth_threshold',
    '{"USD": 500.00, "ZIG": 5000.00}'::jsonb,
    'Absolute |variance value| in header currency requiring second staff approval'
  );

CREATE OR REPLACE FUNCTION public._recon_dual_auth_threshold(p_currency public.currency_code)
RETURNS NUMERIC
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT COALESCE(
    (s.value_json ->> p_currency::text)::numeric,
    (s.value_json ->> 'USD')::numeric,
    500::numeric
  )
  FROM public.app_settings s
  WHERE s.key = 'inventory.reconciliation_variance_dual_auth_threshold';
$$;

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------
CREATE TABLE public.stock_reconciliations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  warehouse_id UUID NOT NULL REFERENCES public.warehouses (id) ON DELETE RESTRICT,
  scope public.stock_reconciliation_scope NOT NULL,
  status public.stock_entry_status NOT NULL DEFAULT 'draft',
  document_number TEXT,
  currency public.currency_code NOT NULL DEFAULT 'USD',
  exchange_rate_applied NUMERIC(18, 6),
  notes TEXT,
  variance_value_abs NUMERIC(18, 2),
  created_by UUID REFERENCES auth.users (id),
  first_approver_id UUID REFERENCES auth.users (id),
  second_approver_id UUID REFERENCES auth.users (id),
  first_approved_at TIMESTAMPTZ,
  second_approved_at TIMESTAMPTZ,
  journal_entry_id UUID REFERENCES public.journal_entries (id),
  reversal_journal_entry_id UUID REFERENCES public.journal_entries (id),
  posted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT stock_recon_dual_auth_distinct CHECK (
    second_approver_id IS NULL
    OR first_approver_id IS NULL
    OR first_approver_id <> second_approver_id
  ),
  CONSTRAINT stock_recon_exchange_rate CHECK (
    currency = 'USD'
    OR (exchange_rate_applied IS NOT NULL AND exchange_rate_applied > 0)
  )
);

CREATE TABLE public.stock_reconciliation_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  stock_reconciliation_id UUID NOT NULL
    REFERENCES public.stock_reconciliations (id) ON DELETE CASCADE,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE RESTRICT,
  system_qty NUMERIC(18, 3) NOT NULL DEFAULT 0 CHECK (system_qty >= 0),
  counted_qty NUMERIC(18, 3) NOT NULL DEFAULT 0 CHECK (counted_qty >= 0),
  variance_qty NUMERIC(18, 3) GENERATED ALWAYS AS (counted_qty - system_qty) STORED,
  unit_cost NUMERIC(18, 4) NOT NULL DEFAULT 0 CHECK (unit_cost >= 0),
  currency public.currency_code NOT NULL DEFAULT 'USD',
  valuation_method public.valuation_method NOT NULL DEFAULT 'FIFO',
  stock_batch_id UUID REFERENCES public.stock_batches (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (stock_reconciliation_id, stock_item_id)
);

CREATE INDEX stock_reconciliations_wh_status_idx
  ON public.stock_reconciliations (warehouse_id, status);

CREATE INDEX stock_reconciliation_lines_recon_idx
  ON public.stock_reconciliation_lines (stock_reconciliation_id);

-- ---------------------------------------------------------------------------
-- Internal journal helpers (warehouse RPCs; skip finance-only gate on post/reverse)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._post_journal_entry_inventory(
  p_entry_date DATE,
  p_description TEXT,
  p_currency public.currency_code,
  p_exchange_rate NUMERIC,
  p_lines JSONB
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_line JSONB;
  v_doc TEXT;
  v_date DATE := COALESCE(p_entry_date, CURRENT_DATE);
BEGIN
  IF public.is_period_locked(v_date) THEN
    RAISE EXCEPTION 'accounting period is locked for date %', v_date;
  END IF;

  IF p_lines IS NULL OR jsonb_typeof(p_lines) <> 'array' OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'journal lines required';
  END IF;

  INSERT INTO public.journal_entries (
    entry_date, description, currency, exchange_rate_applied, status, posted_by, posted_at
  )
  VALUES (
    v_date,
    p_description,
    p_currency,
    CASE WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1) ELSE p_exchange_rate END,
    'draft',
    auth.uid(),
    now()
  )
  RETURNING id INTO v_id;

  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    INSERT INTO public.journal_entry_lines (
      journal_entry_id, account_code, debit, credit, currency
    )
    VALUES (
      v_id,
      v_line ->> 'account_code',
      COALESCE((v_line ->> 'debit')::numeric, 0),
      COALESCE((v_line ->> 'credit')::numeric, 0),
      COALESCE((v_line ->> 'currency')::public.currency_code, p_currency)
    );
  END LOOP;

  PERFORM public._assert_journal_balanced(v_id);
  v_doc := public.next_series_value('JV-');

  UPDATE public.journal_entries
  SET
    status = 'posted',
    posted_at = now(),
    posted_by = COALESCE(auth.uid(), posted_by),
    document_number = v_doc
  WHERE id = v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public._reverse_journal_inventory(
  p_entry_id UUID,
  p_description TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_src public.journal_entries%ROWTYPE;
  v_new_id UUID;
  v_line RECORD;
  v_desc TEXT;
BEGIN
  SELECT * INTO v_src FROM public.journal_entries WHERE id = p_entry_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'journal entry not found: %', p_entry_id;
  END IF;
  IF v_src.status <> 'posted' THEN
    RAISE EXCEPTION 'only posted journals can be reversed';
  END IF;
  IF public.is_period_locked(CURRENT_DATE) THEN
    RAISE EXCEPTION 'accounting period is locked for today';
  END IF;

  v_desc := COALESCE(
    p_description,
    format('Reversal of %s', COALESCE(v_src.document_number, v_src.id::text))
  );

  INSERT INTO public.journal_entries (
    entry_date, description, currency, exchange_rate_applied,
    status, is_reversal, reverses_entry_id, posted_by, posted_at, document_number
  )
  VALUES (
    CURRENT_DATE,
    v_desc,
    v_src.currency,
    v_src.exchange_rate_applied,
    'draft',
    true,
    v_src.id,
    auth.uid(),
    now(),
    public.next_series_value('JV-')
  )
  RETURNING id INTO v_new_id;

  FOR v_line IN
    SELECT account_code, debit, credit, currency
    FROM public.journal_entry_lines
    WHERE journal_entry_id = p_entry_id
  LOOP
    INSERT INTO public.journal_entry_lines (
      journal_entry_id, account_code, debit, credit, currency
    )
    VALUES (
      v_new_id, v_line.account_code, v_line.credit, v_line.debit, v_line.currency
    );
  END LOOP;

  PERFORM public._assert_journal_balanced(v_new_id);

  UPDATE public.journal_entries
  SET status = 'posted', posted_at = now(), posted_by = COALESCE(auth.uid(), posted_by)
  WHERE id = v_new_id;

  RETURN v_new_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Variance helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._recon_compute_variance_value(p_reconciliation_id UUID)
RETURNS NUMERIC
LANGUAGE sql
STABLE
SET search_path = public
AS $$
  SELECT COALESCE(
    round(
      SUM(abs(l.variance_qty) * l.unit_cost),
      2
    ),
    0
  )
  FROM public.stock_reconciliation_lines l
  WHERE l.stock_reconciliation_id = p_reconciliation_id
    AND l.variance_qty <> 0;
$$;

CREATE OR REPLACE FUNCTION public._recon_assert_single_currency(p_reconciliation_id UUID)
RETURNS void
LANGUAGE plpgsql
STABLE
SET search_path = public
AS $$
DECLARE
  v_header public.currency_code;
  v_bad INT;
BEGIN
  SELECT currency INTO v_header
  FROM public.stock_reconciliations
  WHERE id = p_reconciliation_id;

  SELECT count(DISTINCT l.currency) INTO v_bad
  FROM public.stock_reconciliation_lines l
  WHERE l.stock_reconciliation_id = p_reconciliation_id;

  IF v_bad > 1 THEN
    RAISE EXCEPTION 'mixed line currencies not allowed on reconciliation %', p_reconciliation_id;
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.stock_reconciliation_lines l
    WHERE l.stock_reconciliation_id = p_reconciliation_id
      AND l.currency IS DISTINCT FROM v_header
  ) THEN
    RAISE EXCEPTION 'line currency must match reconciliation header currency %', v_header;
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Post stock + journal (internal)
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- RPC: create draft
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- RPC: upsert lines (draft only)
-- p_lines: [{stock_item_id, counted_qty}]
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- RPC: submit
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- RPC: approve (second distinct staff)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.approve_stock_reconciliation(p_reconciliation_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_recon public.stock_reconciliations%ROWTYPE;
BEGIN
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

-- ---------------------------------------------------------------------------
-- RPC: cancel posted (reverse stock + journal)
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- Naming series
-- ---------------------------------------------------------------------------
INSERT INTO public.naming_series (prefix, description, pad_length) VALUES
  ('SRE-', 'Stock reconciliation', 5)
ON CONFLICT (prefix) DO NOTHING;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stock_reconciliations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stock_reconciliation_lines ENABLE ROW LEVEL SECURITY;

CREATE POLICY app_settings_select_staff
  ON public.app_settings FOR SELECT TO authenticated
  USING (public.is_staff());

CREATE POLICY app_settings_write_admin
  ON public.app_settings FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin']::public.staff_role[]));

CREATE POLICY stock_recon_select_staff
  ON public.stock_reconciliations FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY stock_recon_write_wh
  ON public.stock_reconciliations FOR INSERT TO authenticated
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY stock_recon_update_wh
  ON public.stock_reconciliations FOR UPDATE TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

CREATE POLICY stock_recon_lines_select_staff
  ON public.stock_reconciliation_lines FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'warehouse', 'finance']::public.staff_role[])
  );

CREATE POLICY stock_recon_lines_write_wh
  ON public.stock_reconciliation_lines FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'warehouse']::public.staff_role[]));

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public._recon_dual_auth_threshold(public.currency_code) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._post_journal_entry_inventory(DATE, TEXT, public.currency_code, NUMERIC, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._reverse_journal_inventory(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._recon_compute_variance_value(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._recon_assert_single_currency(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._post_stock_reconciliation(UUID) FROM PUBLIC;

REVOKE ALL ON FUNCTION public.create_stock_reconciliation_draft(
  UUID, public.stock_reconciliation_scope, UUID[], TEXT, public.currency_code, NUMERIC
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.upsert_stock_reconciliation_lines(UUID, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.submit_stock_reconciliation(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.approve_stock_reconciliation(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.cancel_stock_reconciliation(UUID, TEXT) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.create_stock_reconciliation_draft(
  UUID, public.stock_reconciliation_scope, UUID[], TEXT, public.currency_code, NUMERIC
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.upsert_stock_reconciliation_lines(UUID, JSONB) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_stock_reconciliation(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.approve_stock_reconciliation(UUID) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.cancel_stock_reconciliation(UUID, TEXT) TO authenticated, service_role;
