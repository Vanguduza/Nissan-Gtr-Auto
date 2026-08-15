-- Petty cash statement ops: float requisition JE, auto-disburse on final
-- approve, intended entry date, receipt Storage + bind RPC.
-- No ZIMRA. Ledger remains append-only.

-- ---------------------------------------------------------------------------
-- intended_entry_date on requisitions (expense / float forms)
-- ---------------------------------------------------------------------------
ALTER TABLE public.finance_requisitions
  ADD COLUMN IF NOT EXISTS intended_entry_date DATE;

COMMENT ON COLUMN public.finance_requisitions.intended_entry_date IS
  'Preferred journal entry_date when disbursed; defaults to CURRENT_DATE.';

-- ---------------------------------------------------------------------------
-- Receipt attachments
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.finance_requisition_receipts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  requisition_id UUID NOT NULL REFERENCES public.finance_requisitions (id) ON DELETE CASCADE,
  storage_path TEXT NOT NULL,
  content_type TEXT,
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT finance_req_receipts_path_unique UNIQUE (storage_path)
);

CREATE INDEX IF NOT EXISTS finance_req_receipts_req_idx
  ON public.finance_requisition_receipts (requisition_id);

ALTER TABLE public.finance_requisition_receipts ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS finance_req_receipts_select ON public.finance_requisition_receipts;
CREATE POLICY finance_req_receipts_select
  ON public.finance_requisition_receipts FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1
      FROM public.finance_requisitions r
      WHERE r.id = finance_requisition_receipts.requisition_id
        AND (
          r.requested_by = auth.uid()
          OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
          OR (
            public.is_staff()
            AND r.status IN (
              'submitted', 'approved', 'rejected', 'disbursed', 'cancelled'
            )
          )
        )
    )
  );

REVOKE ALL ON TABLE public.finance_requisition_receipts FROM PUBLIC, anon;
GRANT SELECT ON TABLE public.finance_requisition_receipts TO authenticated, service_role;
GRANT ALL ON TABLE public.finance_requisition_receipts TO service_role;

-- Storage bucket
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'petty-cash-receipts',
  'petty-cash-receipts',
  false,
  5242880,
  ARRAY['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
)
ON CONFLICT (id) DO UPDATE
SET
  file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

CREATE OR REPLACE FUNCTION public._finance_req_id_from_receipt_path(p_name TEXT)
RETURNS UUID
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
  v_seg TEXT;
  v_id UUID;
BEGIN
  IF p_name IS NULL OR length(trim(p_name)) = 0 THEN
    RETURN NULL;
  END IF;
  v_seg := trim(both '/' FROM p_name);
  IF split_part(v_seg, '/', 1) = 'petty-cash-receipts' THEN
    v_seg := substr(v_seg, length('petty-cash-receipts/') + 1);
  END IF;
  v_seg := split_part(v_seg, '/', 1);
  BEGIN
    v_id := v_seg::uuid;
  EXCEPTION
    WHEN invalid_text_representation THEN
      RETURN NULL;
  END;
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public._can_access_petty_cash_receipt_object(p_name TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_req UUID := public._finance_req_id_from_receipt_path(p_name);
BEGIN
  IF auth.role() = 'service_role' THEN
    RETURN true;
  END IF;
  IF v_req IS NULL OR auth.uid() IS NULL THEN
    RETURN false;
  END IF;
  RETURN EXISTS (
    SELECT 1
    FROM public.finance_requisitions r
    WHERE r.id = v_req
      AND (
        r.requested_by = auth.uid()
        OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
        OR public.is_staff()
      )
  );
END;
$$;

DROP POLICY IF EXISTS petty_cash_receipts_storage_select ON storage.objects;
CREATE POLICY petty_cash_receipts_storage_select
  ON storage.objects FOR SELECT TO authenticated
  USING (
    bucket_id = 'petty-cash-receipts'
    AND public._can_access_petty_cash_receipt_object(name)
  );

DROP POLICY IF EXISTS petty_cash_receipts_storage_insert ON storage.objects;
CREATE POLICY petty_cash_receipts_storage_insert
  ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'petty-cash-receipts'
    AND public._can_access_petty_cash_receipt_object(name)
  );

