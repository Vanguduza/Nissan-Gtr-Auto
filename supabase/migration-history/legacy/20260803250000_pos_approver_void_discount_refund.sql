-- Tablet POS Phase 5 — Admin|shop-manager void / discount / refund.
-- Shop manager = organogram grade A1|A2|B1 OR hr_roles.approval_flags.pos_manager,
-- OR staff_role admin. Refunds post through post_finance_refund (no parallel POS ledger).
-- No ZIMRA.

ALTER TABLE public.hr_roles
  ADD COLUMN IF NOT EXISTS approval_flags JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN public.hr_roles.approval_flags IS
  'Action gates e.g. {"pos_manager": true} for void/discount/refund approval.';

-- Mark B1 "Shop & warehouse manager" organogram roles as POS approvers when present.
UPDATE public.hr_roles r
SET approval_flags = COALESCE(r.approval_flags, '{}'::jsonb) || '{"pos_manager": true}'::jsonb,
    updated_at = now()
FROM public.hr_grades g
WHERE r.grade_id = g.id
  AND g.code = 'B1'
  AND r.is_active
  AND COALESCE(r.approval_flags->>'pos_manager', 'false') <> 'true';

CREATE TABLE IF NOT EXISTS public.pos_action_audit (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id UUID REFERENCES auth.users (id),
  action TEXT NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id UUID,
  before_state JSONB,
  after_state JSONB,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS pos_action_audit_actor_idx
  ON public.pos_action_audit (actor_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS pos_action_audit_entity_idx
  ON public.pos_action_audit (entity_type, entity_id);

ALTER TABLE public.pos_action_audit ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS pos_action_audit_select ON public.pos_action_audit;
CREATE POLICY pos_action_audit_select ON public.pos_action_audit
  FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
  );

DROP POLICY IF EXISTS pos_action_audit_insert ON public.pos_action_audit;
CREATE POLICY pos_action_audit_insert ON public.pos_action_audit
  FOR INSERT TO authenticated
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
    AND actor_user_id = auth.uid()
  );

CREATE OR REPLACE FUNCTION public.is_pos_approver()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    public.has_staff_role(ARRAY['admin']::public.staff_role[])
    OR EXISTS (
      SELECT 1
      FROM public.employees e
      LEFT JOIN public.hr_grades g ON g.id = e.grade_id
      LEFT JOIN public.hr_roles r ON r.id = e.hr_role_id
      WHERE e.user_id = auth.uid()
        AND e.status = 'active'
        AND (
          g.code IN ('A1', 'A2', 'B1')
          OR COALESCE(r.approval_flags->>'pos_manager', 'false') = 'true'
        )
    );
$$;

COMMENT ON FUNCTION public.is_pos_approver() IS
  'Admin staff_role OR active employee with grade A1/A2/B1 or hr_roles.approval_flags.pos_manager.';

