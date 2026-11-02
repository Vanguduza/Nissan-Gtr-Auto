-- Deposits on back-orders and customer transfers.
-- The counter can take part payment when it orders a part the branch does not have. The deposit is
-- the customer's money held for them (2200 Customer deposits / store credit), not a sale:
--   take:   Dr cash / bank / EcoCash   Cr 2200, and the same amount issued as the customer's store credit.
--           Cash goes into the open till as a pay-in, so the till's expected cash includes it.
--   sale:   the cashier tenders the deposit as store credit when the part is sold (existing tender).
--   refund: only once the request is cancelled, by a manager: the store credit issue is reversed
--           (refused if the customer has already spent it) and Dr 2200 / Cr the original tender;
--           cash comes out of the manager's open till as a cash refund.
-- Each step is an ordinary posted journal; corrections use reverse_journal.

CREATE TABLE public.pos_fulfillment_deposits (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number text NOT NULL UNIQUE,
  request_id uuid NOT NULL REFERENCES public.pos_fulfillment_requests(id),
  customer_id uuid NOT NULL REFERENCES public.customers(id),
  amount numeric(14,2) NOT NULL CHECK (amount > 0),
  currency public.currency_code NOT NULL,
  exchange_rate_applied numeric(18,8) NOT NULL CHECK (exchange_rate_applied > 0),
  tender public.payment_tender NOT NULL CHECK (tender IN ('cash','bank','ecocash')),
  reference text,
  till_session_id uuid REFERENCES public.pos_till_sessions(id),
  journal_entry_id uuid NOT NULL REFERENCES public.journal_entries(id),
  store_credit_ledger_id uuid NOT NULL REFERENCES public.store_credit_ledger(id),
  status text NOT NULL DEFAULT 'held' CHECK (status IN ('held','refunded')),
  refund_journal_entry_id uuid REFERENCES public.journal_entries(id),
  refund_till_session_id uuid REFERENCES public.pos_till_sessions(id),
  refund_notes text,
  refunded_by uuid REFERENCES auth.users(id),
  refunded_at timestamptz,
  created_by uuid NOT NULL REFERENCES auth.users(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (tender <> 'cash' OR till_session_id IS NOT NULL),
  CHECK (tender = 'cash' OR reference IS NOT NULL)
);
CREATE INDEX pos_fulfillment_deposits_request_idx ON public.pos_fulfillment_deposits(request_id);
CREATE INDEX pos_fulfillment_deposits_customer_idx ON public.pos_fulfillment_deposits(customer_id, created_at DESC);
ALTER TABLE public.pos_fulfillment_deposits ENABLE ROW LEVEL SECURITY;
CREATE POLICY pos_fulfillment_deposits_staff_read ON public.pos_fulfillment_deposits FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin','finance','sales']::public.staff_role[]) OR public.is_pos_approver());
REVOKE ALL ON TABLE public.pos_fulfillment_deposits FROM anon, authenticated;
GRANT SELECT ON TABLE public.pos_fulfillment_deposits TO authenticated;

INSERT INTO public.naming_series(prefix, current_value, pad_length, description)
VALUES ('DEP-', 0, 5, 'Back-order deposit') ON CONFLICT (prefix) DO NOTHING;

CREATE OR REPLACE FUNCTION private._pos_deposit_json(d public.pos_fulfillment_deposits)
 RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
 SELECT jsonb_build_object('id',d.id,'document_number',d.document_number,'request_id',d.request_id,
  'request_number',(SELECT r.document_number FROM public.pos_fulfillment_requests r WHERE r.id=d.request_id),
  'customer_id',d.customer_id,'amount',d.amount,'currency',d.currency,'tender',d.tender,'reference',d.reference,'status',d.status,
  'journal_number',(SELECT j.document_number FROM public.journal_entries j WHERE j.id=d.journal_entry_id),
  'refund_journal_number',(SELECT j.document_number FROM public.journal_entries j WHERE j.id=d.refund_journal_entry_id),
  'taken_by_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=d.created_by),'created_at',d.created_at,
  'refunded_by_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=d.refunded_by),'refunded_at',d.refunded_at,'refund_notes',d.refund_notes)
$f$;
REVOKE ALL ON FUNCTION private._pos_deposit_json(public.pos_fulfillment_deposits) FROM PUBLIC, anon, authenticated;

-- Cashier: take a deposit on an open back-order / transfer for a registered customer.
CREATE OR REPLACE FUNCTION public.take_pos_fulfillment_deposit(p_request_id uuid, p_amount numeric, p_currency public.currency_code,
  p_tender public.payment_tender, p_till_session_id uuid DEFAULT NULL, p_reference text DEFAULT NULL)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE r public.pos_fulfillment_requests%ROWTYPE; s public.pos_till_sessions%ROWTYPE; d public.pos_fulfillment_deposits%ROWTYPE;
        v_rate numeric; v_doc text; v_j uuid; v_sc uuid; v_ref text := NULLIF(trim(COALESCE(p_reference,'')),'');
BEGIN
 PERFORM public._require_sales_staff();
 SELECT * INTO r FROM public.pos_fulfillment_requests WHERE id=p_request_id FOR UPDATE;
 IF NOT FOUND OR r.status::text IN ('collected','cancelled','rejected') THEN RAISE EXCEPTION 'open back-order or transfer required'; END IF;
 IF r.customer_id IS NULL THEN RAISE EXCEPTION 'a deposit needs a registered customer on the request'; END IF;
 IF COALESCE(p_amount,0) <= 0 THEN RAISE EXCEPTION 'deposit must be more than 0'; END IF;
 IF p_tender NOT IN ('cash','bank','ecocash') THEN RAISE EXCEPTION 'take a deposit in cash, by bank transfer or EcoCash'; END IF;
 IF p_tender = 'cash' THEN
  SELECT * INTO s FROM public.pos_till_sessions WHERE id=p_till_session_id FOR UPDATE;
  IF NOT FOUND OR s.status<>'open' THEN RAISE EXCEPTION 'open till required for a cash deposit'; END IF;
  IF s.operator_user_id<>auth.uid() AND NOT public.is_pos_approver() THEN RAISE EXCEPTION 'use your own till'; END IF;
  IF s.currency<>p_currency THEN RAISE EXCEPTION 'this till counts % cash', s.currency; END IF;
 ELSIF v_ref IS NULL THEN RAISE EXCEPTION 'payment reference required for a % deposit', p_tender;
 END IF;
 v_rate := CASE WHEN p_currency='USD' THEN 1 ELSE public.get_zig_exchange_rate(CURRENT_DATE) END;
 IF v_rate IS NULL THEN RAISE EXCEPTION 'set today''s ZiG exchange rate first'; END IF;
 v_doc := public.next_series_value('DEP-');
 PERFORM public._sales_checkout_accounting_enter();
 v_j := public.post_journal_entry(CURRENT_DATE, format('Deposit %s on %s', v_doc, r.document_number), p_currency, v_rate,
   jsonb_build_array(
     jsonb_build_object('account_code', public.gl_account_for_payment_tender(p_tender), 'debit', round(p_amount,2), 'credit', 0),
     jsonb_build_object('account_code', '2200', 'debit', 0, 'credit', round(p_amount,2))));
 PERFORM public._sales_checkout_accounting_exit();
 PERFORM public._payments_rpc_enter();  -- store credit accounts change only inside the payments context
 v_sc := public._append_store_credit(r.customer_id, 'issue', round(p_amount,2), p_currency, v_rate, NULL, v_j,
   format('Deposit %s on %s', v_doc, r.document_number));
 IF p_tender = 'cash' THEN
  INSERT INTO public.pos_till_cash_movements(session_id,kind,amount,reason_code,notes,actor_user_id)
  VALUES (s.id,'cash_in',round(p_amount,2),'backorder_deposit',format('Deposit %s on %s', v_doc, r.document_number),auth.uid());
  INSERT INTO public.pos_till_session_events(session_id,event_type,actor_user_id,detail)
  VALUES (s.id,'cash_movement',auth.uid(),jsonb_build_object('kind','cash_in','amount',round(p_amount,2),'reason_code','backorder_deposit','deposit',v_doc));
 END IF;
 INSERT INTO public.pos_fulfillment_deposits(document_number,request_id,customer_id,amount,currency,exchange_rate_applied,tender,reference,till_session_id,journal_entry_id,store_credit_ledger_id,created_by)
 VALUES (v_doc,r.id,r.customer_id,round(p_amount,2),p_currency,v_rate,p_tender,v_ref,CASE WHEN p_tender='cash' THEN s.id END,v_j,v_sc,auth.uid())
 RETURNING * INTO d;
 RETURN private._pos_deposit_json(d);
EXCEPTION WHEN OTHERS THEN PERFORM public._sales_checkout_accounting_exit(); RAISE;
END $f$;

-- Manager: give a deposit back after the request is cancelled. Cash leaves through the manager's open till.
CREATE OR REPLACE FUNCTION public.refund_pos_fulfillment_deposit(p_deposit_id uuid, p_till_session_id uuid DEFAULT NULL, p_notes text DEFAULT NULL)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE d public.pos_fulfillment_deposits%ROWTYPE; r public.pos_fulfillment_requests%ROWTYPE; s public.pos_till_sessions%ROWTYPE; v_j uuid;
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO d FROM public.pos_fulfillment_deposits WHERE id=p_deposit_id FOR UPDATE;
 IF NOT FOUND OR d.status<>'held' THEN RAISE EXCEPTION 'deposit held for the customer required'; END IF;
 SELECT * INTO r FROM public.pos_fulfillment_requests WHERE id=d.request_id;
 IF r.status::text NOT IN ('cancelled','rejected') THEN RAISE EXCEPTION 'cancel % before refunding its deposit', r.document_number; END IF;
 IF trim(COALESCE(p_notes,''))='' THEN RAISE EXCEPTION 'say why the deposit is refunded'; END IF;
 IF d.tender='cash' THEN
  SELECT * INTO s FROM public.pos_till_sessions WHERE id=p_till_session_id FOR UPDATE;
  IF NOT FOUND OR s.status<>'open' THEN RAISE EXCEPTION 'open till required to pay a cash refund'; END IF;
  IF s.currency<>d.currency THEN RAISE EXCEPTION 'this till counts % cash', s.currency; END IF;
 END IF;
 -- Refused (store credit overdraw) when the customer has already spent the deposit.
 BEGIN
  PERFORM public._payments_rpc_enter();
  PERFORM public._append_store_credit(d.customer_id, 'reverse', d.amount, d.currency, d.exchange_rate_applied, NULL, NULL,
    format('Refund of deposit %s', d.document_number), d.store_credit_ledger_id);
 EXCEPTION WHEN OTHERS THEN RAISE EXCEPTION 'the customer has already used this deposit as store credit (%)', SQLERRM;
 END;
 PERFORM public._sales_checkout_accounting_enter();
 v_j := public.post_journal_entry(CURRENT_DATE, format('Refund of deposit %s on %s', d.document_number, r.document_number), d.currency, d.exchange_rate_applied,
   jsonb_build_array(
     jsonb_build_object('account_code', '2200', 'debit', d.amount, 'credit', 0),
     jsonb_build_object('account_code', public.gl_account_for_payment_tender(d.tender), 'debit', 0, 'credit', d.amount)));
 PERFORM public._sales_checkout_accounting_exit();
 IF d.tender='cash' THEN
  INSERT INTO public.pos_till_cash_movements(session_id,kind,amount,reason_code,notes,actor_user_id)
  VALUES (s.id,'cash_refund',d.amount,'customer_refund',format('Refund of deposit %s: %s', d.document_number, trim(p_notes)),auth.uid());
  INSERT INTO public.pos_till_session_events(session_id,event_type,actor_user_id,detail)
  VALUES (s.id,'cash_movement',auth.uid(),jsonb_build_object('kind','cash_refund','amount',d.amount,'reason_code','customer_refund','deposit',d.document_number));
 END IF;
 UPDATE public.pos_fulfillment_deposits SET status='refunded', refund_journal_entry_id=v_j, refund_till_session_id=CASE WHEN d.tender='cash' THEN s.id END,
  refund_notes=trim(p_notes), refunded_by=auth.uid(), refunded_at=now() WHERE id=d.id RETURNING * INTO d;
 RETURN private._pos_deposit_json(d);
EXCEPTION WHEN OTHERS THEN PERFORM public._sales_checkout_accounting_exit(); RAISE;
END $f$;

CREATE OR REPLACE FUNCTION public.list_pos_fulfillment_deposits(p_request_id uuid)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
BEGIN
 PERFORM public._require_sales_staff();
 RETURN COALESCE((SELECT jsonb_agg(private._pos_deposit_json(d) ORDER BY d.created_at) FROM public.pos_fulfillment_deposits d WHERE d.request_id=p_request_id),'[]'::jsonb);
END $f$;

-- Request list: adds the deposits still held, per currency.
DROP FUNCTION public.list_pos_fulfillment_requests(text, text, integer);
CREATE FUNCTION public.list_pos_fulfillment_requests(p_query text DEFAULT NULL::text, p_status text DEFAULT NULL::text, p_limit integer DEFAULT 100)
 RETURNS TABLE(id uuid, document_number text, kind text, status text, stock_item_id uuid, oem_part_number text, description text, uom_id uuid, qty numeric,
  source_warehouse_id uuid, source_warehouse_name text, destination_warehouse_id uuid, destination_warehouse_name text, customer_id uuid, cart_id uuid,
  invoice_id uuid, stock_entry_id uuid, expires_at timestamp with time zone, ready_at timestamp with time zone, collected_at timestamp with time zone,
  created_at timestamp with time zone, deposits_held jsonb)
 LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO '' AS $function$
 SELECT r.id,r.document_number,r.kind::text,r.status::text,r.stock_item_id,s.oem_part_number::text,s.description,r.uom_id,r.qty,
  r.source_warehouse_id,sw.name,r.destination_warehouse_id,dw.name,r.customer_id,r.cart_id,r.invoice_id,r.stock_entry_id,r.expires_at,r.ready_at,r.collected_at,r.created_at,
  COALESCE((SELECT jsonb_agg(jsonb_build_object('currency',x.currency,'amount',x.amount) ORDER BY x.currency)
    FROM (SELECT d.currency, sum(d.amount) AS amount FROM public.pos_fulfillment_deposits d WHERE d.request_id=r.id AND d.status='held' GROUP BY d.currency) x),'[]'::jsonb)
 FROM public.pos_fulfillment_requests r JOIN public.stock_items s ON s.id=r.stock_item_id
 LEFT JOIN public.warehouses sw ON sw.id=r.source_warehouse_id LEFT JOIN public.warehouses dw ON dw.id=r.destination_warehouse_id
 -- Staff only: this list was open to any signed-in user (customers included).
 WHERE (auth.role()='service_role' OR public.has_staff_role(ARRAY['admin','sales','warehouse','dispatcher','finance']::public.staff_role[]) OR public.is_pos_approver())
   AND (p_status IS NULL OR r.status::text=p_status) AND (p_query IS NULL OR trim(p_query)='' OR r.document_number ILIKE '%'||trim(p_query)||'%' OR s.oem_part_number ILIKE '%'||trim(p_query)||'%' OR COALESCE(s.description,'') ILIKE '%'||trim(p_query)||'%')
 ORDER BY r.created_at DESC LIMIT LEAST(GREATEST(COALESCE(p_limit,100),1),250);
$function$;

REVOKE ALL ON FUNCTION public.list_pos_fulfillment_requests(text, text, integer) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.take_pos_fulfillment_deposit(uuid, numeric, public.currency_code, public.payment_tender, uuid, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.refund_pos_fulfillment_deposit(uuid, uuid, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.list_pos_fulfillment_deposits(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.list_pos_fulfillment_requests(text, text, integer) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.take_pos_fulfillment_deposit(uuid, numeric, public.currency_code, public.payment_tender, uuid, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.refund_pos_fulfillment_deposit(uuid, uuid, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_pos_fulfillment_deposits(uuid) TO authenticated, service_role;
