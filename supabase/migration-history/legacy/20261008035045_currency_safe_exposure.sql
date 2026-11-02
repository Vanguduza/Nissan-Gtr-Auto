-- Money owed is never added across currencies.
-- Suspension "owing", the on-account credit check and the delivery-balance credit check summed unpaid
-- invoices in USD and ZiG as one number and compared it with a limit held in the account currency.
-- private.customer_exposure() returns the unpaid balance per currency, plus a total converted into
-- the customer's account currency at each invoice's own booked rate (ZiG per USD). If a ZiG invoice
-- has no rate, the total is NULL: credit checks then refuse (or go to the back office) instead of guessing.

CREATE OR REPLACE FUNCTION private.customer_exposure(p_customer_id uuid)
 RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
 WITH c AS (SELECT currency FROM public.customers WHERE id = p_customer_id),
 open AS (
  SELECT si.currency, greatest(si.total - si.amount_paid, 0) AS amount,
         CASE WHEN si.currency = 'USD' THEN 1 ELSE NULLIF(si.exchange_rate_applied, 0) END AS rate
    FROM public.sales_invoices si WHERE si.customer_id = p_customer_id AND si.status = 'posted' AND si.total - si.amount_paid > 0.009),
 per AS (SELECT currency, sum(amount) AS amount, count(*) AS invoices FROM open GROUP BY currency),
 usd AS (SELECT CASE WHEN bool_and(rate IS NOT NULL) OR count(*) = 0 THEN COALESCE(sum(amount / rate), 0) END AS v FROM open)
 SELECT jsonb_build_object(
  'currency', (SELECT currency FROM c),
  'by_currency', COALESCE((SELECT jsonb_agg(jsonb_build_object('currency', currency, 'amount', round(amount, 2), 'invoices', invoices) ORDER BY currency) FROM per), '[]'::jsonb),
  'total', (SELECT round(CASE WHEN (SELECT currency FROM c) = 'USD' THEN usd.v
                              ELSE usd.v * public.get_zig_exchange_rate(CURRENT_DATE) END, 2) FROM usd))
$f$;
REVOKE ALL ON FUNCTION private.customer_exposure(uuid) FROM PUBLIC, anon, authenticated;

-- 'owing' stays a number (now in the account currency, see 'owing_currency'); 'owing_by_currency' lists each currency.
CREATE OR REPLACE FUNCTION private._customer_suspension_json(s public.customer_suspensions)
 RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
 SELECT jsonb_build_object('id',s.id,'customer_id',s.customer_id,
  'customer_name',(SELECT COALESCE(c.business_name,c.display_name) FROM public.customers c WHERE c.id=s.customer_id),
  'status',s.status,'source',s.source,'reason',s.reason,'findings',s.findings,
  'suspended_by_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=s.suspended_by),'suspended_at',s.suspended_at,
  'lifted_by_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=s.lifted_by),'lifted_at',s.lifted_at,'lift_reason',s.lift_reason,
  'owing',e->'total','owing_currency',e->'currency','owing_by_currency',e->'by_currency')
 FROM (SELECT private.customer_exposure(s.customer_id) AS e) x
$f$;

CREATE OR REPLACE FUNCTION public.get_my_account_suspension()
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_cust uuid := public._current_customer_id(); s public.customer_suspensions%ROWTYPE; e jsonb;
BEGIN
 IF v_cust IS NULL THEN RETURN NULL; END IF;
 PERFORM private.suspend_customer_if_due(v_cust);
 SELECT * INTO s FROM public.customer_suspensions WHERE customer_id=v_cust AND status='active';
 IF NOT FOUND THEN RETURN NULL; END IF;
 e := private.customer_exposure(v_cust);
 RETURN jsonb_build_object('suspended',true,'reason',s.reason,'since',s.suspended_at,
  'owing',e->'total','owing_currency',e->'currency','owing_by_currency',e->'by_currency');
END $f$;

