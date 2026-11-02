-- Customer suspension for failing to settle (owner decision 2026-10-04).
--
-- A customer is suspended automatically when any of these holds:
--   on_account_overdue  a balance left on account at delivery (part settlement) is unpaid after 7 days
--   invoice_overdue     a posted invoice still owes money 30 days after it was issued
--   refused_cod         2+ cash/card-on-delivery deliveries failed as refused / customer absent in 90 days
-- It is checked daily (pg_cron) and again whenever the customer tries to use credit.
--
-- While suspended, credit is blocked: every pay-on-delivery purchase (choosing it, placing the order,
-- leaving a balance on account at the door), counter sales on account, and holds / back-orders for the
-- customer. Paying upfront and paying what they owe stay allowed.
--
-- Only a manager (POS approver) or admin lifts a suspension, with a reason; the debts it was for are
-- then waived from the automatic rules, so it is not re-raised for them (new overdue debts still count).

CREATE TABLE public.customer_suspensions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id uuid NOT NULL REFERENCES public.customers(id) ON DELETE CASCADE,
  status text NOT NULL CHECK (status IN ('active','lifted')),
  source text NOT NULL CHECK (source IN ('automatic','manual')),
  reason text NOT NULL,
  -- What it was for: [{rule, key, ref, label, amount, currency, since}]
  findings jsonb NOT NULL DEFAULT '[]'::jsonb,
  suspended_by uuid,
  suspended_at timestamptz NOT NULL DEFAULT now(),
  lifted_by uuid,
  lifted_at timestamptz,
  lift_reason text,
  CHECK (status <> 'lifted' OR (lifted_by IS NOT NULL AND lift_reason IS NOT NULL))
);
CREATE UNIQUE INDEX customer_suspensions_one_active ON public.customer_suspensions(customer_id) WHERE status = 'active';
CREATE INDEX customer_suspensions_customer_idx ON public.customer_suspensions(customer_id, suspended_at DESC);

ALTER TABLE public.customer_suspensions ENABLE ROW LEVEL SECURITY;
CREATE POLICY customer_suspensions_staff_read ON public.customer_suspensions FOR SELECT TO authenticated
  USING (public.is_staff());
REVOKE ALL ON TABLE public.customer_suspensions FROM anon;
GRANT SELECT ON TABLE public.customer_suspensions TO authenticated;

-- What would suspend this customer now, minus debts a manager already waived.
CREATE OR REPLACE FUNCTION private.customer_suspension_findings(p_customer_id uuid)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_waived text[]; v_items jsonb := '[]'::jsonb; v_refused jsonb;
BEGIN
 SELECT COALESCE(array_agg(DISTINCT f->>'key'),'{}') INTO v_waived
 FROM public.customer_suspensions s, jsonb_array_elements(s.findings) f
 WHERE s.customer_id=p_customer_id AND s.status='lifted';

 -- Balance left on account at delivery, unpaid after 7 days.
 SELECT v_items || COALESCE(jsonb_agg(jsonb_build_object('rule','on_account_overdue','key','bal:'||b.id,'ref',b.sales_invoice_id,
   'label','Balance left on account at delivery '||COALESCE(si.document_number,''),'amount',round(si.total-si.amount_paid,2),
   'currency',si.currency,'since',b.created_at)),'[]'::jsonb) INTO v_items
 FROM public.delivery_balance_approvals b JOIN public.sales_invoices si ON si.id=b.sales_invoice_id
 WHERE b.customer_id=p_customer_id AND b.status IN('approved','auto_approved') AND b.created_at < now()-interval '7 days'
   AND si.total-si.amount_paid > 0.01 AND NOT ('bal:'||b.id = ANY(v_waived));

 -- Any posted invoice still owing 30 days after it was issued.
 SELECT v_items || COALESCE(jsonb_agg(jsonb_build_object('rule','invoice_overdue','key','inv:'||si.id,'ref',si.id,
   'label','Invoice '||COALESCE(si.document_number,si.id::text)||' unpaid','amount',round(si.total-si.amount_paid,2),
   'currency',si.currency,'since',COALESCE(si.posted_at,si.created_at))),'[]'::jsonb) INTO v_items
 FROM public.sales_invoices si
 WHERE si.customer_id=p_customer_id AND si.status='posted' AND si.doc_type='invoice'
   AND COALESCE(si.posted_at,si.created_at) < now()-interval '30 days' AND si.total-si.amount_paid > 0.01
   AND NOT ('inv:'||si.id = ANY(v_waived));

 -- Refused / absent pay-on-delivery deliveries in 90 days: two or more.
 SELECT COALESCE(jsonb_agg(jsonb_build_object('rule','refused_cod','key','job:'||j.id,'ref',j.id,
   'label','Pay-on-delivery '||COALESCE(j.document_number,'delivery')||' '||replace(j.failure_reason_code::text,'_',' '),
   'amount',round(si.total,2),'currency',si.currency,'since',j.failed_at)),'[]'::jsonb) INTO v_refused
 FROM public.delivery_jobs j JOIN public.delivery_notes dn ON dn.id=j.delivery_note_id JOIN public.sales_invoices si ON si.id=dn.sales_invoice_id
 WHERE si.customer_id=p_customer_id AND j.status='failed' AND j.failure_reason_code IN('refused','customer_absent')
   AND j.failed_at > now()-interval '90 days'
   AND si.delivery_payment_method IN('cash_on_delivery','card_on_delivery','cash_or_card_on_delivery')
   AND NOT ('job:'||j.id = ANY(v_waived));
 IF jsonb_array_length(v_refused) >= 2 THEN v_items := v_items || v_refused; END IF;
 RETURN v_items;
