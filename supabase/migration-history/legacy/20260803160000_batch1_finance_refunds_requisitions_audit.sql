-- Batch 1 §3.1 / §3.5–3.10 — refunds (reversing + original link), requisition types,
-- threshold multi-approver, finance_audit_log + RLS, human refs via next_series_value.
-- No ZIMRA. Ledger append-only (reverse_journal only).

-- Enum extensions (salary/refund/asset_capex/vendor) live in
-- 20260803155000_batch1_finance_requisition_enum_extend.sql (same-txn PG rule).

-- ---------------------------------------------------------------------------
-- Approval thresholds (amount → required approvals; organogram escalation later)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.finance_requisition_thresholds (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  req_type public.finance_requisition_type NOT NULL,
  currency public.currency_code NOT NULL DEFAULT 'USD',
  min_amount NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (min_amount >= 0),
  required_approvals INT NOT NULL DEFAULT 1 CHECK (required_approvals >= 1),
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (req_type, currency, min_amount)
);

CREATE TABLE IF NOT EXISTS public.finance_requisition_approvals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  requisition_id UUID NOT NULL REFERENCES public.finance_requisitions (id) ON DELETE CASCADE,
  approver_user_id UUID NOT NULL REFERENCES auth.users (id),
  approved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  note TEXT,
  UNIQUE (requisition_id, approver_user_id)
);

CREATE INDEX IF NOT EXISTS finance_req_approvals_req_idx
  ON public.finance_requisition_approvals (requisition_id);

ALTER TABLE public.finance_requisitions
  ADD COLUMN IF NOT EXISTS required_approvals INT NOT NULL DEFAULT 1
    CHECK (required_approvals >= 1),
  ADD COLUMN IF NOT EXISTS approval_count INT NOT NULL DEFAULT 0
    CHECK (approval_count >= 0);

-- ---------------------------------------------------------------------------
-- Finance audit log (append-only human-readable) — before refunds RPC
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.finance_audit_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id UUID REFERENCES auth.users (id),
  action TEXT NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id UUID,
  before_state JSONB,
  after_state JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS finance_audit_log_created_idx
  ON public.finance_audit_log (created_at DESC);
CREATE INDEX IF NOT EXISTS finance_audit_log_entity_idx
  ON public.finance_audit_log (entity_type, entity_id);

CREATE OR REPLACE FUNCTION public.guard_finance_audit_immutable()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'finance_audit_log is append-only';
END;
$$;

DROP TRIGGER IF EXISTS finance_audit_log_immutable ON public.finance_audit_log;
CREATE TRIGGER finance_audit_log_immutable
  BEFORE UPDATE OR DELETE ON public.finance_audit_log
  FOR EACH ROW EXECUTE FUNCTION public.guard_finance_audit_immutable();