CREATE OR REPLACE FUNCTION public.checkout_pos_cart_on_account(p_cart_id uuid, p_receipt_email text DEFAULT NULL::text, p_receipt_whatsapp_e164 text DEFAULT NULL::text, p_receipt_phone_e164 text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE v_cart public.pos_carts%ROWTYPE; v_cust public.customers%ROWTYPE; v_total numeric; v_invoice uuid; v_owing numeric;
BEGIN
  PERFORM public._require_payments_staff();
  PERFORM public._require_cart_mutate(p_cart_id);
  SELECT * INTO v_cart FROM public.pos_carts WHERE id=p_cart_id FOR UPDATE;
  IF NOT FOUND OR v_cart.status<>'open' THEN RAISE EXCEPTION 'open cart required'; END IF;
  IF v_cart.customer_id IS NULL THEN RAISE EXCEPTION 'registered customer required for on-account checkout'; END IF;
  SELECT * INTO v_cust FROM public.customers WHERE id=v_cart.customer_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'customer not found'; END IF;
  PERFORM private.assert_customer_not_suspended(v_cust.id);
  IF v_cust.credit_hold THEN RAISE EXCEPTION 'customer is on credit hold'; END IF;
  IF COALESCE(v_cust.credit_limit,0)<=0 THEN RAISE EXCEPTION 'customer has no approved credit limit'; END IF;
  IF v_cart.currency IS DISTINCT FROM v_cust.currency THEN
    RAISE EXCEPTION 'on-account cart currency % must match customer account currency %',v_cart.currency,v_cust.currency;
  END IF;
  SELECT COALESCE(sum(line_total),0) INTO v_total FROM public.pos_cart_lines WHERE cart_id=p_cart_id;
  IF v_total<=0 THEN RAISE EXCEPTION 'cart total must be > 0'; END IF;
  -- Owing is every unpaid posted invoice in any currency, converted to the account currency at each
  -- invoice's own rate (customers.open_balance mixes currencies).
  v_owing := (private.customer_exposure(v_cust.id)->>'total')::numeric;
  IF v_owing IS NULL THEN RAISE EXCEPTION 'cannot check credit: no ZiG exchange rate set'; END IF;
  IF v_owing+v_total>v_cust.credit_limit THEN
    RAISE EXCEPTION 'insufficient customer credit: owing % %, cart %, limit %',v_cust.currency,v_owing,v_total,v_cust.credit_limit;
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
    jsonb_build_object('invoice_id',v_invoice,'cart_id',p_cart_id,'customer_id',v_cart.customer_id,'amount',v_total,'currency',v_cart.currency,'credit_limit',v_cust.credit_limit,'open_balance_before',v_owing),
    auth.uid(),format('POS on-account sale authorized: %s %s',v_total,v_cart.currency));
  RETURN v_invoice;
END;
$function$;

CREATE OR REPLACE FUNCTION public.request_delivery_balance_on_account(p_delivery_job_id uuid, p_reason text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE j public.delivery_jobs%ROWTYPE; i public.sales_invoices%ROWTYPE; c public.customers%ROWTYPE;
  v_due numeric; v_exposure numeric; b public.delivery_balance_approvals%ROWTYPE; v_auto boolean := false;
BEGIN
 SELECT * INTO j FROM public.delivery_jobs WHERE id=p_delivery_job_id FOR UPDATE;
 IF NOT FOUND OR j.status<>'dispatched' OR j.assignee_user_id IS DISTINCT FROM auth.uid() OR NOT public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN
  RAISE EXCEPTION 'assigned dispatched driver job required'; END IF;
 IF trim(COALESCE(p_reason,''))='' THEN RAISE EXCEPTION 'say why the customer cannot pay the rest'; END IF;
 SELECT si.* INTO i FROM public.delivery_notes dn JOIN public.sales_invoices si ON si.id=dn.sales_invoice_id WHERE dn.id=j.delivery_note_id;
 IF NOT FOUND OR i.delivery_payment_method NOT IN('cash_on_delivery','card_on_delivery','cash_or_card_on_delivery') THEN
  RAISE EXCEPTION 'this delivery is not cash or card on delivery'; END IF;
 IF EXISTS(SELECT 1 FROM public.pos_card_terminal_attempts a WHERE a.delivery_job_id=j.id AND a.status IN('initiated','approved','unknown')) THEN
  RAISE EXCEPTION 'finish the card payment on this delivery first'; END IF;
 v_due:=greatest(round(i.total-i.amount_paid,2),0);
 IF v_due<=0.004 THEN RAISE EXCEPTION 'nothing is owed on this delivery'; END IF;
 -- One open request per delivery: a repeat returns it (or replaces a pending one whose balance changed).
 SELECT * INTO b FROM public.delivery_balance_approvals WHERE delivery_job_id=j.id AND status IN('pending','auto_approved','approved') FOR UPDATE;
 IF FOUND THEN
  IF b.status<>'pending' AND v_due<=b.amount+0.01 THEN RETURN private._delivery_balance_approval_json(b); END IF;
  UPDATE public.delivery_balance_approvals SET status='cancelled',updated_at=now() WHERE id=b.id;
 END IF;
 IF i.customer_id IS NOT NULL THEN
  SELECT * INTO c FROM public.customers WHERE id=i.customer_id;
  v_exposure := (private.customer_exposure(i.customer_id)->>'total')::numeric;
  -- Limit and exposure are in the account currency; an unconvertible exposure goes to the back office.
  v_auto := v_exposure IS NOT NULL AND i.currency=c.currency AND NOT COALESCE(c.credit_hold,false)
    AND COALESCE(c.credit_limit,0)>0 AND v_exposure<=c.credit_limit+0.01;
 END IF;
 INSERT INTO public.delivery_balance_approvals(delivery_job_id,sales_invoice_id,customer_id,amount,currency,reason,status,basis,credit_limit,exposure,decided_at)
 VALUES(j.id,i.id,i.customer_id,v_due,i.currency,trim(p_reason),CASE WHEN v_auto THEN 'auto_approved' ELSE 'pending' END,
  CASE WHEN v_auto THEN 'credit_limit' ELSE 'back_office' END,c.credit_limit,v_exposure,CASE WHEN v_auto THEN now() END)
 RETURNING * INTO b;
 RETURN private._delivery_balance_approval_json(b);
END $function$;
