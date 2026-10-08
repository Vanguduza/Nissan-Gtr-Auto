-- Driver cash differences reach the general ledger.
-- Collected cash was debited to the till cash account (1120) when the driver took it. When a manager
-- approves a counted difference, a journal now moves the difference out of cash:
--   short, reason driver_short or investigation -> Dr 1250 Staff receivables  / Cr 1120 (the driver owes it)
--   short, any other reason                     -> Dr 5310 Cash over / short   / Cr 1120
--   over                                        -> Dr 1120                     / Cr 5310
-- What a driver owes is then either paid back in cash (Dr 1120 / Cr 1250, counted by someone other
-- than the driver) or written off by a manager (Dr 5310 / Cr 1250). Every entry is an ordinary
-- posted journal: a mistake is corrected with reverse_journal, never by editing.
-- ZiG journals use the hand-in's own collections' booked rates, so cash leaves at the rate it came in.

INSERT INTO public.chart_of_accounts(code, name, account_type, display_name) VALUES
 ('1250', 'Staff Receivables', 'asset', 'Staff receivables (cash shortages)'),
 ('5310', 'Cash Over / Short', 'expense', 'Cash over / short')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE public.driver_cash_handins
  ADD COLUMN journal_entry_id uuid REFERENCES public.journal_entries(id),
  ADD COLUMN owed_amount numeric(14,2) NOT NULL DEFAULT 0 CHECK (owed_amount >= 0),
  ADD COLUMN recovered_amount numeric(14,2) NOT NULL DEFAULT 0 CHECK (recovered_amount >= 0),
  ADD COLUMN written_off_amount numeric(14,2) NOT NULL DEFAULT 0 CHECK (written_off_amount >= 0),
  ADD CONSTRAINT driver_cash_handins_settled_within_owed CHECK (recovered_amount + written_off_amount <= owed_amount);

CREATE TABLE public.driver_cash_recoveries (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  handin_id uuid NOT NULL REFERENCES public.driver_cash_handins(id),
  kind text NOT NULL CHECK (kind IN ('cash', 'write_off')),
  amount numeric(14,2) NOT NULL CHECK (amount > 0),
  currency public.currency_code NOT NULL,
  journal_entry_id uuid NOT NULL REFERENCES public.journal_entries(id),
  notes text,
  recorded_by uuid NOT NULL REFERENCES auth.users(id),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX driver_cash_recoveries_handin_idx ON public.driver_cash_recoveries(handin_id, created_at);
ALTER TABLE public.driver_cash_recoveries ENABLE ROW LEVEL SECURITY;
CREATE POLICY driver_cash_recoveries_read ON public.driver_cash_recoveries FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM public.driver_cash_handins h WHERE h.id = handin_id AND
    (h.driver_user_id = auth.uid() OR public.has_staff_role(ARRAY['admin','finance','sales','dispatcher']::public.staff_role[]) OR public.is_pos_approver())));
REVOKE ALL ON TABLE public.driver_cash_recoveries FROM anon, authenticated;
GRANT SELECT ON TABLE public.driver_cash_recoveries TO authenticated;

INSERT INTO public.pos_approval_policies(action, threshold_value, always_require_manager, reason_required)
VALUES ('driver_cash_write_off', 0, true, true) ON CONFLICT (action) DO NOTHING;
INSERT INTO public.pos_approval_reason_codes(action, code, label, is_active, sort_order, requires_notes) VALUES
 ('driver_cash_write_off','not_recoverable','Cannot be recovered',true,10,true),
 ('driver_cash_write_off','found_not_driver','Investigation found the driver not at fault',true,20,true),
 ('driver_cash_write_off','small_amount','Too small to pursue',true,30,false)
ON CONFLICT DO NOTHING;

-- Posts a balanced two-line journal for a hand-in. Callers have already checked who may do this.
CREATE OR REPLACE FUNCTION private._driver_cash_journal(h public.driver_cash_handins, p_debit text, p_credit text, p_amount numeric, p_what text)
 RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_rate numeric; v_id uuid;
