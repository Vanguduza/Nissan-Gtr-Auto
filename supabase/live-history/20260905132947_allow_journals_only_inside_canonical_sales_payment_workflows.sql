-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905132947 allow_journals_only_inside_canonical_sales_payment_workflows).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION public._sales_checkout_accounting_active()
RETURNS boolean
LANGUAGE sql
STABLE
SET search_path TO ''
AS $$ SELECT COALESCE(current_setting('app.sales_checkout_accounting',true),'')='1'; $$;

CREATE OR REPLACE FUNCTION public._sales_checkout_accounting_enter()
RETURNS void
LANGUAGE sql
SET search_path TO ''
AS $$ SELECT set_config('app.sales_checkout_accounting','1',true); $$;

CREATE OR REPLACE FUNCTION public._sales_checkout_accounting_exit()
RETURNS void
LANGUAGE sql
SET search_path TO ''
AS $$ SELECT set_config('app.sales_checkout_accounting','',true); $$;

REVOKE ALL ON FUNCTION public._sales_checkout_accounting_enter() FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public._sales_checkout_accounting_exit() FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public._sales_checkout_accounting_active() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public._sales_checkout_accounting_enter() TO service_role;
GRANT EXECUTE ON FUNCTION public._sales_checkout_accounting_exit() TO service_role;
GRANT EXECUTE ON FUNCTION public._sales_checkout_accounting_active() TO service_role;

