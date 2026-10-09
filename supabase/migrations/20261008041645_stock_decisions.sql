-- Better stock decisions, all in get_restock_suggestions (Staff → Restock):
-- 1. Reorder points per branch (stock_reorder_points) override the part's own; set from the page.
-- 2. Lost demand: the counter records a customer who wanted a part the branch did not have
--    (record_lost_demand); it counts as sales speed, so the part is restocked even though it never sold.
-- 3. Supplier lead time actually seen: median days from purchase order submitted to goods received
--    over the last year (this part, else any part from that supplier), before the quoted lead time.
-- 4. Slow stock (nothing sold here in 90 days) says what to do: move it to the branch that sells it
--    (how many), or mark it down (10/20/30% by age), applied with markdown_stock_item (logged in
--    price_changes; every price list for the part).

CREATE TABLE public.stock_reorder_points (
  stock_item_id uuid NOT NULL REFERENCES public.stock_items(id) ON DELETE CASCADE,
  warehouse_id uuid NOT NULL REFERENCES public.warehouses(id) ON DELETE CASCADE,
  reorder_point numeric(18,3) NOT NULL CHECK (reorder_point >= 0),
  reorder_qty numeric(18,3) CHECK (reorder_qty IS NULL OR reorder_qty > 0),
  updated_by uuid REFERENCES auth.users(id),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (stock_item_id, warehouse_id)
);
ALTER TABLE public.stock_reorder_points ENABLE ROW LEVEL SECURITY;
CREATE POLICY stock_reorder_points_staff_read ON public.stock_reorder_points FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin','finance','warehouse','sales']::public.staff_role[]) OR public.is_pos_approver());
REVOKE ALL ON TABLE public.stock_reorder_points FROM anon, authenticated;
GRANT SELECT ON TABLE public.stock_reorder_points TO authenticated;

