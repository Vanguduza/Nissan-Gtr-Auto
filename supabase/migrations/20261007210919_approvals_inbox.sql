-- Approvals inbox with alerts.
--
-- One list of everything waiting for a decision that the signed-in person may take, computed live
-- from the source tables (nothing is copied, so an item disappears the moment it is decided).
-- A sweep every 5 minutes alerts the people who can act on an item that has waited too long
-- (urgent: 10 minutes, e.g. a driver at the door; others: 4 hours), once per person per item,
-- through the existing staff_ops_notifications.

ALTER TYPE public.staff_ops_notification_kind ADD VALUE IF NOT EXISTS 'approval_waiting';

ALTER TABLE public.staff_ops_notifications ADD COLUMN IF NOT EXISTS ref_key text;
ALTER TABLE public.staff_ops_notifications ADD COLUMN IF NOT EXISTS href text;
CREATE UNIQUE INDEX IF NOT EXISTS staff_ops_notifications_recipient_ref_uidx
  ON public.staff_ops_notifications(recipient_user_id, ref_key) WHERE ref_key IS NOT NULL;

-- Role check for any user (has_staff_role only answers for the caller).
CREATE OR REPLACE FUNCTION private.user_has_staff_role(p_uid uuid, p_roles public.staff_role[])
 RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
 SELECT EXISTS(SELECT 1 FROM public.staff_roles sr WHERE sr.user_id=p_uid AND sr.role=ANY(p_roles));
$f$;

-- Everything [p_uid] can decide now. Each item: kind, ref (source id), title, detail, amount,
-- currency, waiting_since, urgent, href (the screen where it is decided).
CREATE OR REPLACE FUNCTION private.approvals_for(p_uid uuid)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE
 v_admin boolean := private.user_has_staff_role(p_uid, ARRAY['admin']::public.staff_role[]);
 v_finance boolean := private.user_has_staff_role(p_uid, ARRAY['admin','finance']::public.staff_role[]);
 v_dispatch boolean := private.user_has_staff_role(p_uid, ARRAY['admin','dispatcher']::public.staff_role[]);
 v_counter boolean := private.user_has_staff_role(p_uid, ARRAY['admin','finance','sales','dispatcher']::public.staff_role[]);
 v_warehouse boolean := private.user_has_staff_role(p_uid, ARRAY['admin','warehouse']::public.staff_role[]);
 v_manager boolean := private.pos_approver_source(p_uid) IS NOT NULL OR private.user_has_staff_role(p_uid, ARRAY['admin']::public.staff_role[]);
 v_items jsonb := '[]'::jsonb;