CREATE OR REPLACE FUNCTION public.log_finance_audit(
  p_action TEXT,
  p_entity_type TEXT,
  p_entity_id UUID,
  p_before JSONB DEFAULT NULL,
  p_after JSONB DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;
  INSERT INTO public.finance_audit_log (
    actor_user_id, action, entity_type, entity_id, before_state, after_state
  ) VALUES (
    auth.uid(), p_action, p_entity_type, p_entity_id, p_before, p_after
  )
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Finance refunds — reversing JE linked to original sale
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.finance_refunds (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number TEXT UNIQUE,
  original_invoice_id UUID NOT NULL REFERENCES public.sales_invoices (id),
  reversing_journal_entry_id UUID NOT NULL REFERENCES public.journal_entries (id),
  currency public.currency_code NOT NULL,
  amount NUMERIC(18, 2) NOT NULL CHECK (amount > 0),
  exchange_rate_applied NUMERIC(18, 8) NOT NULL DEFAULT 1,
  notes TEXT,
  posted_by UUID REFERENCES auth.users (id),
  posted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS finance_refunds_invoice_idx
  ON public.finance_refunds (original_invoice_id);

CREATE OR REPLACE FUNCTION public.post_finance_refund(
  p_invoice_id UUID,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_inv public.sales_invoices%ROWTYPE;
  v_rev UUID;
  v_id UUID;
  v_doc TEXT;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  SELECT * INTO v_inv FROM public.sales_invoices WHERE id = p_invoice_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'invoice not found: %', p_invoice_id;
  END IF;
  IF v_inv.status <> 'posted' OR v_inv.doc_type <> 'invoice' THEN
    RAISE EXCEPTION 'only posted sales invoices can be refunded';
  END IF;
  IF v_inv.journal_entry_id IS NULL THEN
    RAISE EXCEPTION 'invoice has no journal entry to reverse';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.finance_refunds WHERE original_invoice_id = p_invoice_id
  ) THEN
    RAISE EXCEPTION 'refund already posted for invoice %', p_invoice_id;
  END IF;

  v_rev := public.reverse_journal(
    v_inv.journal_entry_id,
    COALESCE(p_notes, format('Refund of invoice %s', COALESCE(v_inv.document_number, v_inv.id::text)))
  );

  v_doc := public.next_series_value('REF-');

  INSERT INTO public.finance_refunds (
    document_number, original_invoice_id, reversing_journal_entry_id,
    currency, amount, exchange_rate_applied, notes, posted_by
  ) VALUES (
    v_doc, p_invoice_id, v_rev,
    v_inv.currency, v_inv.total, v_inv.exchange_rate_applied, p_notes, auth.uid()
  )
  RETURNING id INTO v_id;

  INSERT INTO public.finance_audit_log (
    actor_user_id, action, entity_type, entity_id, before_state, after_state
  ) VALUES (
    auth.uid(),
    'refund_posted',
    'finance_refunds',
    v_id,
    jsonb_build_object('invoice_id', p_invoice_id, 'journal_entry_id', v_inv.journal_entry_id),
    jsonb_build_object(
      'refund_id', v_id,
      'document_number', v_doc,
      'reversing_journal_entry_id', v_rev
    )
  );

  RETURN v_id;
END;
$$;

-- Resolve required_approvals from thresholds when submitting
CREATE OR REPLACE FUNCTION public._finance_req_required_approvals(
  p_req_type public.finance_requisition_type,
  p_currency public.currency_code,
  p_amount NUMERIC
)
RETURNS INT
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(
    (
      SELECT t.required_approvals
      FROM public.finance_requisition_thresholds t
      WHERE t.is_active
        AND t.req_type = p_req_type
        AND t.currency = p_currency
        AND t.min_amount <= p_amount
      ORDER BY t.min_amount DESC
      LIMIT 1
    ),
    1
  );
$$;

-- Seed default thresholds (single approver under 500; dual at/above)
INSERT INTO public.finance_requisition_thresholds (req_type, currency, min_amount, required_approvals)
VALUES
  ('petty_cash', 'USD', 0, 1),
  ('petty_cash', 'USD', 500, 2),
  ('payment', 'USD', 0, 1),
  ('payment', 'USD', 1000, 2),
  ('refund', 'USD', 0, 1),
  ('salary', 'USD', 0, 2),
  ('asset_capex', 'USD', 0, 2),
  ('vendor', 'USD', 0, 1)
ON CONFLICT (req_type, currency, min_amount) DO NOTHING;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.finance_requisition_thresholds ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.finance_requisition_approvals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.finance_refunds ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.finance_audit_log ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS finance_req_thresholds_select ON public.finance_requisition_thresholds;
CREATE POLICY finance_req_thresholds_select ON public.finance_requisition_thresholds
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

DROP POLICY IF EXISTS finance_req_thresholds_write ON public.finance_requisition_thresholds;
CREATE POLICY finance_req_thresholds_write ON public.finance_requisition_thresholds
  FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin']::public.staff_role[]));

DROP POLICY IF EXISTS finance_req_approvals_select ON public.finance_requisition_approvals;
CREATE POLICY finance_req_approvals_select ON public.finance_requisition_approvals
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

DROP POLICY IF EXISTS finance_req_approvals_insert ON public.finance_requisition_approvals;
CREATE POLICY finance_req_approvals_insert ON public.finance_requisition_approvals
  FOR INSERT TO authenticated
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
    AND approver_user_id = auth.uid()
  );

DROP POLICY IF EXISTS finance_refunds_select ON public.finance_refunds;
CREATE POLICY finance_refunds_select ON public.finance_refunds
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

DROP POLICY IF EXISTS finance_refunds_insert ON public.finance_refunds;
CREATE POLICY finance_refunds_insert ON public.finance_refunds
  FOR INSERT TO authenticated
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

DROP POLICY IF EXISTS finance_audit_select ON public.finance_audit_log;
CREATE POLICY finance_audit_select ON public.finance_audit_log
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

DROP POLICY IF EXISTS finance_audit_insert ON public.finance_audit_log;
CREATE POLICY finance_audit_insert ON public.finance_audit_log
  FOR INSERT TO authenticated
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[]));

REVOKE ALL ON FUNCTION public.post_finance_refund(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.log_finance_audit(TEXT, TEXT, UUID, JSONB, JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.post_finance_refund(UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.log_finance_audit(TEXT, TEXT, UUID, JSONB, JSONB)
  TO authenticated, service_role;