DROP POLICY IF EXISTS petty_cash_receipts_storage_delete ON storage.objects;
CREATE POLICY petty_cash_receipts_storage_delete
  ON storage.objects FOR DELETE TO authenticated
  USING (
    bucket_id = 'petty-cash-receipts'
    AND public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  );

-- ---------------------------------------------------------------------------
-- create_finance_requisition — petty_float defaults (cash 1110, funding line)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_finance_requisition(
  p_req_type public.finance_requisition_type,
  p_amount NUMERIC,
  p_currency public.currency_code,
  p_payee TEXT DEFAULT NULL,
  p_memo TEXT DEFAULT NULL,
  p_expense_account_code VARCHAR(10) DEFAULT '5300',
  p_cash_account_code VARCHAR(10) DEFAULT NULL,
  p_exchange_rate NUMERIC DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_cash VARCHAR(10);
  v_uid UUID := auth.uid();
  v_expense VARCHAR(10);
  v_fund VARCHAR(10) := public.petty_cash_funding_account_code();
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'auth.uid() required to create requisition';
  END IF;

  IF NOT (
    auth.role() = 'service_role'
    OR public.is_staff()
    OR public.has_staff_role(
      ARRAY['admin', 'finance', 'sales', 'warehouse', 'dispatcher', 'hr']::public.staff_role[]
    )
  ) THEN
    RAISE EXCEPTION 'staff role required to create requisition';
  END IF;

  IF p_amount IS NULL OR p_amount <= 0 THEN
    RAISE EXCEPTION 'amount must be > 0';
  END IF;

  IF p_currency = 'ZIG' AND (p_exchange_rate IS NULL OR p_exchange_rate <= 0) THEN
    RAISE EXCEPTION 'exchange_rate_applied required for ZIG requisitions';
  END IF;

  IF p_req_type = 'petty_float' THEN
    v_cash := '1110';
    v_expense := COALESCE(nullif(trim(p_expense_account_code), ''), v_fund);
  ELSE
    v_expense := COALESCE(nullif(trim(p_expense_account_code), ''), '5300');
    v_cash := COALESCE(
      nullif(trim(p_cash_account_code), ''),
      CASE
        WHEN p_req_type = 'petty_cash' THEN '1110'
        ELSE v_fund
      END
    );
    IF p_req_type = 'payment' AND v_cash = '1110' THEN
      v_cash := v_fund;
    END IF;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.chart_of_accounts c
    WHERE c.code = v_expense AND c.is_active
  ) THEN
    RAISE EXCEPTION 'expense account not found: %', v_expense;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.chart_of_accounts c
    WHERE c.code = v_cash AND c.is_active
  ) THEN
    RAISE EXCEPTION 'cash account not found: %', v_cash;
  END IF;

  PERFORM public._finance_req_rpc_enter();

  INSERT INTO public.finance_requisitions (
    req_type, status, amount, currency, exchange_rate_applied,
    payee, memo, expense_account_code, cash_account_code, requested_by
  )
  VALUES (
    p_req_type,
    'draft',
    p_amount,
    p_currency,
    CASE
      WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1)
      ELSE p_exchange_rate
    END,
    nullif(trim(p_payee), ''),
    nullif(trim(p_memo), ''),
    v_expense,
    v_cash,
    v_uid
  )
  RETURNING id INTO v_id;

  INSERT INTO public.finance_requisition_lines (
    requisition_id, line_no, description, expense_account_code, amount
  ) VALUES (
    v_id,
    1,
    COALESCE(nullif(trim(p_memo), ''), nullif(trim(p_payee), ''), 'Requisition'),
    v_expense,
    p_amount
  );

  RETURN v_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- disburse — petty_float = Dr 1110 / Cr funding; else expense lines / Cr cash
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.disburse_finance_requisition(
  p_requisition_id UUID,
  p_entry_date DATE DEFAULT CURRENT_DATE
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.finance_requisitions%ROWTYPE;
  v_je UUID;
  v_desc TEXT;
  v_rate NUMERIC;
  v_lines JSONB := '[]'::jsonb;
  v_line RECORD;
  v_line_sum NUMERIC(18, 2) := 0;
  v_entry_date DATE;
  v_fund VARCHAR(10);
BEGIN
  SELECT * INTO v_row
  FROM public.finance_requisitions
  WHERE id = p_requisition_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'requisition not found: %', p_requisition_id;
  END IF;

  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
    -- Final approver may auto-disburse petty box requests from approve().
    OR (
      v_row.status = 'approved'
      AND v_row.approved_by IS NOT NULL
      AND v_row.approved_by = auth.uid()
    )
  ) THEN
    RAISE EXCEPTION 'finance or admin role required to disburse';
  END IF;

  IF v_row.status <> 'approved' THEN
    RAISE EXCEPTION 'disburse requires approved status (got %); cannot skip approval', v_row.status;
  END IF;
  IF v_row.journal_entry_id IS NOT NULL THEN
    RAISE EXCEPTION 'requisition already disbursed';
  END IF;
  IF v_row.currency = 'ZIG'
     AND (v_row.exchange_rate_applied IS NULL OR v_row.exchange_rate_applied <= 0) THEN
    RAISE EXCEPTION 'exchange_rate_applied required to disburse ZIG requisition';
  END IF;

  v_entry_date := COALESCE(v_row.intended_entry_date, p_entry_date, CURRENT_DATE);
  v_rate := CASE
    WHEN v_row.currency = 'USD' THEN COALESCE(v_row.exchange_rate_applied, 1)
    ELSE v_row.exchange_rate_applied
  END;
  v_desc := format(
    'Disburse %s %s — %s',
    COALESCE(v_row.document_number, p_requisition_id::text),
    v_row.req_type::text,
    COALESCE(v_row.payee, v_row.memo, 'requisition')
  );

  IF v_row.req_type = 'petty_float' THEN
    v_fund := COALESCE(
      nullif(trim(v_row.expense_account_code), ''),
      public.petty_cash_funding_account_code()
    );
    v_lines := jsonb_build_array(
      jsonb_build_object(
        'account_code', '1110',
        'debit', v_row.amount,
        'credit', 0,
        'currency', v_row.currency
      ),
      jsonb_build_object(
        'account_code', v_fund,
        'debit', 0,
        'credit', v_row.amount,
        'currency', v_row.currency
      )
    );
  ELSE
    FOR v_line IN
      SELECT line_no, description, expense_account_code, amount
      FROM public.finance_requisition_lines
      WHERE requisition_id = p_requisition_id
      ORDER BY line_no
    LOOP
      v_line_sum := v_line_sum + v_line.amount;
      v_lines := v_lines || jsonb_build_array(
        jsonb_build_object(
          'account_code', v_line.expense_account_code,
          'debit', v_line.amount,
          'credit', 0,
          'currency', v_row.currency
        )
      );
    END LOOP;

    IF jsonb_array_length(v_lines) < 1 THEN
      RAISE EXCEPTION 'cannot disburse requisition without lines';
    END IF;
    IF v_line_sum IS DISTINCT FROM v_row.amount THEN
      RAISE EXCEPTION 'header amount % does not match line total %', v_row.amount, v_line_sum;
    END IF;

    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'account_code', v_row.cash_account_code,
        'debit', 0,
        'credit', v_row.amount,
        'currency', v_row.currency
      )
    );
  END IF;

  v_je := public.post_journal_entry(
    v_entry_date,
    v_desc,
    v_row.currency,
    v_rate,
    v_lines
  );

  PERFORM public._finance_req_rpc_enter();

  UPDATE public.finance_requisitions
  SET
    status = 'disbursed',
    disbursed_by = auth.uid(),
    disbursed_at = now(),
    journal_entry_id = v_je,
    payment_entry_id = NULL,
    updated_at = now()
  WHERE id = p_requisition_id;

  RETURN v_je;