BEGIN
 IF p_uid IS NULL OR NOT private.user_has_staff_role(p_uid, ARRAY['admin','finance','sales','dispatcher','warehouse']::public.staff_role[]) THEN
  RETURN v_items;
 END IF;

 IF v_dispatch THEN
  v_items := v_items || COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'kind','delivery_balance','ref',a.id,'urgent',true,'href','/staff/logistics/balances',
    'title','Balance on account: '||COALESCE(c.business_name,c.display_name,'customer'),
    'detail','Driver waiting at the door. '||a.reason,
    'amount',a.amount,'currency',a.currency,'waiting_since',a.created_at))
   FROM public.delivery_balance_approvals a LEFT JOIN public.customers c ON c.id=a.customer_id
   WHERE a.status='pending'),'[]'::jsonb);
 END IF;

 IF v_counter THEN
  v_items := v_items || COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'kind','driver_cash_count','ref',h.id,'urgent',false,'href','/staff/logistics/driver-cash',
    'title','Count driver cash: '||COALESCE(p.full_name,'driver'),
    'detail',h.document_number||' · '||h.collection_count||' collections',
    'amount',h.expected_amount,'currency',h.currency,'waiting_since',h.submitted_at))
   FROM public.driver_cash_handins h LEFT JOIN public.profiles p ON p.id=h.driver_user_id
   WHERE h.status='submitted' AND h.driver_user_id<>p_uid),'[]'::jsonb);
 END IF;

 IF v_manager THEN
  v_items := v_items || COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'kind','driver_cash_difference','ref',h.id,'urgent',false,'href','/staff/logistics/driver-cash',
    'title','Driver cash difference: '||COALESCE(p.full_name,'driver'),
    'detail',h.document_number||' · counted '||h.received_amount||' of '||h.expected_amount,
    'amount',h.variance,'currency',h.currency,'waiting_since',h.received_at))
   FROM public.driver_cash_handins h LEFT JOIN public.profiles p ON p.id=h.driver_user_id
   WHERE h.status='variance_pending' AND h.received_by<>p_uid AND h.driver_user_id<>p_uid),'[]'::jsonb);

  v_items := v_items || COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'kind','till_variance','ref',s.id,'urgent',false,'href','/pos',
    'title','Till difference: '||COALESCE(w.name,'till')||' · '||COALESCE(p.full_name,'cashier'),
    'detail','Counted '||s.counted_cash||', expected '||s.expected_cash,
    'amount',s.variance,'currency',s.currency,'waiting_since',COALESCE(s.closed_at,s.updated_at)))
   FROM public.pos_till_sessions s LEFT JOIN public.warehouses w ON w.id=s.warehouse_id
   LEFT JOIN public.profiles p ON p.id=s.operator_user_id
   WHERE s.status='variance_pending' AND s.operator_user_id<>p_uid),'[]'::jsonb);

  v_items := v_items || COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'kind','return','ref',r.id,'urgent',false,'href','/pos',
    'title','Return to approve: '||r.document_number,
    'detail',replace(r.resolution::text,'_',' ')||' · '||COALESCE(r.reason_code,''),
    'amount',(SELECT SUM(round(l.qty*l.unit_price,2)) FROM public.pos_return_case_lines l WHERE l.return_case_id=r.id),
    'currency',i.currency,'waiting_since',r.created_at))
   FROM public.pos_return_cases r JOIN public.sales_invoices i ON i.id=r.source_invoice_id
   WHERE r.status='draft'),'[]'::jsonb);

  v_items := v_items || COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'kind','split_refund','ref',x.id,'urgent',false,'href','/pos',
    'title','Part-payment refund to approve','detail',COALESCE(x.failure_reason,x.notes,''),
    'amount',x.gross_amount,'currency',NULL,'waiting_since',x.created_at))
   FROM public.pos_split_refund_requests x WHERE x.status IN('review','failed')),'[]'::jsonb);

  v_items := v_items || COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'kind','warranty','ref',w.id,'urgent',false,'href','/staff/warranty',
    'title','Warranty claim: '||w.document_number,'detail',COALESCE(w.notes,''),
    'amount',NULL,'currency',w.currency,'waiting_since',w.created_at))
   FROM public.warranty_claims w WHERE w.status='open'),'[]'::jsonb);
 END IF;

 IF v_manager OR v_finance THEN
  -- Card charges with no posted outcome: the customer may have been charged.
  v_items := v_items || COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'kind','card_unresolved','ref',a.id,'urgent',true,'href','/pos',
    'title','Card payment to reconcile'||COALESCE(' ('||t.label||')',''),
    'detail',a.status||COALESCE(' · '||a.finalization_error,''),
    'amount',a.amount,'currency',a.currency,'waiting_since',a.created_at))
   FROM public.pos_card_terminal_attempts a LEFT JOIN public.pos_card_terminals t ON t.id=a.terminal_id
   WHERE a.status IN('unknown','approved') OR (a.status='initiated' AND a.created_at<now()-interval '10 minutes')
      OR (a.finalization_error IS NOT NULL AND a.status<>'settled')),'[]'::jsonb);
 END IF;

 IF v_finance THEN
  v_items := v_items || COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'kind','requisition','ref',r.id,'urgent',false,'href','/staff/finance',
    'title','Requisition: '||COALESCE(r.payee,r.document_number),
    'detail',COALESCE(r.memo,'')||' · approvals '||COALESCE(r.approval_count,0)||'/'||COALESCE(r.required_approvals,1),
    'amount',r.amount,'currency',r.currency,'waiting_since',COALESCE(r.submitted_at,r.created_at)))
   FROM public.finance_requisitions r
   WHERE r.status='submitted' AND r.requested_by<>p_uid
     AND NOT EXISTS(SELECT 1 FROM public.finance_requisition_approvals x WHERE x.requisition_id=r.id AND x.approver_user_id=p_uid)),'[]'::jsonb);
 END IF;

 IF v_warehouse THEN
  v_items := v_items || COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'kind','transfer_send','ref',f.id,'urgent',f.customer_id IS NOT NULL,'href','/pos',
    'title','Send to '||COALESCE(dw.name,'branch')||': '||COALESCE(si.oem_part_number,''),
    'detail',f.document_number||' · from '||COALESCE(sw.name,'')||CASE WHEN f.customer_id IS NOT NULL THEN ' · customer waiting' ELSE '' END,
    'amount',f.qty,'currency',NULL,'waiting_since',f.created_at))
   FROM public.pos_fulfillment_requests f
   LEFT JOIN public.warehouses sw ON sw.id=f.source_warehouse_id LEFT JOIN public.warehouses dw ON dw.id=f.destination_warehouse_id
   LEFT JOIN public.stock_items si ON si.id=f.stock_item_id
   WHERE f.kind='branch_transfer' AND f.status='reserved'),'[]'::jsonb);

  v_items := v_items || COALESCE((SELECT jsonb_agg(jsonb_build_object(
    'kind','stock_transfer','ref',e.id,'urgent',false,'href','/staff/warehouse/transfers',
    'title','Receive transfer '||COALESCE(e.document_number,''),
    'detail',COALESCE(fw.name,'')||' → '||COALESCE(tw.name,'')||COALESCE(' · '||e.notes,''),
    'amount',NULL,'currency',NULL,'waiting_since',e.created_at))
   FROM public.stock_entries e
   LEFT JOIN public.warehouses fw ON fw.id=e.from_warehouse_id LEFT JOIN public.warehouses tw ON tw.id=e.to_warehouse_id
   WHERE e.entry_type='transfer' AND e.status='pending_approval'
     AND COALESCE(e.first_approver_id,e.created_by)<>p_uid),'[]'::jsonb);
 END IF;

 RETURN COALESCE((SELECT jsonb_agg(x ORDER BY (x->>'urgent')::boolean DESC, x->>'waiting_since')
                  FROM jsonb_array_elements(v_items) x),'[]'::jsonb);