BEGIN
 IF h.currency = 'USD' THEN v_rate := 1;
 ELSE
  SELECT sum(pe.exchange_rate_applied * c.amount) / NULLIF(sum(c.amount), 0) INTO v_rate
    FROM public.delivery_cash_collections c JOIN public.payment_entries pe ON pe.id = c.payment_entry_id
   WHERE c.handin_id = h.id AND pe.exchange_rate_applied > 0;
  v_rate := COALESCE(v_rate, public.get_zig_exchange_rate(CURRENT_DATE));
 END IF;
 PERFORM public._sales_checkout_accounting_enter();
 v_id := public.post_journal_entry(CURRENT_DATE, format('Driver cash %s: %s', h.document_number, p_what), h.currency, v_rate,
   jsonb_build_array(
     jsonb_build_object('account_code', p_debit, 'debit', round(p_amount, 2), 'credit', 0),
     jsonb_build_object('account_code', p_credit, 'debit', 0, 'credit', round(p_amount, 2))));
 PERFORM public._sales_checkout_accounting_exit();
 RETURN v_id;
EXCEPTION WHEN OTHERS THEN PERFORM public._sales_checkout_accounting_exit(); RAISE;
END $f$;

CREATE OR REPLACE FUNCTION private._driver_cash_handin_json(h public.driver_cash_handins)
 RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
 SELECT jsonb_build_object('id',h.id,'document_number',h.document_number,'driver_user_id',h.driver_user_id,
  'driver_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=h.driver_user_id),
  'currency',h.currency,'expected_amount',h.expected_amount,'declared_amount',h.declared_amount,'received_amount',h.received_amount,
  'variance',h.variance,'collection_count',h.collection_count,'status',h.status,'reason_code',h.reason_code,
  'driver_notes',h.driver_notes,'receiver_notes',h.receiver_notes,'approver_notes',h.approver_notes,'submitted_at',h.submitted_at,
  'received_by_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=h.received_by),'received_at',h.received_at,
  'approved_by_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=h.approved_by),'approved_at',h.approved_at,
  'journal_number',(SELECT j.document_number FROM public.journal_entries j WHERE j.id=h.journal_entry_id),
  'owed_amount',h.owed_amount,'recovered_amount',h.recovered_amount,'written_off_amount',h.written_off_amount,
  'outstanding_amount',h.owed_amount-h.recovered_amount-h.written_off_amount,
  'recoveries',COALESCE((SELECT jsonb_agg(jsonb_build_object('kind',r.kind,'amount',r.amount,'notes',r.notes,'created_at',r.created_at,
      'recorded_by_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=r.recorded_by),
      'journal_number',(SELECT j.document_number FROM public.journal_entries j WHERE j.id=r.journal_entry_id)) ORDER BY r.created_at)
    FROM public.driver_cash_recoveries r WHERE r.handin_id=h.id),'[]'::jsonb))
$f$;

-- Manager: accept a difference (someone other than whoever counted it) and post it.
CREATE OR REPLACE FUNCTION public.approve_driver_cash_variance(p_handin_id uuid, p_reason_code text, p_notes text DEFAULT NULL)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE h public.driver_cash_handins%ROWTYPE; v_j uuid; v_owed numeric := 0; v_amt numeric;
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO h FROM public.driver_cash_handins WHERE id=p_handin_id FOR UPDATE;
 IF NOT FOUND OR h.status<>'variance_pending' THEN RAISE EXCEPTION 'hand-in with a difference to review required'; END IF;
 IF h.received_by=auth.uid() THEN RAISE EXCEPTION 'a different manager must approve a difference you counted'; END IF;
 IF h.driver_user_id=auth.uid() THEN RAISE EXCEPTION 'you cannot approve your own hand-in'; END IF;
 IF trim(COALESCE(p_reason_code,''))='' THEN RAISE EXCEPTION 'reason required'; END IF;
 IF NOT EXISTS(SELECT 1 FROM public.pos_approval_reason_codes WHERE action='driver_cash_variance' AND code=p_reason_code AND is_active) THEN
  RAISE EXCEPTION 'unknown reason %', p_reason_code; END IF;
 v_amt := abs(h.variance);
 IF h.variance < 0 AND p_reason_code IN ('driver_short','investigation') THEN
  v_j := private._driver_cash_journal(h, '1250', '1120', v_amt, 'short, owed by the driver');
  v_owed := v_amt;
 ELSIF h.variance < 0 THEN
  v_j := private._driver_cash_journal(h, '5310', '1120', v_amt, 'short (' || p_reason_code || ')');
 ELSE
  v_j := private._driver_cash_journal(h, '1120', '5310', v_amt, 'over (' || p_reason_code || ')');
 END IF;
 UPDATE public.driver_cash_handins SET status='approved', reason_code=p_reason_code, approver_notes=NULLIF(trim(COALESCE(p_notes,'')),''),
  approved_by=auth.uid(), approved_at=now(), journal_entry_id=v_j, owed_amount=v_owed
 WHERE id=h.id RETURNING * INTO h;
 RETURN private._driver_cash_handin_json(h);
