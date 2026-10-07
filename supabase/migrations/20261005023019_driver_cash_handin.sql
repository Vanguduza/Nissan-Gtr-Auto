-- Driver cash hand-in. Cash a driver collects at the door (collect_delivery_cash) stays with that
-- driver until it is handed in: the driver declares what they counted, a cashier / finance /
-- dispatcher counts it on receipt, and any difference goes to a manager (POS approver) with a reason,
-- as a till close does. The back office sees the cash each driver still holds and for how long.
-- No journal is posted: the cash was already booked to the cash account when it was collected; a
-- difference is recorded and approved, like a till variance.

CREATE TABLE public.driver_cash_handins (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  document_number text NOT NULL,
  driver_user_id uuid NOT NULL REFERENCES auth.users(id),
  currency public.currency_code NOT NULL,
  expected_amount numeric(14,2) NOT NULL,
  declared_amount numeric(14,2) NOT NULL CHECK (declared_amount >= 0),
  received_amount numeric(14,2) CHECK (received_amount >= 0),
  variance numeric(14,2),
  collection_count integer NOT NULL,
  status text NOT NULL CHECK (status IN ('submitted','received','variance_pending','approved')),
  reason_code text,
  driver_notes text,
  receiver_notes text,
  approver_notes text,
  submitted_at timestamptz NOT NULL DEFAULT now(),
  received_by uuid REFERENCES auth.users(id),
  received_at timestamptz,
  approved_by uuid REFERENCES auth.users(id),
  approved_at timestamptz,
  CHECK (approved_by IS NULL OR approved_by IS DISTINCT FROM received_by)
);
CREATE UNIQUE INDEX driver_cash_handins_one_open ON public.driver_cash_handins(driver_user_id, currency) WHERE status = 'submitted';
CREATE INDEX driver_cash_handins_status_idx ON public.driver_cash_handins(status, submitted_at DESC);

ALTER TABLE public.delivery_cash_collections ADD COLUMN handin_id uuid REFERENCES public.driver_cash_handins(id);
CREATE INDEX delivery_cash_collections_unhanded_idx ON public.delivery_cash_collections(collected_by, currency) WHERE handin_id IS NULL;

ALTER TABLE public.driver_cash_handins ENABLE ROW LEVEL SECURITY;
CREATE POLICY driver_cash_handins_read ON public.driver_cash_handins FOR SELECT TO authenticated
  USING (driver_user_id = auth.uid() OR public.has_staff_role(ARRAY['admin','finance','sales','dispatcher']::public.staff_role[]) OR public.is_pos_approver());
REVOKE ALL ON TABLE public.driver_cash_handins FROM anon;
GRANT SELECT ON TABLE public.driver_cash_handins TO authenticated;

INSERT INTO public.naming_series(prefix, current_value, pad_length, description)
VALUES ('DCH-', 0, 5, 'Driver cash hand-in') ON CONFLICT (prefix) DO NOTHING;

INSERT INTO public.pos_approval_policies(action, threshold_value, always_require_manager, reason_required)
VALUES ('driver_cash_variance', 0, true, true) ON CONFLICT (action) DO NOTHING;
INSERT INTO public.pos_approval_reason_codes(action, code, label, is_active, sort_order, requires_notes) VALUES
 ('driver_cash_variance','count_error','Recount / counting error',true,10,false),
 ('driver_cash_variance','driver_short','Driver short — to be recovered',true,20,true),
 ('driver_cash_variance','customer_paid_less','Customer paid less than recorded',true,30,true),
 ('driver_cash_variance','investigation','Under investigation',true,40,true)
ON CONFLICT DO NOTHING;

CREATE OR REPLACE FUNCTION private._driver_cash_handin_json(h public.driver_cash_handins)
 RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
 SELECT jsonb_build_object('id',h.id,'document_number',h.document_number,'driver_user_id',h.driver_user_id,
  'driver_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=h.driver_user_id),
  'currency',h.currency,'expected_amount',h.expected_amount,'declared_amount',h.declared_amount,'received_amount',h.received_amount,
  'variance',h.variance,'collection_count',h.collection_count,'status',h.status,'reason_code',h.reason_code,
  'driver_notes',h.driver_notes,'receiver_notes',h.receiver_notes,'approver_notes',h.approver_notes,'submitted_at',h.submitted_at,
  'received_by_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=h.received_by),'received_at',h.received_at,
  'approved_by_name',(SELECT p.full_name FROM public.profiles p WHERE p.id=h.approved_by),'approved_at',h.approved_at)
$f$;

-- Driver: cash I still hold, per currency, and my hand-ins waiting to be received or reviewed.
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
  'handins', COALESCE((SELECT jsonb_agg(private._driver_cash_handin_json(h) ORDER BY h.submitted_at DESC)
    FROM (SELECT * FROM public.driver_cash_handins WHERE driver_user_id=auth.uid() ORDER BY submitted_at DESC LIMIT 10) h),'[]'::jsonb));
END $f$;