CREATE TABLE public.lost_demand (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  stock_item_id uuid NOT NULL REFERENCES public.stock_items(id) ON DELETE CASCADE,
  warehouse_id uuid NOT NULL REFERENCES public.warehouses(id),
  qty numeric(18,3) NOT NULL CHECK (qty > 0 AND qty <= 1000),
  note text,
  created_by uuid NOT NULL REFERENCES auth.users(id),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX lost_demand_item_wh_idx ON public.lost_demand(stock_item_id, warehouse_id, created_at DESC);
ALTER TABLE public.lost_demand ENABLE ROW LEVEL SECURITY;
CREATE POLICY lost_demand_staff_read ON public.lost_demand FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin','finance','warehouse','sales']::public.staff_role[]) OR public.is_pos_approver());
REVOKE ALL ON TABLE public.lost_demand FROM anon, authenticated;
GRANT SELECT ON TABLE public.lost_demand TO authenticated;

CREATE TABLE public.price_changes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  price_list_item_id uuid NOT NULL REFERENCES public.price_list_items(id) ON DELETE CASCADE,
  stock_item_id uuid NOT NULL REFERENCES public.stock_items(id) ON DELETE CASCADE,
  old_price numeric(18,4) NOT NULL,
  new_price numeric(18,4) NOT NULL,
  reason text NOT NULL,
  changed_by uuid NOT NULL REFERENCES auth.users(id),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX price_changes_item_idx ON public.price_changes(stock_item_id, created_at DESC);
ALTER TABLE public.price_changes ENABLE ROW LEVEL SECURITY;
CREATE POLICY price_changes_staff_read ON public.price_changes FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin','finance','sales']::public.staff_role[]) OR public.is_pos_approver());
REVOKE ALL ON TABLE public.price_changes FROM anon, authenticated;
GRANT SELECT ON TABLE public.price_changes TO authenticated;

-- Median days from order submitted to goods received (submitted GRNs, last 365 days):
-- this part from this supplier, else anything from this supplier. NULL when never received.
CREATE OR REPLACE FUNCTION private.supplier_lead_days(p_supplier_id uuid, p_stock_item_id uuid)
 RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
 WITH r AS (
  SELECT DISTINCT gr.id, extract(epoch FROM gr.submitted_at - po.submitted_at) / 86400.0 AS days,
         EXISTS (SELECT 1 FROM public.goods_receipt_lines l WHERE l.goods_receipt_id=gr.id AND l.stock_item_id=p_stock_item_id) AS this_part
    FROM public.goods_receipts gr JOIN public.purchase_orders po ON po.id=gr.purchase_order_id
   WHERE gr.supplier_id=p_supplier_id AND gr.status IN ('submitted','approved') AND gr.submitted_at IS NOT NULL
     AND po.submitted_at IS NOT NULL AND gr.submitted_at >= po.submitted_at AND gr.submitted_at > now() - interval '365 days')
 SELECT CASE
  WHEN EXISTS (SELECT 1 FROM r WHERE this_part) THEN
   (SELECT jsonb_build_object('days', round(percentile_cont(0.5) WITHIN GROUP (ORDER BY days)), 'samples', count(*), 'scope', 'part') FROM r WHERE this_part)
  WHEN EXISTS (SELECT 1 FROM r) THEN
   (SELECT jsonb_build_object('days', round(percentile_cont(0.5) WITHIN GROUP (ORDER BY days)), 'samples', count(*), 'scope', 'supplier') FROM r)
 END
$f$;
REVOKE ALL ON FUNCTION private.supplier_lead_days(uuid, uuid) FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.set_branch_reorder_point(p_stock_item_id uuid, p_warehouse_id uuid, p_reorder_point numeric, p_reorder_qty numeric DEFAULT NULL)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin','finance','warehouse']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'warehouse, finance, manager or admin required';
 END IF;
 IF p_reorder_point IS NULL THEN
  DELETE FROM public.stock_reorder_points WHERE stock_item_id=p_stock_item_id AND warehouse_id=p_warehouse_id;
  RETURN jsonb_build_object('stock_item_id', p_stock_item_id, 'warehouse_id', p_warehouse_id, 'reorder_point', NULL);
 END IF;
 IF p_reorder_point < 0 OR (p_reorder_qty IS NOT NULL AND p_reorder_qty <= 0) THEN RAISE EXCEPTION 'reorder point must be 0 or more, reorder quantity more than 0'; END IF;
 INSERT INTO public.stock_reorder_points(stock_item_id, warehouse_id, reorder_point, reorder_qty, updated_by, updated_at)
 VALUES (p_stock_item_id, p_warehouse_id, p_reorder_point, p_reorder_qty, auth.uid(), now())
 ON CONFLICT (stock_item_id, warehouse_id) DO UPDATE SET reorder_point=EXCLUDED.reorder_point, reorder_qty=EXCLUDED.reorder_qty, updated_by=auth.uid(), updated_at=now();
 RETURN jsonb_build_object('stock_item_id', p_stock_item_id, 'warehouse_id', p_warehouse_id, 'reorder_point', p_reorder_point, 'reorder_qty', p_reorder_qty);
END $f$;

-- Counter: a customer wanted [p_qty] of a part this branch did not have.
CREATE OR REPLACE FUNCTION public.record_lost_demand(p_stock_item_id uuid, p_warehouse_id uuid, p_qty numeric, p_note text DEFAULT NULL)
 RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE v_id uuid;
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin','sales','warehouse']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'counter staff required';
 END IF;
 IF COALESCE(p_qty,0) <= 0 OR p_qty > 1000 THEN RAISE EXCEPTION 'quantity must be between 1 and 1000'; END IF;
 IF NOT EXISTS (SELECT 1 FROM public.warehouses w WHERE w.id=p_warehouse_id AND w.is_active) THEN RAISE EXCEPTION 'branch not found'; END IF;
 INSERT INTO public.lost_demand(stock_item_id, warehouse_id, qty, note, created_by)
 VALUES (p_stock_item_id, p_warehouse_id, p_qty, NULLIF(trim(COALESCE(p_note,'')),''), auth.uid()) RETURNING id INTO v_id;
 RETURN v_id;
END $f$;

-- Manager / finance / admin: lower every price of a part by [p_percent] (1-50), logged.
CREATE OR REPLACE FUNCTION public.markdown_stock_item(p_stock_item_id uuid, p_percent numeric, p_reason text)
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE r record; v_out jsonb := '[]'::jsonb; v_new numeric;
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin','finance']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'manager, finance or admin required';
 END IF;
 IF COALESCE(p_percent,0) < 1 OR p_percent > 50 THEN RAISE EXCEPTION 'markdown must be between 1%% and 50%%'; END IF;
 IF trim(COALESCE(p_reason,'')) = '' THEN RAISE EXCEPTION 'reason required'; END IF;
 FOR r IN SELECT pli.id, pli.unit_price, pl.code FROM public.price_list_items pli JOIN public.price_lists pl ON pl.id=pli.price_list_id
          WHERE pli.stock_item_id=p_stock_item_id FOR UPDATE OF pli LOOP
  v_new := round(r.unit_price * (100 - p_percent) / 100, 2);
  UPDATE public.price_list_items SET unit_price=v_new WHERE id=r.id;
  INSERT INTO public.price_changes(price_list_item_id, stock_item_id, old_price, new_price, reason, changed_by)
  VALUES (r.id, p_stock_item_id, r.unit_price, v_new, format('Markdown %s%%: %s', p_percent, trim(p_reason)), auth.uid());
  v_out := v_out || jsonb_build_object('price_list', r.code, 'old', r.unit_price, 'new', v_new);
 END LOOP;
 IF jsonb_array_length(v_out) = 0 THEN RAISE EXCEPTION 'this part has no prices to mark down'; END IF;
 RETURN v_out;
END $f$;

REVOKE ALL ON FUNCTION public.set_branch_reorder_point(uuid, uuid, numeric, numeric) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.record_lost_demand(uuid, uuid, numeric, text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.markdown_stock_item(uuid, numeric, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_branch_reorder_point(uuid, uuid, numeric, numeric) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.record_lost_demand(uuid, uuid, numeric, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.markdown_stock_item(uuid, numeric, text) TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.get_restock_suggestions(
  p_warehouse_id uuid DEFAULT NULL,
  p_sales_days integer DEFAULT 28,
  p_lead_days integer DEFAULT 14,
  p_safety_days integer DEFAULT 7,
  p_cover_days integer DEFAULT 28)
 RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path TO '' AS $f$
DECLARE
 v_days integer := LEAST(GREATEST(COALESCE(p_sales_days,28),7),365);
 v_since timestamptz := now() - make_interval(days => LEAST(GREATEST(COALESCE(p_sales_days,28),7),365));
 v_out jsonb;
BEGIN
 IF NOT (public.has_staff_role(ARRAY['admin','finance','warehouse']::public.staff_role[]) OR public.is_pos_approver()) THEN
  RAISE EXCEPTION 'warehouse, finance, manager or admin required';
 END IF;

 WITH wh AS (
  SELECT w.id, w.name FROM public.warehouses w WHERE w.is_active AND NOT COALESCE(w.is_quarantine,false)
 ), pairs AS (
  -- Every part that is stocked, sold, back-ordered or asked for (lost demand) at a branch.
  SELECT DISTINCT x.stock_item_id, x.warehouse_id FROM (
    SELECT sl.stock_item_id, sl.warehouse_id FROM public.stock_levels sl
    UNION SELECT l.stock_item_id, i.warehouse_id FROM public.sales_invoice_lines l JOIN public.sales_invoices i ON i.id=l.invoice_id
          WHERE i.status='posted' AND i.doc_type='invoice' AND i.posted_at>=v_since
    UNION SELECT f.stock_item_id, f.destination_warehouse_id FROM public.pos_fulfillment_requests f
          WHERE f.kind='backorder' AND f.status='requested'
    UNION SELECT ld.stock_item_id, ld.warehouse_id FROM public.lost_demand ld WHERE ld.created_at>=v_since
  ) x JOIN wh ON wh.id=x.warehouse_id
 ), facts AS (
  SELECT p.stock_item_id, p.warehouse_id, si.oem_part_number, si.description, si.base_uom_id,
   COALESCE(brp.reorder_point, si.reorder_point) AS own_rop, COALESCE(brp.reorder_qty, si.reorder_qty) AS reorder_qty,
   CASE WHEN brp.reorder_point IS NOT NULL THEN 'branch' WHEN si.reorder_point IS NOT NULL THEN 'set' END AS rop_source,
   COALESCE((SELECT SUM(ld.qty) FROM public.lost_demand ld WHERE ld.stock_item_id=p.stock_item_id AND ld.warehouse_id=p.warehouse_id AND ld.created_at>=v_since),0) AS lost,
   COALESCE((SELECT sl.quantity FROM public.stock_levels sl WHERE sl.stock_item_id=p.stock_item_id AND sl.warehouse_id=p.warehouse_id),0) AS on_hand,
   public.get_inventory_available_quantity(p.stock_item_id, p.warehouse_id) AS available,
   COALESCE((SELECT SUM(l.qty_base) FROM public.sales_invoice_lines l JOIN public.sales_invoices i ON i.id=l.invoice_id
             WHERE l.stock_item_id=p.stock_item_id AND i.warehouse_id=p.warehouse_id AND i.status='posted' AND i.doc_type='invoice'
               AND i.posted_at>=v_since AND NOT l.is_core_charge),0) AS sold,
   (SELECT max(i.posted_at) FROM public.sales_invoice_lines l JOIN public.sales_invoices i ON i.id=l.invoice_id
     WHERE l.stock_item_id=p.stock_item_id AND i.warehouse_id=p.warehouse_id AND i.status='posted' AND i.doc_type='invoice') AS last_sold_at,
   COALESCE((SELECT SUM(public.convert_to_base_uom(pl.stock_item_id, pl.uom_id, GREATEST(pl.qty_ordered-COALESCE(pl.qty_received,0),0)))
             FROM public.purchase_order_lines pl JOIN public.purchase_orders po ON po.id=pl.purchase_order_id
             WHERE pl.stock_item_id=p.stock_item_id AND po.warehouse_id=p.warehouse_id AND po.status IN('submitted','approved')),0) AS on_order,
   -- Draft orders are not on their way yet; shown so nobody drafts the same order twice.
   COALESCE((SELECT SUM(public.convert_to_base_uom(pl.stock_item_id, pl.uom_id, pl.qty_ordered))
             FROM public.purchase_order_lines pl JOIN public.purchase_orders po ON po.id=pl.purchase_order_id
             WHERE pl.stock_item_id=p.stock_item_id AND po.warehouse_id=p.warehouse_id AND po.status='draft'),0) AS in_draft,
   COALESCE((SELECT SUM(el.qty_base) FROM public.stock_entry_lines el JOIN public.stock_entries e ON e.id=el.stock_entry_id
             WHERE el.stock_item_id=p.stock_item_id AND e.entry_type='transfer' AND e.status='pending_approval' AND e.to_warehouse_id=p.warehouse_id),0) AS transfer_in,
   COALESCE((SELECT SUM(el.qty_base) FROM public.stock_entry_lines el JOIN public.stock_entries e ON e.id=el.stock_entry_id
             WHERE el.stock_item_id=p.stock_item_id AND e.entry_type='transfer' AND e.status='pending_approval' AND e.from_warehouse_id=p.warehouse_id),0) AS transfer_out,
   COALESCE((SELECT SUM(public.convert_to_base_uom(f.stock_item_id, f.uom_id, f.qty)) FROM public.pos_fulfillment_requests f
             WHERE f.stock_item_id=p.stock_item_id AND f.destination_warehouse_id=p.warehouse_id AND f.kind='backorder' AND f.status='requested'),0) AS backordered,
   sup.supplier_id, sup.supplier_name, sup.unit_cost AS supplier_cost, sup.currency AS supplier_currency,
   -- Lead time actually seen (order submitted -> goods received) beats the quoted one.
   COALESCE((sup.actual->>'days')::numeric, sup.lead_days) AS lead_days,
   CASE WHEN sup.actual IS NOT NULL THEN 'received' WHEN sup.lead_days IS NOT NULL THEN 'quoted' END AS lead_source,
   (sup.actual->>'samples')::int AS lead_samples,
   (SELECT b.unit_cost FROM public.stock_batches b WHERE b.stock_item_id=p.stock_item_id AND b.unit_cost IS NOT NULL ORDER BY b.received_at DESC NULLS LAST LIMIT 1) AS last_cost,
   (SELECT b.currency::text FROM public.stock_batches b WHERE b.stock_item_id=p.stock_item_id AND b.unit_cost IS NOT NULL ORDER BY b.received_at DESC NULLS LAST LIMIT 1) AS last_cost_currency
  FROM pairs p JOIN public.stock_items si ON si.id=p.stock_item_id
  LEFT JOIN public.stock_reorder_points brp ON brp.stock_item_id=p.stock_item_id AND brp.warehouse_id=p.warehouse_id
  LEFT JOIN LATERAL (
   SELECT ps.supplier_id, s.name AS supplier_name, ps.typical_lead_days AS lead_days, ps.last_quoted_unit_cost AS unit_cost, ps.currency::text AS currency,
          private.supplier_lead_days(ps.supplier_id, p.stock_item_id) AS actual
   FROM public.supplier_preferred_skus ps JOIN public.suppliers s ON s.id=ps.supplier_id
   WHERE ps.stock_item_id=p.stock_item_id AND ps.is_active AND s.is_active
   ORDER BY s.is_preferred DESC, ps.last_quoted_unit_cost ASC NULLS LAST LIMIT 1) sup ON true
 ), calc AS (
  SELECT f.*,
   round((f.sold + f.lost) / v_days, 3) AS daily,
   COALESCE(f.own_rop, ceil(((f.sold + f.lost) / v_days) * (COALESCE(f.lead_days, p_lead_days) + p_safety_days))) AS rop,
   f.available + f.on_order + f.transfer_in - f.transfer_out - f.backordered AS position
  FROM facts f
 ), need AS (
  SELECT c.*,
   CASE WHEN (c.daily > 0 OR c.own_rop IS NOT NULL OR c.backordered > 0) AND c.position <= c.rop
        THEN GREATEST(1, ceil(c.rop + GREATEST(COALESCE(c.reorder_qty,0), c.daily * p_cover_days) - c.position)) ELSE 0 END AS qty_needed,
   -- What this branch could give away: free stock above its own reorder point and next cover period.
   GREATEST(0, floor(c.available - c.transfer_out - c.rop - c.daily * p_cover_days)) AS spare
  FROM calc c
 ), plan AS (
  SELECT n.*,
   (SELECT jsonb_build_object('warehouse_id', o.warehouse_id, 'warehouse', w.name, 'spare', o.spare)
      FROM need o JOIN wh w ON w.id=o.warehouse_id
     WHERE o.stock_item_id=n.stock_item_id AND o.warehouse_id<>n.warehouse_id AND o.spare>0
     ORDER BY o.spare DESC LIMIT 1) AS donor
  FROM need n
 )
 SELECT jsonb_build_object(
  'generated_at', now(),
  'settings', jsonb_build_object('sales_days', v_days, 'lead_days', p_lead_days, 'safety_days', p_safety_days, 'cover_days', p_cover_days),
  'suggestions', COALESCE((SELECT jsonb_agg(jsonb_build_object(
     'stock_item_id', p.stock_item_id, 'uom_id', p.base_uom_id, 'oem_part_number', p.oem_part_number, 'description', p.description,
     'warehouse_id', p.warehouse_id, 'warehouse', (SELECT w.name FROM wh w WHERE w.id=p.warehouse_id),
     'on_hand', p.on_hand, 'available', p.available, 'on_order', p.on_order, 'in_draft', p.in_draft, 'transfer_in', p.transfer_in, 'backordered', p.backordered,
     'sold', p.sold, 'lost', p.lost, 'daily', p.daily, 'days_of_cover', CASE WHEN p.daily > 0 THEN round(GREATEST(p.available,0) / p.daily, 1) END,
     'reorder_point', p.rop, 'reorder_point_source', COALESCE(p.rop_source, 'from_sales'), 'reorder_qty', p.reorder_qty,
     'qty_needed', p.qty_needed,
     'transfer_qty', LEAST(p.qty_needed, COALESCE((p.donor->>'spare')::numeric,0)),
     'transfer_from', p.donor,
     'buy_qty', p.qty_needed - LEAST(p.qty_needed, COALESCE((p.donor->>'spare')::numeric,0)),
     'supplier_id', p.supplier_id, 'supplier', p.supplier_name, 'lead_days', COALESCE(p.lead_days, p_lead_days), 'lead_source', COALESCE(p.lead_source, 'default'), 'lead_samples', p.lead_samples,
     'unit_cost', COALESCE(p.supplier_cost, p.last_cost), 'cost_currency', COALESCE(p.supplier_currency, p.last_cost_currency),
     'urgency', CASE WHEN p.available <= 0 OR p.backordered > 0 THEN 'out'
                     WHEN p.daily > 0 AND p.available / p.daily < COALESCE(p.lead_days, p_lead_days) THEN 'before_delivery'
                     ELSE 'low' END)
     ORDER BY CASE WHEN p.available <= 0 OR p.backordered > 0 THEN 0 ELSE 1 END,
              CASE WHEN p.daily > 0 THEN p.available / p.daily ELSE 999999 END, p.oem_part_number)
   FROM plan p WHERE p.qty_needed > 0 AND (p_warehouse_id IS NULL OR p.warehouse_id=p_warehouse_id)), '[]'::jsonb),
  'slow_stock', COALESCE((SELECT jsonb_agg(x ORDER BY (x->>'value')::numeric DESC NULLS LAST) FROM (
     SELECT jsonb_build_object('stock_item_id', p.stock_item_id, 'uom_id', p.base_uom_id, 'oem_part_number', p.oem_part_number, 'description', p.description,
       'warehouse_id', p.warehouse_id, 'warehouse', (SELECT w.name FROM wh w WHERE w.id=p.warehouse_id), 'on_hand', p.on_hand, 'available', p.available,
       'last_sold_at', p.last_sold_at, 'value', round(p.on_hand * COALESCE(p.last_cost,0),2), 'currency', p.last_cost_currency,
       -- Another branch sold it recently: move what it would sell over the cover period there.
       'move_to', mv.dest, 'move_qty', CASE WHEN mv.dest IS NOT NULL THEN LEAST(floor(GREATEST(p.available,0)), GREATEST(1, ceil((mv.dest->>'daily')::numeric * p_cover_days))) END,
       -- Nobody sells it: mark it down, more the longer it has sat.
       'markdown_pct', CASE WHEN mv.dest IS NOT NULL THEN NULL
                            WHEN p.last_sold_at IS NULL OR p.last_sold_at < now() - interval '365 days' THEN 30
                            WHEN p.last_sold_at < now() - interval '180 days' THEN 20 ELSE 10 END) x
     FROM plan p
     LEFT JOIN LATERAL (
      SELECT jsonb_build_object('warehouse_id', o.warehouse_id, 'warehouse', w.name, 'sold', o.sold, 'daily', o.daily) AS dest
        FROM plan o JOIN wh w ON w.id=o.warehouse_id
       WHERE o.stock_item_id=p.stock_item_id AND o.warehouse_id<>p.warehouse_id AND o.daily > 0
       ORDER BY o.daily DESC LIMIT 1) mv ON true
     WHERE p.on_hand > 0 AND (p.last_sold_at IS NULL OR p.last_sold_at < now() - interval '90 days')
       AND (p_warehouse_id IS NULL OR p.warehouse_id=p_warehouse_id)
     ORDER BY p.on_hand * COALESCE(p.last_cost,0) DESC LIMIT 20) q), '[]'::jsonb)
 ) INTO v_out;
 RETURN v_out;
END $f$;
REVOKE ALL ON FUNCTION public.get_restock_suggestions(uuid, integer, integer, integer, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_restock_suggestions(uuid, integer, integer, integer, integer) TO authenticated;