END $f$;

-- Suspends the customer when a rule holds and they are not suspended yet; returns the active suspension id.
CREATE OR REPLACE FUNCTION private.suspend_customer_if_due(p_customer_id uuid)
 RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_id uuid; v_findings jsonb; v_rules text;
BEGIN
 IF p_customer_id IS NULL THEN RETURN NULL; END IF;
 SELECT id INTO v_id FROM public.customer_suspensions WHERE customer_id=p_customer_id AND status='active';
 IF FOUND THEN RETURN v_id; END IF;
 v_findings := private.customer_suspension_findings(p_customer_id);
 IF jsonb_array_length(v_findings)=0 THEN RETURN NULL; END IF;
 SELECT string_agg(DISTINCT CASE f->>'rule'
   WHEN 'on_account_overdue' THEN 'balance left on account unpaid after 7 days'
   WHEN 'invoice_overdue' THEN 'invoice unpaid after 30 days'
   WHEN 'refused_cod' THEN 'refused pay-on-delivery deliveries' END, '; ') INTO v_rules
 FROM jsonb_array_elements(v_findings) f;
 INSERT INTO public.customer_suspensions(customer_id,status,source,reason,findings)
 VALUES(p_customer_id,'active','automatic','Failed to settle: '||v_rules,v_findings)
 ON CONFLICT DO NOTHING RETURNING id INTO v_id;
 RETURN v_id;
END $f$;

-- Refuses credit for a suspended customer (checking the rules first).
CREATE OR REPLACE FUNCTION private.assert_customer_not_suspended(p_customer_id uuid)
 RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_id uuid; v_reason text;
BEGIN
 v_id := private.suspend_customer_if_due(p_customer_id);
 IF v_id IS NULL THEN RETURN; END IF;
 SELECT reason INTO v_reason FROM public.customer_suspensions WHERE id=v_id;
 RAISE EXCEPTION 'customer account suspended (%): pay upfront, or ask a manager to lift the suspension', v_reason
  USING ERRCODE='P0001', HINT='customer_suspended';
END $f$;

CREATE OR REPLACE FUNCTION private._customer_suspension_json(s public.customer_suspensions)
 RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
 SELECT jsonb_build_object('id',s.id,'customer_id',s.customer_id,
  'customer_name',(SELECT COALESCE(c.business_name,c.display_name) FROM public.customers c WHERE c.id=s.customer_id),
  'status',s.status,'source',s.source,'reason',s.reason,'findings',s.findings,
  'suspended_by_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=s.suspended_by),'suspended_at',s.suspended_at,
  'lifted_by_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=s.lifted_by),'lifted_at',s.lifted_at,'lift_reason',s.lift_reason,
  'owing',(SELECT COALESCE(sum(greatest(si.total-si.amount_paid,0)),0) FROM public.sales_invoices si WHERE si.customer_id=s.customer_id AND si.status='posted'))
$f$;

-- Staff: a customer's current suspension (checking the rules), or null.
CREATE OR REPLACE FUNCTION public.get_customer_suspension(p_customer_id uuid)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE s public.customer_suspensions%ROWTYPE;
BEGIN
 IF NOT public.is_staff() THEN RAISE EXCEPTION 'staff required'; END IF;
 PERFORM private.suspend_customer_if_due(p_customer_id);
 SELECT * INTO s FROM public.customer_suspensions WHERE customer_id=p_customer_id AND status='active';
 RETURN CASE WHEN FOUND THEN private._customer_suspension_json(s) END;
END $f$;