END $f$;
REVOKE ALL ON FUNCTION private.approvals_for(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.user_has_staff_role(uuid, public.staff_role[]) FROM PUBLIC, anon, authenticated;

-- The signed-in person's inbox, plus their unread alerts.
CREATE OR REPLACE FUNCTION public.list_my_approvals()
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
BEGIN
 RETURN jsonb_build_object(
  'items', private.approvals_for(auth.uid()),
  'alerts', COALESCE((SELECT jsonb_agg(jsonb_build_object('id',n.id,'title',n.title,'body',n.body,'href',n.href,'created_at',n.created_at)
                       ORDER BY n.created_at DESC)
                      FROM public.staff_ops_notifications n
                      WHERE n.recipient_user_id=auth.uid() AND n.kind='approval_waiting' AND n.read_at IS NULL),'[]'::jsonb));
END $f$;
REVOKE ALL ON FUNCTION public.list_my_approvals() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.list_my_approvals() TO authenticated;

-- Alerts: anything waiting longer than its limit, once per person who can decide it.
CREATE OR REPLACE FUNCTION private.sweep_approval_alerts()
 RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE u record; it jsonb; v_n integer := 0; v_limit interval;
BEGIN
 PERFORM set_config('app.staff_ops_notify','1',true);
 FOR u IN SELECT DISTINCT sr.user_id FROM public.staff_roles sr
          WHERE sr.role IN('admin','finance','sales','dispatcher','warehouse') LOOP
  FOR it IN SELECT * FROM jsonb_array_elements(private.approvals_for(u.user_id)) LOOP
   v_limit := CASE WHEN (it->>'urgent')::boolean THEN interval '10 minutes' ELSE interval '4 hours' END;
   CONTINUE WHEN (it->>'waiting_since')::timestamptz > now() - v_limit;
   INSERT INTO public.staff_ops_notifications(kind, recipient_user_id, title, body, ref_key, href)
   VALUES ('approval_waiting', u.user_id,
           CASE WHEN (it->>'urgent')::boolean THEN 'Urgent: ' ELSE 'Waiting: ' END || (it->>'title'),
           COALESCE(it->>'detail','') || ' · waiting since ' || to_char((it->>'waiting_since')::timestamptz AT TIME ZONE 'Africa/Harare','DD Mon HH24:MI'),
           (it->>'kind')||':'||(it->>'ref'), it->>'href')
   ON CONFLICT (recipient_user_id, ref_key) WHERE ref_key IS NOT NULL DO NOTHING;
   IF FOUND THEN v_n := v_n + 1; END IF;
  END LOOP;
 END LOOP;
 PERFORM set_config('app.staff_ops_notify','',true);
 RETURN v_n;
END $f$;
REVOKE ALL ON FUNCTION private.sweep_approval_alerts() FROM PUBLIC, anon, authenticated;

-- Mark an alert read (own alerts only).
CREATE OR REPLACE FUNCTION public.dismiss_approval_alert(p_id uuid)
 RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path TO '' AS $f$
 UPDATE public.staff_ops_notifications SET read_at=now() WHERE id=p_id AND recipient_user_id=auth.uid() AND read_at IS NULL;
$f$;
REVOKE ALL ON FUNCTION public.dismiss_approval_alert(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.dismiss_approval_alert(uuid) TO authenticated;

DO $do$
BEGIN
 IF EXISTS(SELECT 1 FROM pg_extension WHERE extname='pg_cron') THEN
  PERFORM cron.unschedule(jobid) FROM cron.job WHERE jobname='approval-alerts-sweep-v1';
  PERFORM cron.schedule('approval-alerts-sweep-v1','*/5 * * * *','SELECT private.sweep_approval_alerts()');
 END IF;
END $do$;
