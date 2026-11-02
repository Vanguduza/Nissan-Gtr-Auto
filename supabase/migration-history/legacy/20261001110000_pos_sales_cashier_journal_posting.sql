-- POS sales cashiers could not complete a sale: checkout, tender settlement and refunds post their
-- journal through create_journal_draft and post_journal, which only admitted admin/finance (or service_role / the
-- storefront flag). The POS entry points already admit sales staff (_require_payments_staff,
-- _require_cart_mutate) and approvers (is_pos_approver), so a sales-only cashier failed at the ledger
-- step with "finance or admin role required". Found by supabase/tests/web_pos_sale_flow_smoke.sql.
--
-- Fix, same pattern as the storefront path (app.storefront_rpc): each POS entry point marks the
-- transaction as internal POS posting for the duration of the call. Callers cannot set the flag
-- themselves (set_config is not exposed through PostgREST), and the journal functions stay closed to
-- direct calls by sales staff. Who may sell, settle or refund is unchanged: the original functions are
-- kept intact as *_core and still run their own role checks.

CREATE OR REPLACE FUNCTION public._pos_posting_active()
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
  SELECT COALESCE(current_setting('app.pos_posting', true), '') = '1';
$$;
REVOKE ALL ON FUNCTION public._pos_posting_active() FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.create_journal_draft(p_entry_date date, p_description text, p_currency currency_code, p_exchange_rate numeric, p_lines jsonb)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path = public
AS $function$
DECLARE
  v_id UUID;
  v_line JSONB;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
    OR public._storefront_rpc_active()
    OR public._pos_posting_active()
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  IF public.is_period_locked(p_entry_date) THEN
    RAISE EXCEPTION 'accounting period is locked for date %', p_entry_date;
  END IF;

  INSERT INTO public.journal_entries (
    entry_date, description, currency, exchange_rate_applied, status, posted_by, posted_at
  )
  VALUES (
    COALESCE(p_entry_date, CURRENT_DATE),
    p_description,
    p_currency,
    CASE WHEN p_currency = 'USD' THEN COALESCE(p_exchange_rate, 1) ELSE p_exchange_rate END,
    'draft',
    auth.uid(),
    now()
  )
  RETURNING id INTO v_id;

  IF p_lines IS NULL OR jsonb_typeof(p_lines) <> 'array' OR jsonb_array_length(p_lines) = 0 THEN
    RAISE EXCEPTION 'journal lines required';
  END IF;

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
  RETURN v_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.post_journal(p_entry_id uuid)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path = public
AS $function$
DECLARE
  v_date DATE;
  v_status public.journal_status;
  v_doc TEXT;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
    OR public._storefront_rpc_active()
    OR public._pos_posting_active()
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  SELECT entry_date, status INTO v_date, v_status
  FROM public.journal_entries
  WHERE id = p_entry_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'journal entry not found: %', p_entry_id;
  END IF;
  IF v_status = 'posted' THEN
    RETURN p_entry_id;
  END IF;
  IF public.is_period_locked(v_date) THEN
    RAISE EXCEPTION 'accounting period is locked for date %', v_date;
  END IF;

  PERFORM public._assert_journal_balanced(p_entry_id);
  v_doc := public.next_series_value('JV-');

  UPDATE public.journal_entries
  SET
    status = 'posted',
    posted_at = now(),
    posted_by = COALESCE(auth.uid(), posted_by),
    document_number = COALESCE(document_number, v_doc)
  WHERE id = p_entry_id;

  RETURN p_entry_id;
END;
$function$;

ALTER FUNCTION public.checkout_pos_cart(uuid, text, text, text) RENAME TO _checkout_pos_cart_core;
REVOKE ALL ON FUNCTION public._checkout_pos_cart_core(uuid, text, text, text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public._checkout_pos_cart_core(uuid, text, text, text) TO service_role;

CREATE FUNCTION public.checkout_pos_cart(p_cart_id uuid,
  p_receipt_email text DEFAULT NULL,
  p_receipt_whatsapp_e164 text DEFAULT NULL,
  p_receipt_phone_e164 text DEFAULT NULL)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_prev text := COALESCE(current_setting('app.pos_posting', true), '');
  v_result uuid;
BEGIN
  -- Role checks stay in _checkout_pos_cart_core; this only marks its ledger posting as internal.
  PERFORM set_config('app.pos_posting', '1', true);
  v_result := public._checkout_pos_cart_core(p_cart_id, p_receipt_email, p_receipt_whatsapp_e164, p_receipt_phone_e164);
  PERFORM set_config('app.pos_posting', v_prev, true);
  RETURN v_result;
END;
$$;
COMMENT ON FUNCTION public.checkout_pos_cart(uuid, text, text, text) IS
  'POS entry point. Logic lives in _checkout_pos_cart_core; change that, not this wrapper (see 20261001110000).';
REVOKE ALL ON FUNCTION public.checkout_pos_cart(uuid, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.checkout_pos_cart(uuid, text, text, text) TO anon, authenticated, service_role;

ALTER FUNCTION public.settle_invoice_tenders(uuid, jsonb) RENAME TO _settle_invoice_tenders_core;
REVOKE ALL ON FUNCTION public._settle_invoice_tenders_core(uuid, jsonb) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public._settle_invoice_tenders_core(uuid, jsonb) TO service_role;

CREATE FUNCTION public.settle_invoice_tenders(p_invoice_id uuid, p_tenders jsonb)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_prev text := COALESCE(current_setting('app.pos_posting', true), '');
  v_result uuid;
BEGIN
  -- Role checks stay in _settle_invoice_tenders_core; this only marks its ledger posting as internal.
  PERFORM set_config('app.pos_posting', '1', true);
  v_result := public._settle_invoice_tenders_core(p_invoice_id, p_tenders);
  PERFORM set_config('app.pos_posting', v_prev, true);
  RETURN v_result;
END;
$$;
COMMENT ON FUNCTION public.settle_invoice_tenders(uuid, jsonb) IS
  'POS entry point. Logic lives in _settle_invoice_tenders_core; change that, not this wrapper (see 20261001110000).';
REVOKE ALL ON FUNCTION public.settle_invoice_tenders(uuid, jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.settle_invoice_tenders(uuid, jsonb) TO anon, authenticated, service_role;

ALTER FUNCTION public.post_pos_refund(uuid, text) RENAME TO _post_pos_refund_core;
REVOKE ALL ON FUNCTION public._post_pos_refund_core(uuid, text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public._post_pos_refund_core(uuid, text) TO service_role;

CREATE FUNCTION public.post_pos_refund(p_invoice_id uuid, p_notes text DEFAULT NULL)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_prev text := COALESCE(current_setting('app.pos_posting', true), '');
  v_result uuid;
BEGIN
  -- Role checks stay in _post_pos_refund_core; this only marks its ledger posting as internal.
  PERFORM set_config('app.pos_posting', '1', true);
  v_result := public._post_pos_refund_core(p_invoice_id, p_notes);
  PERFORM set_config('app.pos_posting', v_prev, true);
  RETURN v_result;
END;
$$;
COMMENT ON FUNCTION public.post_pos_refund(uuid, text) IS
  'POS entry point. Logic lives in _post_pos_refund_core; change that, not this wrapper (see 20261001110000).';
REVOKE ALL ON FUNCTION public.post_pos_refund(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.post_pos_refund(uuid, text) TO anon, authenticated, service_role;
