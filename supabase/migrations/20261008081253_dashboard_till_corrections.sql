-- Daily dashboard: each till also lists its corrections during the day (cash pay-ins and pay-outs,
-- bank drops, cash refunds, with reason and who) and, for a counted difference, the reason and the
-- manager who signed it off.
CREATE OR REPLACE FUNCTION public.get_daily_dashboard(p_date date DEFAULT NULL::date, p_warehouse_id uuid DEFAULT NULL::uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE
 v_day date := COALESCE(p_date, (now() AT TIME ZONE 'Africa/Harare')::date);
 v_from timestamptz := (v_day::timestamp AT TIME ZONE 'Africa/Harare');
 v_to timestamptz := ((v_day + 1)::timestamp AT TIME ZONE 'Africa/Harare');
 v_lw_from timestamptz := ((v_day - 7)::timestamp AT TIME ZONE 'Africa/Harare');
 v_lw_to timestamptz := ((v_day - 6)::timestamp AT TIME ZONE 'Africa/Harare');
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'manager, finance or admin required';
 END IF;

 RETURN jsonb_build_object(
  'date', v_day,
  'warehouse_id', p_warehouse_id,
  'warehouse_name', (SELECT w.name FROM public.warehouses w WHERE w.id=p_warehouse_id),
  'generated_at', now(),

  -- Sales (invoices) less returns (credit notes), with gross margin from the cost basis.
  'sales', COALESCE((SELECT jsonb_agg(jsonb_build_object(
     'currency', x.currency, 'invoices', x.n, 'gross', x.gross, 'returns', x.returns, 'net', x.gross - x.returns,
     'cost', x.cost, 'margin', x.gross - x.returns - x.cost,
     'margin_pct', CASE WHEN x.gross - x.returns > 0 THEN round(100 * (x.gross - x.returns - x.cost) / (x.gross - x.returns), 1) END,
     'average_sale', CASE WHEN x.n > 0 THEN round(x.gross / x.n, 2) END,
     'last_week_net', COALESCE((SELECT SUM(CASE WHEN i.doc_type='invoice' THEN i.total ELSE -i.total END) FROM public.sales_invoices i
        WHERE i.status='posted' AND i.currency=x.currency AND i.posted_at>=v_lw_from AND i.posted_at<v_lw_to
          AND (p_warehouse_id IS NULL OR COALESCE((SELECT s0.warehouse_id FROM public.sales_invoices s0 WHERE s0.id=i.return_against_id), i.warehouse_id)=p_warehouse_id)), 0)) ORDER BY x.currency)
   FROM (SELECT i.currency,
           count(*) FILTER (WHERE i.doc_type='invoice') n,
           COALESCE(SUM(i.total) FILTER (WHERE i.doc_type='invoice'),0) gross,
           COALESCE(SUM(i.total) FILTER (WHERE i.doc_type='credit_note'),0) returns,
           COALESCE(SUM((SELECT SUM(COALESCE(l.cost_total_basis,0)) FROM public.sales_invoice_lines l WHERE l.invoice_id=i.id))
             FILTER (WHERE i.doc_type='invoice'),0)
           - COALESCE(SUM((SELECT SUM(COALESCE(l.cost_total_basis,0)) FROM public.sales_invoice_lines l WHERE l.invoice_id=i.id))
             FILTER (WHERE i.doc_type='credit_note'),0) cost
         FROM public.sales_invoices i
         WHERE i.status='posted' AND i.posted_at>=v_from AND i.posted_at<v_to
           AND (p_warehouse_id IS NULL OR COALESCE((SELECT s0.warehouse_id FROM public.sales_invoices s0 WHERE s0.id=i.return_against_id), i.warehouse_id)=p_warehouse_id)
         GROUP BY i.currency) x), '[]'::jsonb),

  -- Where the sales came from.
  'channels', COALESCE((SELECT jsonb_agg(jsonb_build_object('channel', c.channel, 'currency', c.currency, 'invoices', c.n, 'total', c.total)
                         ORDER BY c.currency, c.total DESC)
   FROM (SELECT CASE WHEN i.till_session_id IS NOT NULL THEN 'counter'
                     WHEN i.fulfillment_mode='dispatch' THEN 'delivery'
                     ELSE 'online_pickup' END channel, i.currency, count(*) n, SUM(i.total) total
         FROM public.sales_invoices i
         WHERE i.status='posted' AND i.doc_type='invoice' AND i.posted_at>=v_from AND i.posted_at<v_to
           AND (p_warehouse_id IS NULL OR i.warehouse_id=p_warehouse_id)
         GROUP BY 1,2) c), '[]'::jsonb),

  -- Money taken today, by tender (payments posted today, any invoice date).
  'payments', COALESCE((SELECT jsonb_agg(jsonb_build_object('tender', p.tender, 'currency', p.currency, 'count', p.n, 'amount', p.amount)
                         ORDER BY p.currency, p.amount DESC)
   FROM (SELECT pe.tender::text tender, pe.currency, count(*) n, SUM(pe.amount) amount
         FROM public.payment_entries pe
         WHERE pe.status='posted' AND pe.posted_at>=v_from AND pe.posted_at<v_to
           AND (p_warehouse_id IS NULL OR EXISTS(SELECT 1 FROM public.payment_allocations pa JOIN public.sales_invoices i ON i.id=pa.sales_invoice_id
                                                WHERE pa.payment_entry_id=pe.id AND i.warehouse_id=p_warehouse_id))
         GROUP BY 1,2) p), '[]'::jsonb),

  -- Sold today but not paid (on account / pay on delivery still owed).
  'unpaid_today', COALESCE((SELECT jsonb_agg(jsonb_build_object('currency', u.currency, 'invoices', u.n, 'amount', u.amount))
   FROM (SELECT i.currency, count(*) n, SUM(i.total - COALESCE(i.amount_paid,0)) amount
         FROM public.sales_invoices i
         WHERE i.status='posted' AND i.doc_type='invoice' AND i.posted_at>=v_from AND i.posted_at<v_to
           AND i.total - COALESCE(i.amount_paid,0) > 0.004
           AND (p_warehouse_id IS NULL OR i.warehouse_id=p_warehouse_id)
         GROUP BY 1) u), '[]'::jsonb),

  -- Sales by hour (Harare), for the day's shape.
  'hourly', COALESCE((SELECT jsonb_agg(jsonb_build_object('hour', h.hr, 'currency', h.currency, 'total', h.total) ORDER BY h.currency, h.hr)
   FROM (SELECT extract(hour FROM i.posted_at AT TIME ZONE 'Africa/Harare')::int hr, i.currency, SUM(i.total) total
         FROM public.sales_invoices i
         WHERE i.status='posted' AND i.doc_type='invoice' AND i.posted_at>=v_from AND i.posted_at<v_to
           AND (p_warehouse_id IS NULL OR i.warehouse_id=p_warehouse_id)
         GROUP BY 1,2) h), '[]'::jsonb),

  'top_parts', COALESCE((SELECT jsonb_agg(t ORDER BY (t->>'total')::numeric DESC)
   FROM (SELECT jsonb_build_object('oem_part_number', si.oem_part_number, 'description', si.description, 'currency', i.currency,
                 'qty', SUM(l.qty), 'total', SUM(l.line_total)) t
         FROM public.sales_invoice_lines l JOIN public.sales_invoices i ON i.id=l.invoice_id JOIN public.stock_items si ON si.id=l.stock_item_id
         WHERE i.status='posted' AND i.doc_type='invoice' AND i.posted_at>=v_from AND i.posted_at<v_to AND NOT l.is_core_charge
           AND (p_warehouse_id IS NULL OR i.warehouse_id=p_warehouse_id)
         GROUP BY si.oem_part_number, si.description, i.currency
         ORDER BY SUM(l.line_total) DESC LIMIT 5) q), '[]'::jsonb),

  -- Tills opened today: who, float, and how the close counted.
  'tills', COALESCE((SELECT jsonb_agg(jsonb_build_object(
     'id', s.id, 'warehouse', w.name, 'cashier', p.full_name, 'status', s.status, 'currency', s.currency,
     'opening_float', s.opening_float, 'expected_cash', s.expected_cash, 'counted_cash', s.counted_cash, 'variance', s.variance,
     'approved', s.approved_by IS NOT NULL, 'opened_at', s.opened_at, 'closed_at', s.closed_at,
     'variance_reason', s.variance_reason_code, 'approved_by_name', (SELECT ap.full_name FROM public.profiles ap WHERE ap.id=s.approved_by),
     -- Corrections to the drawer during the day: pay-ins, pay-outs, bank drops, cash refunds.
     'movements', COALESCE((SELECT jsonb_agg(jsonb_build_object('kind', m.kind, 'amount', m.amount, 'reason', m.reason_code, 'notes', m.notes,
          'by', (SELECT mp.full_name FROM public.profiles mp WHERE mp.id=m.actor_user_id), 'at', m.created_at) ORDER BY m.created_at)
        FROM public.pos_till_cash_movements m WHERE m.session_id=s.id), '[]'::jsonb)) ORDER BY s.opened_at)
   FROM public.pos_till_sessions s LEFT JOIN public.warehouses w ON w.id=s.warehouse_id LEFT JOIN public.profiles p ON p.id=s.operator_user_id
   WHERE ((s.opened_at>=v_from AND s.opened_at<v_to) OR (s.status<>'closed' AND s.opened_at<v_to))
     AND (p_warehouse_id IS NULL OR s.warehouse_id=p_warehouse_id)), '[]'::jsonb),

  'deliveries', (SELECT jsonb_build_object(
     'dispatched', count(*) FILTER (WHERE dj.dispatched_at>=v_from AND dj.dispatched_at<v_to),
     'completed', count(*) FILTER (WHERE dj.completed_at>=v_from AND dj.completed_at<v_to),
     'failed', count(*) FILTER (WHERE dj.failed_at>=v_from AND dj.failed_at<v_to),
     'on_the_road', count(*) FILTER (WHERE dj.status='dispatched'),
     'waiting_for_driver', count(*) FILTER (WHERE dj.status='pending'))
   FROM public.delivery_jobs dj JOIN public.delivery_notes dn ON dn.id=dj.delivery_note_id
   LEFT JOIN public.sales_invoices i ON i.id=dn.sales_invoice_id
   WHERE p_warehouse_id IS NULL OR i.warehouse_id=p_warehouse_id),

  'cash_on_delivery', COALESCE((SELECT jsonb_agg(jsonb_build_object('currency', c.currency, 'collected', c.amount, 'collections', c.n))
   FROM (SELECT dc.currency, SUM(dc.amount) amount, count(*) n FROM public.delivery_cash_collections dc
         WHERE dc.collected_at>=v_from AND dc.collected_at<v_to
           AND (p_warehouse_id IS NULL OR EXISTS(SELECT 1 FROM public.sales_invoices i WHERE i.id=dc.sales_invoice_id AND i.warehouse_id=p_warehouse_id))
         GROUP BY 1) c), '[]'::jsonb),

  -- Cash still in drivers' hands (all days): should come in at the end of each run.
  'driver_cash_held', COALESCE((SELECT jsonb_agg(jsonb_build_object('currency', d.currency, 'amount', d.amount, 'drivers', d.drivers, 'oldest_at', d.oldest))
   FROM (SELECT dc.currency, SUM(dc.amount) amount, count(DISTINCT dc.collected_by) drivers, min(dc.collected_at) oldest
         FROM public.delivery_cash_collections dc WHERE dc.handin_id IS NULL GROUP BY 1) d), '[]'::jsonb),

  -- What customers owe (all days), and how much of it is more than 30 days old.
  'owed', COALESCE((SELECT jsonb_agg(jsonb_build_object('currency', o.currency, 'amount', o.amount, 'overdue_30', o.overdue, 'customers', o.customers))
   FROM (SELECT i.currency, SUM(i.total - COALESCE(i.amount_paid,0)) amount,
                COALESCE(SUM(i.total - COALESCE(i.amount_paid,0)) FILTER (WHERE i.posted_at < now() - interval '30 days'),0) overdue,
                count(DISTINCT i.customer_id) customers
         FROM public.sales_invoices i
         WHERE i.status='posted' AND i.doc_type='invoice' AND i.customer_id IS NOT NULL AND i.total - COALESCE(i.amount_paid,0) > 0.004
           AND (p_warehouse_id IS NULL OR i.warehouse_id=p_warehouse_id)
         GROUP BY 1) o), '[]'::jsonb),
  'suspended_customers', (SELECT count(*) FROM public.customer_suspensions cs WHERE cs.status='active'),
  'suspended_today', (SELECT count(*) FROM public.customer_suspensions cs WHERE cs.suspended_at>=v_from AND cs.suspended_at<v_to),

  -- Stock to reorder: at or below the reorder point at this branch (or any branch).
  'low_stock', COALESCE((SELECT jsonb_agg(ls ORDER BY (ls->>'on_hand')::numeric)
   FROM (SELECT jsonb_build_object('oem_part_number', si.oem_part_number, 'description', si.description, 'warehouse', w.name,
                 'on_hand', sl.quantity, 'reorder_point', si.reorder_point, 'reorder_qty', si.reorder_qty) ls
         FROM public.stock_levels sl JOIN public.stock_items si ON si.id=sl.stock_item_id JOIN public.warehouses w ON w.id=sl.warehouse_id
         WHERE si.reorder_point IS NOT NULL AND sl.quantity <= si.reorder_point
           AND (p_warehouse_id IS NULL OR sl.warehouse_id=p_warehouse_id)
         ORDER BY sl.quantity LIMIT 10) q), '[]'::jsonb),
  'low_stock_count', (SELECT count(*) FROM public.stock_levels sl JOIN public.stock_items si ON si.id=sl.stock_item_id
     WHERE si.reorder_point IS NOT NULL AND sl.quantity <= si.reorder_point AND (p_warehouse_id IS NULL OR sl.warehouse_id=p_warehouse_id)),
  'backorders_waiting', (SELECT count(*) FROM public.pos_fulfillment_requests f
     WHERE f.kind='backorder' AND f.status IN('requested','ready') AND f.invoice_id IS NULL
       AND (p_warehouse_id IS NULL OR f.destination_warehouse_id=p_warehouse_id)),

  'approvals_waiting', jsonb_array_length(private.approvals_for(auth.uid()))
 );
END $function$;