-- Signed-in customer: is my account suspended (so the shop can say so before checkout)?
CREATE OR REPLACE FUNCTION public.get_my_account_suspension()
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_cust uuid := public._current_customer_id(); s public.customer_suspensions%ROWTYPE;
BEGIN
 IF v_cust IS NULL THEN RETURN NULL; END IF;
 PERFORM private.suspend_customer_if_due(v_cust);
 SELECT * INTO s FROM public.customer_suspensions WHERE customer_id=v_cust AND status='active';
 IF NOT FOUND THEN RETURN NULL; END IF;
 RETURN jsonb_build_object('suspended',true,'reason',s.reason,'since',s.suspended_at,
  'owing',(SELECT COALESCE(sum(greatest(si.total-si.amount_paid,0)),0) FROM public.sales_invoices si WHERE si.customer_id=v_cust AND si.status='posted'));
END $f$;

-- Staff list: active first.
CREATE OR REPLACE FUNCTION public.list_customer_suspensions(p_status text DEFAULT 'active', p_limit integer DEFAULT 100)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
BEGIN
 IF NOT public.is_staff() THEN RAISE EXCEPTION 'staff required'; END IF;
 RETURN COALESCE((SELECT jsonb_agg(private._customer_suspension_json(s) ORDER BY (s.status='active') DESC, s.suspended_at DESC)
  FROM (SELECT * FROM public.customer_suspensions WHERE p_status IS NULL OR status=p_status ORDER BY suspended_at DESC
        LIMIT LEAST(GREATEST(COALESCE(p_limit,100),1),500)) s),'[]'::jsonb);
END $f$;

-- Admin, finance or a manager suspends a customer by hand.
CREATE OR REPLACE FUNCTION public.suspend_customer(p_customer_id uuid, p_reason text)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE s public.customer_suspensions%ROWTYPE;
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'admin, finance or manager required to suspend a customer'; END IF;
 IF trim(COALESCE(p_reason,''))='' THEN RAISE EXCEPTION 'reason required'; END IF;
 IF NOT EXISTS(SELECT 1 FROM public.customers WHERE id=p_customer_id) THEN RAISE EXCEPTION 'customer not found'; END IF;
 IF EXISTS(SELECT 1 FROM public.customer_suspensions WHERE customer_id=p_customer_id AND status='active') THEN
  RAISE EXCEPTION 'customer is already suspended'; END IF;
 INSERT INTO public.customer_suspensions(customer_id,status,source,reason,findings,suspended_by)
 VALUES(p_customer_id,'active','manual',trim(p_reason),private.customer_suspension_findings(p_customer_id),auth.uid())
 RETURNING * INTO s;
 RETURN private._customer_suspension_json(s);
END $f$;

-- Manager consent: only a POS approver (manager) or admin lifts a suspension, with a reason.
CREATE OR REPLACE FUNCTION public.lift_customer_suspension(p_suspension_id uuid, p_reason text)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE s public.customer_suspensions%ROWTYPE;
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'a manager must approve lifting a suspension'; END IF;
 IF trim(COALESCE(p_reason,''))='' THEN RAISE EXCEPTION 'say why the suspension is lifted'; END IF;
 SELECT * INTO s FROM public.customer_suspensions WHERE id=p_suspension_id FOR UPDATE;
 IF NOT FOUND OR s.status<>'active' THEN RAISE EXCEPTION 'active suspension required'; END IF;
 -- What the manager accepts is waived from the automatic rules: everything owing now, not just what triggered it.
 UPDATE public.customer_suspensions
 SET status='lifted', lifted_by=auth.uid(), lifted_at=now(), lift_reason=trim(p_reason),
     findings = (SELECT COALESCE(jsonb_agg(DISTINCT x),'[]'::jsonb) FROM jsonb_array_elements(s.findings || private.customer_suspension_findings(s.customer_id)) x)
 WHERE id=s.id RETURNING * INTO s;
 RETURN private._customer_suspension_json(s);
END $f$;

-- Daily: suspend every customer a rule now applies to.
CREATE OR REPLACE FUNCTION private.sweep_customer_suspensions()
 RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_id uuid; n integer := 0; c uuid;
BEGIN
 FOR c IN
  SELECT DISTINCT customer_id FROM public.sales_invoices
   WHERE customer_id IS NOT NULL AND status='posted' AND total-amount_paid > 0.01 AND COALESCE(posted_at,created_at) < now()-interval '7 days'
  UNION
  SELECT DISTINCT si.customer_id FROM public.delivery_jobs j JOIN public.delivery_notes dn ON dn.id=j.delivery_note_id
   JOIN public.sales_invoices si ON si.id=dn.sales_invoice_id
   WHERE si.customer_id IS NOT NULL AND j.status='failed' AND j.failed_at > now()-interval '90 days'
 LOOP
  IF NOT EXISTS(SELECT 1 FROM public.customer_suspensions WHERE customer_id=c AND status='active') THEN
   v_id := private.suspend_customer_if_due(c);
   IF v_id IS NOT NULL THEN n := n+1; END IF;
  END IF;
 END LOOP;
 RETURN n;