END;
$$;

-- ---------------------------------------------------------------------------
-- approve — auto-disburse petty_cash + petty_float on final approval
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.approve_finance_requisition(
  p_requisition_id UUID,
  p_note TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.finance_requisitions%ROWTYPE;
  v_uid UUID := auth.uid();
  v_new_count INT;
  v_final BOOLEAN := false;
BEGIN
  IF v_uid IS NULL AND auth.role() <> 'service_role' THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;
  IF auth.role() = 'service_role' AND v_uid IS NULL THEN
    v_uid := 'a0000000-0000-4000-8000-000000000002'::uuid;
  END IF;

  SELECT * INTO v_row
  FROM public.finance_requisitions
  WHERE id = p_requisition_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'requisition not found: %', p_requisition_id;
  END IF;
  IF v_row.status <> 'submitted' THEN
    RAISE EXCEPTION 'only submitted requisitions can be approved (status=%)', v_row.status;
  END IF;

  IF NOT public._finance_req_can_approve(v_row) THEN
    RAISE EXCEPTION
      'not allowed to approve (need finance/admin or organogram reporting line)';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.finance_requisition_approvals
    WHERE requisition_id = p_requisition_id
      AND approver_user_id = v_uid
  ) THEN
    RAISE EXCEPTION 'already approved by this user';
  END IF;

  PERFORM public._finance_req_rpc_enter();

  INSERT INTO public.finance_requisition_approvals (
    requisition_id, approver_user_id, note
  ) VALUES (
    p_requisition_id, v_uid, nullif(trim(p_note), '')
  );

  v_new_count := COALESCE(v_row.approval_count, 0) + 1;
  v_final := v_new_count >= COALESCE(v_row.required_approvals, 1);

  UPDATE public.finance_requisitions
  SET
    approval_count = v_new_count,
    status = CASE WHEN v_final THEN 'approved'::public.finance_requisition_status ELSE status END,
    approved_by = CASE WHEN v_final THEN v_uid ELSE approved_by END,
    approved_at = CASE WHEN v_final THEN now() ELSE approved_at END,
    updated_at = now()
  WHERE id = p_requisition_id;

  INSERT INTO public.finance_audit_log (
    actor_user_id, action, entity_type, entity_id, before_state, after_state
  ) VALUES (
    v_uid,
    CASE WHEN v_final THEN 'requisition_approved' ELSE 'requisition_approval_partial' END,
    'finance_requisitions',
    p_requisition_id,
    jsonb_build_object(
      'status', v_row.status,
      'approval_count', v_row.approval_count,
      'required_approvals', v_row.required_approvals
    ),
    jsonb_build_object(
      'status', CASE WHEN v_final THEN 'approved' ELSE 'submitted' END,
      'approval_count', v_new_count,
      'required_approvals', v_row.required_approvals,
      'final', v_final
    )
  );

  -- Petty box: final approval posts the ledger automatically.
  IF v_final AND v_row.req_type IN (
    'petty_cash'::public.finance_requisition_type,
    'petty_float'::public.finance_requisition_type
  ) THEN
    PERFORM public.disburse_finance_requisition(
      p_requisition_id,
      COALESCE(v_row.intended_entry_date, CURRENT_DATE)
    );
  END IF;

  RETURN p_requisition_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Convenience: create + date + optional receipt path + submit
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_petty_cash_expense_request(
  p_amount NUMERIC,
  p_currency public.currency_code,
  p_description TEXT,
  p_entry_date DATE DEFAULT CURRENT_DATE,
  p_expense_account_code VARCHAR(10) DEFAULT '5300',
  p_exchange_rate NUMERIC DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  v_id := public.create_finance_requisition(
    'petty_cash'::public.finance_requisition_type,
    p_amount,
    p_currency,
    NULL,
    p_description,
    COALESCE(nullif(trim(p_expense_account_code), ''), '5300'),
    '1110',
    p_exchange_rate
  );

  PERFORM public._finance_req_rpc_enter();
  UPDATE public.finance_requisitions
  SET intended_entry_date = COALESCE(p_entry_date, CURRENT_DATE),
      updated_at = now()
  WHERE id = v_id;

  -- Draft only — caller attaches optional receipt then submit_finance_requisition.
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_petty_cash_float_request(
  p_amount NUMERIC,
  p_currency public.currency_code,
  p_description TEXT,
  p_entry_date DATE DEFAULT CURRENT_DATE,
  p_exchange_rate NUMERIC DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_fund VARCHAR(10) := public.petty_cash_funding_account_code();
BEGIN
  v_id := public.create_finance_requisition(
    'petty_float'::public.finance_requisition_type,
    p_amount,
    p_currency,
    NULL,
    p_description,
    v_fund,
    '1110',
    p_exchange_rate
  );

  PERFORM public._finance_req_rpc_enter();
  UPDATE public.finance_requisitions
  SET intended_entry_date = COALESCE(p_entry_date, CURRENT_DATE),
      updated_at = now()
  WHERE id = v_id;

  -- Draft only — caller submits into the approval → auto-disburse path.
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.attach_finance_requisition_receipt(
  p_requisition_id UUID,
  p_storage_path TEXT,
  p_content_type TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_row public.finance_requisitions%ROWTYPE;
  v_path TEXT := trim(both '/' FROM coalesce(p_storage_path, ''));
  v_id UUID;
BEGIN
  IF auth.uid() IS NULL AND auth.role() <> 'service_role' THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;

  SELECT * INTO v_row
  FROM public.finance_requisitions
  WHERE id = p_requisition_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'requisition not found: %', p_requisition_id;
  END IF;

  IF v_row.requested_by <> auth.uid()
     AND NOT public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
     AND auth.role() <> 'service_role' THEN
    RAISE EXCEPTION 'not allowed to attach receipt';
  END IF;

  IF left(v_path, length('petty-cash-receipts/')) = 'petty-cash-receipts/' THEN
    v_path := substr(v_path, length('petty-cash-receipts/') + 1);
  END IF;

  IF public._finance_req_id_from_receipt_path(v_path) IS DISTINCT FROM p_requisition_id THEN
    RAISE EXCEPTION 'storage path must start with requisition id';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM storage.objects o
    WHERE o.bucket_id = 'petty-cash-receipts'
      AND o.name = v_path
  ) THEN
    RAISE EXCEPTION 'receipt object missing in petty-cash-receipts at %', v_path;
  END IF;

  INSERT INTO public.finance_requisition_receipts (
    requisition_id, storage_path, content_type, created_by
  ) VALUES (
    p_requisition_id,
    v_path,
    nullif(trim(p_content_type), ''),
    auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

-- Threshold seed for float (same as petty_cash defaults)
INSERT INTO public.finance_requisition_thresholds (
  req_type, currency, min_amount, required_approvals
)
VALUES
  ('petty_float', 'USD', 0, 1),
  ('petty_float', 'ZIG', 0, 1)
ON CONFLICT (req_type, currency, min_amount) DO NOTHING;

REVOKE ALL ON FUNCTION public._finance_req_id_from_receipt_path(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public._can_access_petty_cash_receipt_object(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_petty_cash_expense_request(
  NUMERIC, public.currency_code, TEXT, DATE, VARCHAR, NUMERIC
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_petty_cash_float_request(
  NUMERIC, public.currency_code, TEXT, DATE, NUMERIC
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.attach_finance_requisition_receipt(UUID, TEXT, TEXT)
  FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.create_finance_requisition(
  public.finance_requisition_type, NUMERIC, public.currency_code,
  TEXT, TEXT, VARCHAR, VARCHAR, NUMERIC
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.disburse_finance_requisition(UUID, DATE)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.approve_finance_requisition(UUID, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_petty_cash_expense_request(
  NUMERIC, public.currency_code, TEXT, DATE, VARCHAR, NUMERIC
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.create_petty_cash_float_request(
  NUMERIC, public.currency_code, TEXT, DATE, NUMERIC
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.attach_finance_requisition_receipt(UUID, TEXT, TEXT)
  TO authenticated, service_role;