END $f$;

-- Cashier / finance / manager: the driver pays back (part of) what they owe, in cash.
CREATE OR REPLACE FUNCTION public.record_driver_cash_recovery(p_handin_id uuid, p_amount numeric, p_notes text DEFAULT NULL)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE h public.driver_cash_handins%ROWTYPE; v_j uuid; v_out numeric;
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin','finance','sales']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'cashier, finance or manager required to take a repayment'; END IF;
 SELECT * INTO h FROM public.driver_cash_handins WHERE id=p_handin_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'hand-in not found'; END IF;
 IF h.driver_user_id=auth.uid() THEN RAISE EXCEPTION 'someone else must take your repayment'; END IF;
 v_out := h.owed_amount - h.recovered_amount - h.written_off_amount;
 IF v_out <= 0 THEN RAISE EXCEPTION 'nothing is owed on %', h.document_number; END IF;
 IF COALESCE(p_amount,0) <= 0 OR round(p_amount,2) > v_out THEN RAISE EXCEPTION 'amount must be between 0.01 and % %', h.currency, v_out; END IF;
 v_j := private._driver_cash_journal(h, '1120', '1250', p_amount, 'repaid by the driver');
 INSERT INTO public.driver_cash_recoveries(handin_id, kind, amount, currency, journal_entry_id, notes, recorded_by)
 VALUES (h.id, 'cash', round(p_amount,2), h.currency, v_j, NULLIF(trim(COALESCE(p_notes,'')),''), auth.uid());
 UPDATE public.driver_cash_handins SET recovered_amount = recovered_amount + round(p_amount,2) WHERE id=h.id RETURNING * INTO h;
 RETURN private._driver_cash_handin_json(h);
END $f$;

-- Manager: write off what cannot be recovered (not the driver, not whoever approved the difference).
CREATE OR REPLACE FUNCTION public.write_off_driver_cash_shortage(p_handin_id uuid, p_amount numeric, p_reason_code text, p_notes text DEFAULT NULL)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE h public.driver_cash_handins%ROWTYPE; v_j uuid; v_out numeric; v_rc record;
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO h FROM public.driver_cash_handins WHERE id=p_handin_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'hand-in not found'; END IF;
 IF h.driver_user_id=auth.uid() THEN RAISE EXCEPTION 'you cannot write off your own shortage'; END IF;
 IF h.approved_by=auth.uid() THEN RAISE EXCEPTION 'a different manager must write off a shortage you approved'; END IF;
 SELECT * INTO v_rc FROM public.pos_approval_reason_codes WHERE action='driver_cash_write_off' AND code=p_reason_code AND is_active;
 IF NOT FOUND THEN RAISE EXCEPTION 'reason required'; END IF;
 IF v_rc.requires_notes AND trim(COALESCE(p_notes,''))='' THEN RAISE EXCEPTION 'notes required for this reason'; END IF;
 v_out := h.owed_amount - h.recovered_amount - h.written_off_amount;
 IF v_out <= 0 THEN RAISE EXCEPTION 'nothing is owed on %', h.document_number; END IF;
 IF COALESCE(p_amount,0) <= 0 OR round(p_amount,2) > v_out THEN RAISE EXCEPTION 'amount must be between 0.01 and % %', h.currency, v_out; END IF;
 v_j := private._driver_cash_journal(h, '5310', '1250', p_amount, 'written off (' || p_reason_code || ')');
 INSERT INTO public.driver_cash_recoveries(handin_id, kind, amount, currency, journal_entry_id, notes, recorded_by)
 VALUES (h.id, 'write_off', round(p_amount,2), h.currency, v_j, concat_ws(': ', p_reason_code, NULLIF(trim(COALESCE(p_notes,'')),'')), auth.uid());
 UPDATE public.driver_cash_handins SET written_off_amount = written_off_amount + round(p_amount,2) WHERE id=h.id RETURNING * INTO h;
 RETURN private._driver_cash_handin_json(h);