END $f$;

-- A refused / absent pay-on-delivery delivery is checked at once (the second one suspends).
CREATE OR REPLACE FUNCTION private.delivery_failed_check_suspension()
 RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_cust uuid;
BEGIN
 IF NEW.status='failed' AND OLD.status IS DISTINCT FROM 'failed' AND NEW.failure_reason_code IN('refused','customer_absent') THEN
  SELECT si.customer_id INTO v_cust FROM public.delivery_notes dn JOIN public.sales_invoices si ON si.id=dn.sales_invoice_id WHERE dn.id=NEW.delivery_note_id;
  PERFORM private.suspend_customer_if_due(v_cust);
 END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER trg_delivery_failed_check_suspension AFTER UPDATE OF status ON public.delivery_jobs
 FOR EACH ROW EXECUTE FUNCTION private.delivery_failed_check_suspension();

-- Enforcement on the tables every credit path writes through.
CREATE OR REPLACE FUNCTION private.enforce_suspension_pay_on_delivery()
 RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
BEGIN
 IF NEW.delivery_payment_method IS DISTINCT FROM 'prepay' AND NEW.customer_id IS NOT NULL THEN
  PERFORM private.assert_customer_not_suspended(NEW.customer_id);
 END IF;
 RETURN NEW;
END $f$;
CREATE TRIGGER trg_carts_suspension_pay_on_delivery BEFORE UPDATE OF delivery_payment_method ON public.pos_carts
 FOR EACH ROW EXECUTE FUNCTION private.enforce_suspension_pay_on_delivery();

CREATE OR REPLACE FUNCTION private.enforce_suspension_balance_on_account()
 RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
BEGIN
 PERFORM private.assert_customer_not_suspended(NEW.customer_id);
 RETURN NEW;
END $f$;
CREATE TRIGGER trg_delivery_balance_suspension BEFORE INSERT ON public.delivery_balance_approvals
 FOR EACH ROW EXECUTE FUNCTION private.enforce_suspension_balance_on_account();

CREATE OR REPLACE FUNCTION private.enforce_suspension_fulfillment()
 RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_cust uuid;
BEGIN
 IF NEW.kind = 'branch_transfer' THEN RETURN NEW; END IF;
 v_cust := COALESCE(NEW.customer_id, (SELECT customer_id FROM public.pos_carts WHERE id=NEW.cart_id));
 PERFORM private.assert_customer_not_suspended(v_cust);
 RETURN NEW;
END $f$;
CREATE TRIGGER trg_fulfillment_suspension BEFORE INSERT ON public.pos_fulfillment_requests
 FOR EACH ROW EXECUTE FUNCTION private.enforce_suspension_fulfillment();

REVOKE ALL ON FUNCTION private.customer_suspension_findings(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.suspend_customer_if_due(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.assert_customer_not_suspended(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private._customer_suspension_json(public.customer_suspensions) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.sweep_customer_suspensions() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.get_customer_suspension(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.get_my_account_suspension() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.list_customer_suspensions(text, integer) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.suspend_customer(uuid, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.lift_customer_suspension(uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_customer_suspension(uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.get_my_account_suspension() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_customer_suspensions(text, integer) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.suspend_customer(uuid, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.lift_customer_suspension(uuid, text) TO authenticated, service_role;

-- Counter sale on account: refused for a suspended customer (same function, one added check).
CREATE OR REPLACE FUNCTION public.checkout_pos_cart_on_account(p_cart_id uuid, p_receipt_email text DEFAULT NULL::text, p_receipt_whatsapp_e164 text DEFAULT NULL::text, p_receipt_phone_e164 text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
DECLARE v_cart public.pos_carts%ROWTYPE; v_cust public.customers%ROWTYPE; v_total numeric; v_invoice uuid;
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
$function$;

-- Daily at 02:10 server time.
DO $d$
BEGIN
 IF EXISTS (SELECT 1 FROM pg_extension WHERE extname='pg_cron') THEN
  PERFORM cron.unschedule(jobid) FROM cron.job WHERE jobname='customer-suspension-sweep-v1';
  PERFORM cron.schedule('customer-suspension-sweep-v1','10 2 * * *','SELECT private.sweep_customer_suspensions();');
 END IF;
END $d$;