CREATE OR REPLACE FUNCTION public._log_pos_action(
  p_action TEXT,
  p_entity_type TEXT,
  p_entity_id UUID,
  p_before JSONB DEFAULT NULL,
  p_after JSONB DEFAULT NULL,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  INSERT INTO public.pos_action_audit (
    actor_user_id, action, entity_type, entity_id, before_state, after_state, notes
  ) VALUES (
    auth.uid(), p_action, p_entity_type, p_entity_id, p_before, p_after, p_notes
  )
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;

-- Cart-level percent discount on non-core open lines (manager gate).
CREATE OR REPLACE FUNCTION public.apply_pos_cart_discount(
  p_cart_id UUID,
  p_discount_percent NUMERIC,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cart public.pos_carts%ROWTYPE;
  v_before JSONB;
  v_after JSONB;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;
  IF NOT public.is_pos_approver() THEN
    RAISE EXCEPTION 'admin or shop manager approval required';
  END IF;
  IF p_discount_percent IS NULL
     OR p_discount_percent < 0
     OR p_discount_percent > 100 THEN
    RAISE EXCEPTION 'discount percent must be between 0 and 100';
  END IF;

  PERFORM public._require_cart_mutate(p_cart_id);
  SELECT * INTO v_cart FROM public.pos_carts WHERE id = p_cart_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'cart not found: %', p_cart_id;
  END IF;
  IF v_cart.status <> 'open' THEN
    RAISE EXCEPTION 'only open carts can be discounted (got %)', v_cart.status;
  END IF;

  SELECT COALESCE(jsonb_agg(jsonb_build_object(
    'id', l.id,
    'unit_price', l.unit_price,
    'line_total', l.line_total,
    'is_core_charge', l.is_core_charge
  )), '[]'::jsonb)
  INTO v_before
  FROM public.pos_cart_lines l
  WHERE l.cart_id = p_cart_id;

  UPDATE public.pos_cart_lines l
  SET
    unit_price = round(l.unit_price * (1 - p_discount_percent / 100.0), 4),
    line_total = round(
      round(l.unit_price * (1 - p_discount_percent / 100.0), 4) * l.qty,
      2
    )
  WHERE l.cart_id = p_cart_id
    AND l.is_core_charge = false;

  UPDATE public.pos_carts SET updated_at = now() WHERE id = p_cart_id;

  SELECT COALESCE(jsonb_agg(jsonb_build_object(
    'id', l.id,
    'unit_price', l.unit_price,
    'line_total', l.line_total,
    'is_core_charge', l.is_core_charge
  )), '[]'::jsonb)
  INTO v_after
  FROM public.pos_cart_lines l
  WHERE l.cart_id = p_cart_id;

  PERFORM public._log_pos_action(
    'discount_applied',
    'pos_carts',
    p_cart_id,
    v_before,
    v_after || jsonb_build_object('discount_percent', p_discount_percent),
    p_notes
  );

  RETURN p_cart_id;
END;
$$;

-- Void open/parked cart (abandon) — manager gate; no silent delete.
CREATE OR REPLACE FUNCTION public.void_pos_cart(
  p_cart_id UUID,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_cart public.pos_carts%ROWTYPE;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;
  IF NOT public.is_pos_approver() THEN
    RAISE EXCEPTION 'admin or shop manager approval required';
  END IF;

  PERFORM public._require_cart_mutate(p_cart_id);
  SELECT * INTO v_cart FROM public.pos_carts WHERE id = p_cart_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'cart not found: %', p_cart_id;
  END IF;
  IF v_cart.status NOT IN ('open', 'parked') THEN
    RAISE EXCEPTION 'only open or parked carts can be voided (got %)', v_cart.status;
  END IF;

  UPDATE public.pos_carts
  SET status = 'abandoned', updated_at = now()
  WHERE id = p_cart_id;

  PERFORM public._log_pos_action(
    'cart_voided',
    'pos_carts',
    p_cart_id,
    jsonb_build_object('status', v_cart.status),
    jsonb_build_object('status', 'abandoned'),
    p_notes
  );

  RETURN p_cart_id;
END;
$$;

-- Widen finance refund so Admin|shop-manager counter path posts through same SoR.
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
  IF NOT (
    public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
    OR public.is_pos_approver()
  ) THEN
    RAISE EXCEPTION 'finance, admin, or shop manager role required';
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

  PERFORM public._log_pos_action(
    'refund_posted',
    'finance_refunds',
    v_id,
    jsonb_build_object('invoice_id', p_invoice_id),
    jsonb_build_object('refund_id', v_id, 'document_number', v_doc),
    p_notes
  );

  RETURN v_id;
END;
$$;

-- Explicit POS counter entry point — always delegates to finance pipeline.
CREATE OR REPLACE FUNCTION public.post_pos_refund(
  p_invoice_id UUID,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;
  IF NOT public.is_pos_approver() THEN
    RAISE EXCEPTION 'admin or shop manager approval required';
  END IF;
  RETURN public.post_finance_refund(p_invoice_id, p_notes);
END;
$$;

REVOKE ALL ON FUNCTION public.is_pos_approver() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._log_pos_action(TEXT, TEXT, UUID, JSONB, JSONB, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.apply_pos_cart_discount(UUID, NUMERIC, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.void_pos_cart(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.post_pos_refund(UUID, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.post_finance_refund(UUID, TEXT) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.is_pos_approver() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public._log_pos_action(TEXT, TEXT, UUID, JSONB, JSONB, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.apply_pos_cart_discount(UUID, NUMERIC, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.void_pos_cart(UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.post_pos_refund(UUID, TEXT) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.post_finance_refund(UUID, TEXT) TO authenticated, service_role;
