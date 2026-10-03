-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905131529 harden_payment_reversal_and_credit_authority).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION public.guard_posted_payment_cancellation_authority()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $$
BEGIN
  IF OLD.status='posted'::public.payment_entry_status
     AND NEW.status='cancelled'::public.payment_entry_status
     AND NOT (
       COALESCE(auth.role(),'')='service_role'
       OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])
     ) THEN
    RAISE EXCEPTION 'posted payment cancellation requires finance or admin role';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS payment_entries_posted_cancel_authority ON public.payment_entries;
CREATE TRIGGER payment_entries_posted_cancel_authority
BEFORE UPDATE OF status ON public.payment_entries
FOR EACH ROW EXECUTE FUNCTION public.guard_posted_payment_cancellation_authority();

CREATE OR REPLACE FUNCTION public.issue_store_credit(
  p_customer_id uuid,
  p_amount numeric,
  p_currency public.currency_code DEFAULT 'USD'::public.currency_code,
  p_exchange_rate numeric DEFAULT 1,
  p_reason text DEFAULT NULL,
  p_debit_account text DEFAULT '1100'
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_journal UUID;
  v_ledger UUID;
  v_debit TEXT;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required to issue store credit';
  END IF;
  PERFORM public._payments_rpc_enter();

  IF p_customer_id IS NULL THEN
    RAISE EXCEPTION 'customer_id required';
  END IF;
  IF p_amount IS NULL OR p_amount <= 0 THEN
    RAISE EXCEPTION 'issue amount must be > 0';
  END IF;
  IF p_currency='ZIG' AND (p_exchange_rate IS NULL OR p_exchange_rate<=0) THEN
    RAISE EXCEPTION 'exchange_rate_applied required for ZIG';
  END IF;

  v_debit := COALESCE(NULLIF(p_debit_account, ''), '1100');
  IF v_debit NOT IN ('1100', '1200') THEN
    RAISE EXCEPTION 'issue_store_credit debit must be 1100 or 1200';
  END IF;

  v_journal := public.post_journal_entry(
    CURRENT_DATE,
    COALESCE(p_reason, 'Store credit issued'),
    p_currency,
    CASE WHEN p_currency='USD' THEN COALESCE(p_exchange_rate,1) ELSE p_exchange_rate END,
    jsonb_build_array(
      jsonb_build_object('account_code',v_debit,'debit',p_amount,'credit',0,'currency',p_currency),
      jsonb_build_object('account_code','2200','debit',0,'credit',p_amount,'currency',p_currency)
    )
  );

  v_ledger := public._append_store_credit(
    p_customer_id,'issue',p_amount,p_currency,
    CASE WHEN p_currency='USD' THEN COALESCE(p_exchange_rate,1) ELSE p_exchange_rate END,
    NULL,v_journal,COALESCE(p_reason,'Store credit issued')
  );

  PERFORM public.emit_domain_event(
    'refund_issued',
    'sc:issue:' || v_ledger::text,
    jsonb_build_object('ledger_id',v_ledger,'customer_id',p_customer_id,'amount',p_amount,'currency',p_currency),
    auth.uid(),
    format('GTR Auto: store credit issued %s %s',p_amount,p_currency)
  );
  RETURN v_ledger;
END;
$$;

REVOKE EXECUTE ON FUNCTION public.post_finance_refund(uuid,text) FROM anon;
REVOKE EXECUTE ON FUNCTION public.post_pos_refund(uuid,text) FROM anon;
REVOKE EXECUTE ON FUNCTION public.post_return_credit_note(uuid,jsonb) FROM anon;
REVOKE EXECUTE ON FUNCTION public.post_customer_return_credit_note(uuid,jsonb) FROM anon;
REVOKE EXECUTE ON FUNCTION public.request_customer_return(uuid,jsonb) FROM anon;
REVOKE EXECUTE ON FUNCTION public.redeem_loyalty_points(uuid,numeric,public.currency_code,numeric,uuid,text) FROM anon;
REVOKE EXECUTE ON FUNCTION public.reverse_loyalty_movement(uuid) FROM anon;