CREATE OR REPLACE FUNCTION public.create_journal_draft(
  p_entry_date date,p_description text,p_currency public.currency_code,p_exchange_rate numeric,p_lines jsonb
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_id uuid; v_line jsonb;
BEGIN
  IF NOT (
    auth.role()='service_role'
    OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])
    OR public._storefront_rpc_active()
    OR public._payments_rpc_active()
    OR public._sales_checkout_accounting_active()
  ) THEN RAISE EXCEPTION 'finance or admin role required'; END IF;
  IF public.is_period_locked(COALESCE(p_entry_date,CURRENT_DATE)) THEN
    RAISE EXCEPTION 'accounting period is locked for date %',COALESCE(p_entry_date,CURRENT_DATE);
  END IF;
  IF p_currency='ZIG' AND (p_exchange_rate IS NULL OR p_exchange_rate<=0) THEN
    RAISE EXCEPTION 'exchange_rate_applied required for ZIG journal';
  END IF;
  IF p_lines IS NULL OR jsonb_typeof(p_lines)<>'array' OR jsonb_array_length(p_lines)=0 THEN
    RAISE EXCEPTION 'journal lines required';
  END IF;
  INSERT INTO public.journal_entries(entry_date,description,currency,exchange_rate_applied,status,posted_by,posted_at)
  VALUES(COALESCE(p_entry_date,CURRENT_DATE),p_description,p_currency,
    CASE WHEN p_currency='USD' THEN 1 ELSE p_exchange_rate END,'draft',auth.uid(),now())
  RETURNING id INTO v_id;
  FOR v_line IN SELECT * FROM jsonb_array_elements(p_lines)
  LOOP
    INSERT INTO public.journal_entry_lines(journal_entry_id,account_code,debit,credit,currency)
    VALUES(v_id,v_line->>'account_code',COALESCE((v_line->>'debit')::numeric,0),COALESCE((v_line->>'credit')::numeric,0),COALESCE((v_line->>'currency')::public.currency_code,p_currency));
  END LOOP;
  PERFORM public._assert_journal_balanced(v_id);
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.post_journal(p_entry_id uuid)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_date date; v_status public.journal_status; v_doc text;
BEGIN
  IF NOT (
    auth.role()='service_role'
    OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])
    OR public._storefront_rpc_active()
    OR public._payments_rpc_active()
    OR public._sales_checkout_accounting_active()
  ) THEN RAISE EXCEPTION 'finance or admin role required'; END IF;
  SELECT entry_date,status INTO v_date,v_status FROM public.journal_entries WHERE id=p_entry_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'journal entry not found: %',p_entry_id; END IF;
  IF v_status='posted' THEN RETURN p_entry_id; END IF;
  IF public.is_period_locked(v_date) THEN RAISE EXCEPTION 'accounting period is locked for date %',v_date; END IF;
  PERFORM public._assert_journal_balanced(p_entry_id);
  v_doc:=public.next_series_value('JV-');
  UPDATE public.journal_entries SET status='posted',posted_at=now(),posted_by=COALESCE(auth.uid(),posted_by),document_number=COALESCE(document_number,v_doc)
  WHERE id=p_entry_id;
  RETURN p_entry_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.checkout_pos_cart_with_tenders(
  p_cart_id uuid,p_tenders jsonb,p_receipt_email text DEFAULT NULL,p_receipt_whatsapp_e164 text DEFAULT NULL,p_receipt_phone_e164 text DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_inv uuid; v_status text;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM public._require_cart_mutate(p_cart_id);
  IF p_tenders IS NULL OR jsonb_typeof(p_tenders)<>'array' OR jsonb_array_length(p_tenders)<1 THEN
    RAISE EXCEPTION 'p_tenders required for split-bill checkout';
  END IF;
  UPDATE public.pos_carts c
  SET customer_id=COALESCE(c.customer_id,public.ensure_pos_walkin_customer()),updated_at=now()
  WHERE c.id=p_cart_id AND c.customer_id IS NULL;

  PERFORM public._sales_checkout_accounting_enter();
  BEGIN
    v_inv:=public.checkout_pos_cart(p_cart_id,p_receipt_email,p_receipt_whatsapp_e164,p_receipt_phone_e164);
  EXCEPTION WHEN OTHERS THEN
    PERFORM public._sales_checkout_accounting_exit();
    RAISE;
  END;
  PERFORM public._sales_checkout_accounting_exit();

  SELECT status::text INTO v_status FROM public.sales_invoices WHERE id=v_inv;
  IF v_status='on_hold' THEN RETURN v_inv; END IF;
  PERFORM public.settle_invoice_tenders(v_inv,p_tenders);
  RETURN v_inv;
END;
$$;

CREATE OR REPLACE FUNCTION public.checkout_pos_cart_on_account(
  p_cart_id uuid,p_receipt_email text DEFAULT NULL,p_receipt_whatsapp_e164 text DEFAULT NULL,p_receipt_phone_e164 text DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_cart public.pos_carts%ROWTYPE; v_cust public.customers%ROWTYPE; v_total numeric; v_invoice uuid;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM public._require_cart_mutate(p_cart_id);
  SELECT * INTO v_cart FROM public.pos_carts WHERE id=p_cart_id FOR UPDATE;
  IF NOT FOUND OR v_cart.status<>'open' THEN RAISE EXCEPTION 'open cart required'; END IF;
  IF v_cart.customer_id IS NULL THEN RAISE EXCEPTION 'registered customer required for on-account checkout'; END IF;
  SELECT * INTO v_cust FROM public.customers WHERE id=v_cart.customer_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'customer not found'; END IF;
  IF v_cust.credit_hold THEN RAISE EXCEPTION 'customer is on credit hold'; END IF;
  IF COALESCE(v_cust.credit_limit,0)<=0 THEN RAISE EXCEPTION 'customer has no approved credit limit'; END IF;
  IF v_cart.currency IS DISTINCT FROM v_cust.currency THEN
    RAISE EXCEPTION 'on-account cart currency % must match customer account currency %',v_cart.currency,v_cust.currency;
  END IF;
  SELECT COALESCE(sum(line_total),0) INTO v_total FROM public.pos_cart_lines WHERE cart_id=p_cart_id;
  IF v_total<=0 THEN RAISE EXCEPTION 'cart total must be > 0'; END IF;
  IF COALESCE(v_cust.open_balance,0)+v_total>v_cust.credit_limit THEN
    RAISE EXCEPTION 'insufficient customer credit: open balance %, cart %, limit %',COALESCE(v_cust.open_balance,0),v_total,v_cust.credit_limit;
  END IF;

  PERFORM public._sales_checkout_accounting_enter();
  BEGIN
    v_invoice:=public.checkout_pos_cart(p_cart_id,p_receipt_email,p_receipt_whatsapp_e164,p_receipt_phone_e164);
  EXCEPTION WHEN OTHERS THEN
    PERFORM public._sales_checkout_accounting_exit();
    RAISE;
  END;
  PERFORM public._sales_checkout_accounting_exit();

  IF NOT EXISTS(SELECT 1 FROM public.sales_invoices si WHERE si.id=v_invoice AND si.status='posted' AND si.doc_type='invoice') THEN
    RAISE EXCEPTION 'on-account checkout did not produce a posted invoice';
  END IF;
  PERFORM public.emit_domain_event('pos_credit_sale_authorized','pos:credit:'||v_invoice::text,
    jsonb_build_object('invoice_id',v_invoice,'cart_id',p_cart_id,'customer_id',v_cart.customer_id,'amount',v_total,'currency',v_cart.currency,'credit_limit',v_cust.credit_limit,'open_balance_before',COALESCE(v_cust.open_balance,0)),
    auth.uid(),format('POS on-account sale authorized: %s %s',v_total,v_cart.currency));
  RETURN v_invoice;
END;
$$;
