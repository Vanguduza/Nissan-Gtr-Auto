-- Restocking suggestions: what to move between branches and what to buy, per branch.
--
-- Per part and branch: sales speed over the last p_sales_days, free stock, stock on its way (open
-- purchase orders, transfers coming in) less transfers going out and back-orders waiting, and the
-- reorder point (the part's own, else daily sales x (supplier lead time + safety days)). At or under
-- the reorder point, top up to the reorder point + p_cover_days of sales: from another branch's spare
-- stock first (what it holds above its own reorder point), the rest bought from the preferred
-- supplier. Also lists slow stock (on hand, nothing sold in 90 days). Quantities are in base units.
-- Who: admin, finance, warehouse, or a POS manager.

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
  -- Every part that is stocked, sold, ordered or back-ordered at a branch.
  SELECT DISTINCT x.stock_item_id, x.warehouse_id FROM (
    SELECT sl.stock_item_id, sl.warehouse_id FROM public.stock_levels sl
    UNION SELECT l.stock_item_id, i.warehouse_id FROM public.sales_invoice_lines l JOIN public.sales_invoices i ON i.id=l.invoice_id
          WHERE i.status='posted' AND i.doc_type='invoice' AND i.posted_at>=v_since
    UNION SELECT f.stock_item_id, f.destination_warehouse_id FROM public.pos_fulfillment_requests f
          WHERE f.kind='backorder' AND f.status='requested'
  ) x JOIN wh ON wh.id=x.warehouse_id
 ), facts AS (
  SELECT p.stock_item_id, p.warehouse_id, si.oem_part_number, si.description, si.base_uom_id, si.reorder_point AS own_rop, si.reorder_qty,
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
   sup.supplier_id, sup.supplier_name, sup.lead_days, sup.unit_cost AS supplier_cost, sup.currency AS supplier_currency,
   (SELECT b.unit_cost FROM public.stock_batches b WHERE b.stock_item_id=p.stock_item_id AND b.unit_cost IS NOT NULL ORDER BY b.received_at DESC NULLS LAST LIMIT 1) AS last_cost,
   (SELECT b.currency::text FROM public.stock_batches b WHERE b.stock_item_id=p.stock_item_id AND b.unit_cost IS NOT NULL ORDER BY b.received_at DESC NULLS LAST LIMIT 1) AS last_cost_currency
  FROM pairs p JOIN public.stock_items si ON si.id=p.stock_item_id
  LEFT JOIN LATERAL (
   SELECT ps.supplier_id, s.name AS supplier_name, ps.typical_lead_days AS lead_days, ps.last_quoted_unit_cost AS unit_cost, ps.currency::text AS currency
   FROM public.supplier_preferred_skus ps JOIN public.suppliers s ON s.id=ps.supplier_id
   WHERE ps.stock_item_id=p.stock_item_id AND ps.is_active AND s.is_active
   ORDER BY s.is_preferred DESC, ps.last_quoted_unit_cost ASC NULLS LAST LIMIT 1) sup ON true
 ), calc AS (
  SELECT f.*,
   round(f.sold / v_days, 3) AS daily,
   COALESCE(f.own_rop, ceil((f.sold / v_days) * (COALESCE(f.lead_days, p_lead_days) + p_safety_days))) AS rop,
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
     'sold', p.sold, 'daily', p.daily, 'days_of_cover', CASE WHEN p.daily > 0 THEN round(GREATEST(p.available,0) / p.daily, 1) END,
     'reorder_point', p.rop, 'reorder_point_source', CASE WHEN p.own_rop IS NOT NULL THEN 'set' ELSE 'from_sales' END,
     'qty_needed', p.qty_needed,
     'transfer_qty', LEAST(p.qty_needed, COALESCE((p.donor->>'spare')::numeric,0)),
     'transfer_from', p.donor,
     'buy_qty', p.qty_needed - LEAST(p.qty_needed, COALESCE((p.donor->>'spare')::numeric,0)),
     'supplier_id', p.supplier_id, 'supplier', p.supplier_name, 'lead_days', COALESCE(p.lead_days, p_lead_days),
     'unit_cost', COALESCE(p.supplier_cost, p.last_cost), 'cost_currency', COALESCE(p.supplier_currency, p.last_cost_currency),
     'urgency', CASE WHEN p.available <= 0 OR p.backordered > 0 THEN 'out'
                     WHEN p.daily > 0 AND p.available / p.daily < COALESCE(p.lead_days, p_lead_days) THEN 'before_delivery'
                     ELSE 'low' END)
     ORDER BY CASE WHEN p.available <= 0 OR p.backordered > 0 THEN 0 ELSE 1 END,
              CASE WHEN p.daily > 0 THEN p.available / p.daily ELSE 999999 END, p.oem_part_number)
   FROM plan p WHERE p.qty_needed > 0 AND (p_warehouse_id IS NULL OR p.warehouse_id=p_warehouse_id)), '[]'::jsonb),
  'slow_stock', COALESCE((SELECT jsonb_agg(x ORDER BY (x->>'value')::numeric DESC NULLS LAST) FROM (
     SELECT jsonb_build_object('stock_item_id', p.stock_item_id, 'oem_part_number', p.oem_part_number, 'description', p.description,
       'warehouse', (SELECT w.name FROM wh w WHERE w.id=p.warehouse_id), 'on_hand', p.on_hand,
       'last_sold_at', p.last_sold_at, 'value', round(p.on_hand * COALESCE(p.last_cost,0),2), 'currency', p.last_cost_currency) x
     FROM plan p
     WHERE p.on_hand > 0 AND (p.last_sold_at IS NULL OR p.last_sold_at < now() - interval '90 days')
       AND (p_warehouse_id IS NULL OR p.warehouse_id=p_warehouse_id)
     ORDER BY p.on_hand * COALESCE(p.last_cost,0) DESC LIMIT 20) q), '[]'::jsonb)
 ) INTO v_out;
 RETURN v_out;
END $f$;
REVOKE ALL ON FUNCTION public.get_restock_suggestions(uuid, integer, integer, integer, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_restock_suggestions(uuid, integer, integer, integer, integer) TO authenticated;