-- Driver: hand in everything collected in [p_currency] so far, declaring what was counted.
CREATE OR REPLACE FUNCTION public.submit_driver_cash_handin(p_currency public.currency_code, p_declared_amount numeric, p_notes text DEFAULT NULL)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE h public.driver_cash_handins%ROWTYPE; v_expected numeric; v_count integer;
BEGIN
 IF NOT public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN RAISE EXCEPTION 'driver role required'; END IF;
 IF COALESCE(p_declared_amount,-1) < 0 THEN RAISE EXCEPTION 'declared amount must be >= 0'; END IF;
 IF EXISTS(SELECT 1 FROM public.driver_cash_handins WHERE driver_user_id=auth.uid() AND currency=p_currency AND status='submitted') THEN
  RAISE EXCEPTION 'your last % hand-in is still waiting to be received', p_currency; END IF;
 SELECT COALESCE(sum(amount),0), count(*) INTO v_expected, v_count FROM public.delivery_cash_collections
 WHERE collected_by=auth.uid() AND currency=p_currency AND handin_id IS NULL;
 IF v_count=0 THEN RAISE EXCEPTION 'no % cash to hand in', p_currency; END IF;
 INSERT INTO public.driver_cash_handins(document_number,driver_user_id,currency,expected_amount,declared_amount,collection_count,status,driver_notes)
 VALUES(public.next_series_value('DCH-'),auth.uid(),p_currency,round(v_expected,2),round(p_declared_amount,2),v_count,'submitted',NULLIF(trim(COALESCE(p_notes,'')),''))
 RETURNING * INTO h;
 UPDATE public.delivery_cash_collections SET handin_id=h.id WHERE collected_by=auth.uid() AND currency=p_currency AND handin_id IS NULL;
 RETURN private._driver_cash_handin_json(h);
END $f$;

-- Cashier / finance / dispatch: count the cash on receipt. A difference goes to a manager.
CREATE OR REPLACE FUNCTION public.receive_driver_cash_handin(p_handin_id uuid, p_counted_amount numeric, p_reason_code text DEFAULT NULL, p_notes text DEFAULT NULL)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE h public.driver_cash_handins%ROWTYPE; v_var numeric;
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin','finance','sales','dispatcher']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'cashier, finance or dispatcher required to receive driver cash'; END IF;
 IF COALESCE(p_counted_amount,-1) < 0 THEN RAISE EXCEPTION 'counted amount must be >= 0'; END IF;
 SELECT * INTO h FROM public.driver_cash_handins WHERE id=p_handin_id FOR UPDATE;
 IF NOT FOUND OR h.status<>'submitted' THEN RAISE EXCEPTION 'hand-in waiting to be received required'; END IF;
 IF h.driver_user_id=auth.uid() THEN RAISE EXCEPTION 'someone else must receive your cash'; END IF;
 v_var := round(p_counted_amount - h.expected_amount, 2);
 IF abs(v_var) > 0.009 AND trim(COALESCE(p_reason_code,''))='' THEN RAISE EXCEPTION 'reason required for a cash difference'; END IF;
 IF abs(v_var) > 0.009 AND NOT EXISTS(SELECT 1 FROM public.pos_approval_reason_codes WHERE action='driver_cash_variance' AND code=p_reason_code AND is_active) THEN
  RAISE EXCEPTION 'unknown reason %', p_reason_code; END IF;
 UPDATE public.driver_cash_handins SET received_amount=round(p_counted_amount,2), variance=v_var,
  status=CASE WHEN abs(v_var) > 0.009 THEN 'variance_pending' ELSE 'received' END,
  reason_code=NULLIF(trim(COALESCE(p_reason_code,'')),''), receiver_notes=NULLIF(trim(COALESCE(p_notes,'')),''),
  received_by=auth.uid(), received_at=now()
 WHERE id=h.id RETURNING * INTO h;
 RETURN private._driver_cash_handin_json(h);
END $f$;

-- Manager: accept a difference (someone other than whoever counted it).
CREATE OR REPLACE FUNCTION public.approve_driver_cash_variance(p_handin_id uuid, p_reason_code text, p_notes text DEFAULT NULL)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE h public.driver_cash_handins%ROWTYPE;
BEGIN
 IF NOT public.is_pos_approver() THEN RAISE EXCEPTION 'POS manager approval required'; END IF;
 SELECT * INTO h FROM public.driver_cash_handins WHERE id=p_handin_id FOR UPDATE;
 IF NOT FOUND OR h.status<>'variance_pending' THEN RAISE EXCEPTION 'hand-in with a difference to review required'; END IF;
 IF h.received_by=auth.uid() THEN RAISE EXCEPTION 'a different manager must approve a difference you counted'; END IF;
 IF h.driver_user_id=auth.uid() THEN RAISE EXCEPTION 'you cannot approve your own hand-in'; END IF;
 IF trim(COALESCE(p_reason_code,''))='' THEN RAISE EXCEPTION 'reason required'; END IF;
 UPDATE public.driver_cash_handins SET status='approved', reason_code=p_reason_code, approver_notes=NULLIF(trim(COALESCE(p_notes,'')),''),
  approved_by=auth.uid(), approved_at=now()
 WHERE id=h.id RETURNING * INTO h;
 RETURN private._driver_cash_handin_json(h);
END $f$;

-- Back office: cash each driver still holds (and since when), plus hand-ins by status.
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
  'handins', COALESCE((SELECT jsonb_agg(private._driver_cash_handin_json(h) ORDER BY
     CASE h.status WHEN 'submitted' THEN 0 WHEN 'variance_pending' THEN 1 ELSE 2 END, h.submitted_at DESC)
   FROM (SELECT * FROM public.driver_cash_handins WHERE p_status IS NULL OR status=p_status
         ORDER BY status IN ('submitted','variance_pending') DESC, submitted_at DESC
         LIMIT LEAST(GREATEST(COALESCE(p_limit,100),1),500)) h),'[]'::jsonb));
END $f$;

REVOKE ALL ON FUNCTION private._driver_cash_handin_json(public.driver_cash_handins) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.get_my_driver_cash() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.submit_driver_cash_handin(public.currency_code, numeric, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.receive_driver_cash_handin(uuid, numeric, text, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.approve_driver_cash_variance(uuid, text, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.list_driver_cash(text, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_driver_cash() TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.submit_driver_cash_handin(public.currency_code, numeric, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.receive_driver_cash_handin(uuid, numeric, text, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.approve_driver_cash_variance(uuid, text, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_driver_cash(text, integer) TO authenticated, service_role;
