-- Exception report: unusual activity over a period, at a branch or all branches.
--
-- Built only from what is already recorded (POS audit, tills, driver cash, card attempts, invoices,
-- deliveries, stock counts, balance approvals). Each exception names who, when, how much (in its own
-- currency) and where to look; the summary by person shows patterns (e.g. one cashier with most of
-- the voids). Who: admin, finance, or a POS manager (approver).

CREATE OR REPLACE FUNCTION public.get_exception_report(p_from date DEFAULT NULL, p_to date DEFAULT NULL, p_warehouse_id uuid DEFAULT NULL)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE
 v_to_day date := COALESCE(p_to, (now() AT TIME ZONE 'Africa/Harare')::date);
 v_from_day date := COALESCE(p_from, v_to_day - 6);
 v_from timestamptz := (v_from_day::timestamp AT TIME ZONE 'Africa/Harare');
 v_to timestamptz := ((v_to_day + 1)::timestamp AT TIME ZONE 'Africa/Harare');
 v_items jsonb;
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'manager, finance or admin required';
 END IF;
 IF v_to_day - v_from_day > 92 THEN RAISE EXCEPTION 'choose at most 93 days'; END IF;

 WITH ex AS (
  -- POS actions that give value away or undo a sale in progress.
  SELECT a.created_at AS at, a.action AS kind,
         CASE a.action WHEN 'cart_voided' THEN 'medium' WHEN 'cart_line_removed' THEN 'medium'
                       WHEN 'paid_order_repaired' THEN 'high' ELSE 'low' END AS severity,
         -- The sale's cashier; the manager who did or approved it (governed actions run as the manager).
         COALESCE(c.created_by, a.actor_user_id) AS person_id,
         COALESCE(a.approved_by_user_id, CASE WHEN a.actor_user_id IS DISTINCT FROM c.created_by THEN a.actor_user_id END) AS approved_by,
         c.warehouse_id, c.currency::text AS currency,
         CASE a.action
          WHEN 'price_override' THEN (a.before_state->>'line_total')::numeric - (a.after_state->>'line_total')::numeric
          WHEN 'discount_applied' THEN
            COALESCE((SELECT SUM((x->>'line_total')::numeric) FROM jsonb_array_elements(CASE WHEN jsonb_typeof(a.before_state)='array' THEN a.before_state ELSE '[]'::jsonb END) x),0)
          - COALESCE((SELECT SUM((x->>'line_total')::numeric) FROM jsonb_array_elements(CASE WHEN jsonb_typeof(a.after_state)='array' THEN a.after_state
                       WHEN jsonb_typeof(a.after_state)='object' AND a.after_state ? 'lines' THEN a.after_state->'lines' ELSE '[]'::jsonb END) x),0)
          WHEN 'cart_line_removed' THEN (a.before_state->'line'->>'line_total')::numeric
          WHEN 'cart_voided' THEN (SELECT SUM(l.line_total) FROM public.pos_cart_lines l WHERE l.cart_id=c.id)
         END AS amount,
         CASE a.action
          WHEN 'cart_voided' THEN 'Sale voided'
          WHEN 'cart_line_removed' THEN 'Part removed after it was rung up'
          WHEN 'discount_applied' THEN 'Discount '||COALESCE(
             CASE WHEN jsonb_typeof(a.after_state)='object' THEN a.after_state->>'discount_percent' END,
             (SELECT x->>'discount_percent' FROM jsonb_array_elements(CASE WHEN jsonb_typeof(a.after_state)='array' THEN a.after_state ELSE '[]'::jsonb END) x
               WHERE x ? 'discount_percent' LIMIT 1),'?')||'%'
          WHEN 'price_override' THEN 'Price changed from '||(a.before_state->>'unit_price')||' to '||(a.after_state->>'unit_price')
          WHEN 'paid_order_repaired' THEN 'Paid order repaired by hand'
         END ||COALESCE(' · '||replace(a.reason_code,'_',' '),'')
         -- Governed actions write "reason · notes" into notes: keep only the free text.
         ||COALESCE(' · '||NULLIF(regexp_replace(a.notes,'^'||COALESCE(a.reason_code,'')||'( · )?',''),''),'') AS detail,
         '/pos' AS href
  FROM public.pos_action_audit a
  LEFT JOIN public.pos_carts c ON c.id = CASE
     WHEN a.entity_type='pos_carts' THEN a.entity_id
     WHEN a.entity_type='pos_cart_line' THEN (SELECT l.cart_id FROM public.pos_cart_lines l WHERE l.id=a.entity_id)
     WHEN a.entity_type='pos_cart_lines' THEN NULLIF(a.before_state->'line'->>'cart_id','')::uuid
     WHEN a.entity_type='commerce_orders' THEN (SELECT o.cart_id FROM public.commerce_orders o WHERE o.id=a.entity_id) END
  WHERE a.created_at>=v_from AND a.created_at<v_to
    AND a.action IN('cart_voided','cart_line_removed','discount_applied','price_override','paid_order_repaired')

  UNION ALL -- Returns posted (who raised it and which manager approved).
  SELECT r.posted_at, 'return', CASE WHEN r.resolution='cash_refund' THEN 'medium' ELSE 'low' END,
         r.created_by, r.approved_by, i.warehouse_id, i.currency::text,
         (SELECT SUM(round(l.qty*l.unit_price,2)) FROM public.pos_return_case_lines l WHERE l.return_case_id=r.id),
         r.document_number||' · '||replace(r.resolution::text,'_',' ')||COALESCE(' · '||replace(r.reason_code,'_',' '),'')
           ||CASE WHEN r.posted_at - i.posted_at < interval '1 hour' THEN ' · within an hour of the sale' ELSE '' END,
         '/pos'
  FROM public.pos_return_cases r JOIN public.sales_invoices i ON i.id=r.source_invoice_id
  WHERE r.status='posted' AND r.posted_at>=v_from AND r.posted_at<v_to

  UNION ALL -- Tills that did not count out right.
  SELECT COALESCE(s.closed_at,s.updated_at), CASE WHEN s.variance<0 THEN 'till_short' ELSE 'till_over' END,
         CASE WHEN abs(s.variance)>=20 THEN 'high' WHEN abs(s.variance)>=5 THEN 'medium' ELSE 'low' END,
         s.operator_user_id, s.approved_by, s.warehouse_id, s.currency::text, s.variance,
         'Counted '||s.counted_cash||', expected '||s.expected_cash||COALESCE(' · '||replace(s.variance_reason_code,'_',' '),'')
           ||CASE WHEN s.approved_by IS NULL THEN ' · not yet approved' ELSE '' END,
         '/pos'
  FROM public.pos_till_sessions s
  WHERE s.variance IS NOT NULL AND abs(s.variance)>0.009 AND COALESCE(s.closed_at,s.updated_at)>=v_from AND COALESCE(s.closed_at,s.updated_at)<v_to

  UNION ALL -- Driver cash that did not count out right.
  SELECT h.received_at, CASE WHEN h.variance<0 THEN 'driver_cash_short' ELSE 'driver_cash_over' END,
         CASE WHEN abs(h.variance)>=20 THEN 'high' ELSE 'medium' END,
         h.driver_user_id, h.approved_by, NULL::uuid, h.currency::text, h.variance,
         h.document_number||' · counted '||h.received_amount||' of '||h.expected_amount||COALESCE(' · '||replace(h.reason_code,'_',' '),''),
         '/staff/logistics/driver-cash'
  FROM public.driver_cash_handins h
  WHERE h.variance IS NOT NULL AND abs(h.variance)>0.009 AND h.received_at>=v_from AND h.received_at<v_to

  UNION ALL -- Card charges that did not go through, or have no answer.
  SELECT a.created_at, 'card_'||a.status, CASE WHEN a.status='unknown' THEN 'high' ELSE 'low' END,
         a.created_by, NULL::uuid, t.warehouse_id, a.currency::text, a.amount,
         COALESCE(t.label,'card machine')||' · '||a.status||COALESCE(' · '||a.response_message,''),
         '/pos'
  FROM public.pos_card_terminal_attempts a LEFT JOIN public.pos_card_terminals t ON t.id=a.terminal_id
  WHERE a.created_at>=v_from AND a.created_at<v_to AND a.status IN('declined','cancelled','unknown','failed')

  UNION ALL -- Sold below cost.
  SELECT i.posted_at, 'below_cost', 'medium', i.posted_by, NULL::uuid, i.warehouse_id, i.currency::text,
         l.cost_total_basis - l.line_total,
         i.document_number||' · '||si.oem_part_number||' sold for '||l.line_total||', cost '||l.cost_total_basis,
         '/staff/finance'
  FROM public.sales_invoice_lines l JOIN public.sales_invoices i ON i.id=l.invoice_id JOIN public.stock_items si ON si.id=l.stock_item_id
  WHERE i.status='posted' AND i.doc_type='invoice' AND i.posted_at>=v_from AND i.posted_at<v_to
    AND NOT l.is_core_charge AND l.cost_total_basis IS NOT NULL AND l.line_total + 0.005 < l.cost_total_basis

  UNION ALL -- Sales outside trading hours (before 07:00 or from 19:00 Harare).
  SELECT i.posted_at, 'after_hours', 'low', i.posted_by, NULL::uuid, i.warehouse_id, i.currency::text, i.total,
         i.document_number||' at '||to_char(i.posted_at AT TIME ZONE 'Africa/Harare','HH24:MI'),
         '/staff/finance'
  FROM public.sales_invoices i
  WHERE i.status='posted' AND i.doc_type='invoice' AND i.till_session_id IS NOT NULL AND i.posted_at>=v_from AND i.posted_at<v_to
    AND (extract(hour FROM i.posted_at AT TIME ZONE 'Africa/Harare')<7 OR extract(hour FROM i.posted_at AT TIME ZONE 'Africa/Harare')>=19)

  UNION ALL -- Deliveries that failed at the door.
  SELECT dj.failed_at, 'delivery_failed', CASE WHEN COALESCE(dj.failure_reason_code::text,'') IN('refused','customer_absent') THEN 'medium' ELSE 'low' END,
         dj.assignee_user_id, NULL::uuid, i.warehouse_id, i.currency::text, i.total,
         dj.document_number||COALESCE(' · '||replace(dj.failure_reason_code::text,'_',' '),'')||COALESCE(' · '||dj.failure_reason,''),
         '/staff/logistics'
  FROM public.delivery_jobs dj JOIN public.delivery_notes dn ON dn.id=dj.delivery_note_id LEFT JOIN public.sales_invoices i ON i.id=dn.sales_invoice_id
  WHERE dj.status='failed' AND dj.failed_at>=v_from AND dj.failed_at<v_to

  UNION ALL -- Balances left on account by a back-office decision (outside the customer's limit).
  SELECT b.decided_at, 'balance_on_account', 'medium', b.requested_by, b.decided_by, i.warehouse_id, b.currency::text, b.amount,
         COALESCE(c.business_name,c.display_name,'customer')||' · '||b.reason, '/staff/logistics/balances'
  FROM public.delivery_balance_approvals b LEFT JOIN public.customers c ON c.id=b.customer_id LEFT JOIN public.sales_invoices i ON i.id=b.sales_invoice_id
  WHERE b.status='approved' AND b.basis='back_office' AND b.decided_at>=v_from AND b.decided_at<v_to

  UNION ALL -- Stock counts that did not match the books.
  SELECT COALESCE(sr.posted_at,sr.created_at), 'stock_count_difference',
         CASE WHEN abs(rl.variance_qty*COALESCE(rl.unit_cost,0))>=100 THEN 'high' ELSE 'medium' END,
         sr.created_by, sr.second_approver_id, sr.warehouse_id, rl.currency::text, rl.variance_qty*COALESCE(rl.unit_cost,0),
         COALESCE(sr.document_number,'count')||' · '||si.oem_part_number||' counted '||rl.counted_qty||', books '||rl.system_qty
           ||CASE WHEN sr.second_approver_id IS NULL THEN ' · posted by one person (under the two-person limit)' ELSE '' END,
         '/staff/warehouse'
  FROM public.stock_reconciliation_lines rl JOIN public.stock_reconciliations sr ON sr.id=rl.stock_reconciliation_id
  JOIN public.stock_items si ON si.id=rl.stock_item_id
  WHERE abs(COALESCE(rl.variance_qty,0))>0.0001 AND COALESCE(sr.posted_at,sr.created_at)>=v_from AND COALESCE(sr.posted_at,sr.created_at)<v_to
 ), scoped AS (
  SELECT * FROM ex WHERE p_warehouse_id IS NULL OR ex.warehouse_id=p_warehouse_id OR (ex.warehouse_id IS NULL AND ex.kind LIKE 'driver_cash%')
 )
 SELECT jsonb_build_object(
  'from', v_from_day, 'to', v_to_day, 'warehouse_id', p_warehouse_id,
  'warehouse_name', (SELECT w.name FROM public.warehouses w WHERE w.id=p_warehouse_id),
  'items', COALESCE((SELECT jsonb_agg(jsonb_build_object(
      'at', s.at, 'kind', s.kind, 'severity', s.severity,
      'person_id', s.person_id, 'person', (SELECT p.full_name FROM public.profiles p WHERE p.id=s.person_id),
      'approved_by', (SELECT p.full_name FROM public.profiles p WHERE p.id=s.approved_by),
      'warehouse', (SELECT w.name FROM public.warehouses w WHERE w.id=s.warehouse_id),
      'currency', s.currency, 'amount', round(s.amount,2), 'detail', s.detail, 'href', s.href)
     ORDER BY CASE s.severity WHEN 'high' THEN 0 WHEN 'medium' THEN 1 ELSE 2 END, s.at DESC)
    FROM (SELECT * FROM scoped ORDER BY at DESC LIMIT 500) s), '[]'::jsonb),
  'by_kind', COALESCE((SELECT jsonb_agg(jsonb_build_object('kind', k.kind, 'count', k.n, 'currency', k.currency, 'amount', k.amount) ORDER BY k.n DESC)
    FROM (SELECT kind, currency, count(*) n, round(SUM(abs(COALESCE(amount,0))),2) amount FROM scoped GROUP BY kind, currency) k), '[]'::jsonb),
  'by_person', COALESCE((SELECT jsonb_agg(jsonb_build_object('person_id', b.person_id, 'person', (SELECT p.full_name FROM public.profiles p WHERE p.id=b.person_id),
       'count', b.n, 'high', b.high, 'kinds', b.kinds) ORDER BY b.high DESC, b.n DESC)
    FROM (SELECT person_id, count(*) n, count(*) FILTER (WHERE severity='high') high,
                 jsonb_object_agg(kind, kn) kinds
          FROM (SELECT person_id, kind, severity, count(*) OVER (PARTITION BY person_id, kind) kn FROM scoped WHERE person_id IS NOT NULL) z
          GROUP BY person_id) b), '[]'::jsonb)
 ) INTO v_items;
 RETURN v_items;
END $f$;
REVOKE ALL ON FUNCTION public.get_exception_report(date, date, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_exception_report(date, date, uuid) TO authenticated;