END $f$;

-- Back office board: adds what each driver still owes from approved shortages.
CREATE OR REPLACE FUNCTION public.list_driver_cash(p_status text DEFAULT NULL, p_limit integer DEFAULT 100)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin','finance','sales','dispatcher']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'cashier, finance, dispatcher or manager required'; END IF;
 RETURN jsonb_build_object(
  'holding', COALESCE((SELECT jsonb_agg(jsonb_build_object('driver_user_id',g.collected_by,
     'driver_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=g.collected_by),
     'currency',g.currency,'amount',g.amount,'count',g.n,'oldest_at',g.oldest_at) ORDER BY g.oldest_at)
   FROM (SELECT c.collected_by, c.currency, sum(c.amount) AS amount, count(*) AS n, min(c.collected_at) AS oldest_at
         FROM public.delivery_cash_collections c WHERE c.handin_id IS NULL GROUP BY c.collected_by, c.currency) g),'[]'::jsonb),
  'owed', COALESCE((SELECT jsonb_agg(private._driver_cash_handin_json(h) ORDER BY h.approved_at)
   FROM public.driver_cash_handins h WHERE h.owed_amount - h.recovered_amount - h.written_off_amount > 0),'[]'::jsonb),
  'handins', COALESCE((SELECT jsonb_agg(private._driver_cash_handin_json(h) ORDER BY
     CASE h.status WHEN 'submitted' THEN 0 WHEN 'variance_pending' THEN 1 ELSE 2 END, h.submitted_at DESC)
   FROM (SELECT * FROM public.driver_cash_handins WHERE p_status IS NULL OR status=p_status
         ORDER BY status IN ('submitted','variance_pending') DESC, submitted_at DESC
         LIMIT LEAST(GREATEST(COALESCE(p_limit,100),1),500)) h),'[]'::jsonb));
END $f$;

-- Driver: also shows what they owe.
CREATE OR REPLACE FUNCTION public.get_my_driver_cash()
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
BEGIN
 IF NOT public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN RAISE EXCEPTION 'driver role required'; END IF;
 RETURN jsonb_build_object(
  'holding', COALESCE((SELECT jsonb_agg(x ORDER BY x->>'currency') FROM (
    SELECT jsonb_build_object('currency',c.currency,'amount',sum(c.amount),'count',count(*),'oldest_at',min(c.collected_at),
      'collections',jsonb_agg(jsonb_build_object('id',c.id,'amount',c.amount,'collected_at',c.collected_at,
        'job_number',(SELECT j.document_number FROM public.delivery_jobs j WHERE j.id=c.delivery_job_id),
        'invoice_number',(SELECT si.document_number FROM public.sales_invoices si WHERE si.id=c.sales_invoice_id)) ORDER BY c.collected_at)) x
    FROM public.delivery_cash_collections c WHERE c.collected_by=auth.uid() AND c.handin_id IS NULL GROUP BY c.currency) t),'[]'::jsonb),
  'owed', COALESCE((SELECT jsonb_agg(jsonb_build_object('currency',o.currency,'amount',o.amount) ORDER BY o.currency)
    FROM (SELECT currency, sum(owed_amount-recovered_amount-written_off_amount) AS amount FROM public.driver_cash_handins
          WHERE driver_user_id=auth.uid() AND owed_amount-recovered_amount-written_off_amount > 0 GROUP BY currency) o),'[]'::jsonb),
  'handins', COALESCE((SELECT jsonb_agg(private._driver_cash_handin_json(h) ORDER BY h.submitted_at DESC)
    FROM (SELECT * FROM public.driver_cash_handins WHERE driver_user_id=auth.uid() ORDER BY submitted_at DESC LIMIT 10) h),'[]'::jsonb));
END $f$;

REVOKE ALL ON FUNCTION private._driver_cash_journal(public.driver_cash_handins, text, text, numeric, text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.record_driver_cash_recovery(uuid, numeric, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.write_off_driver_cash_shortage(uuid, numeric, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.record_driver_cash_recovery(uuid, numeric, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.write_off_driver_cash_shortage(uuid, numeric, text, text) TO authenticated, service_role;
