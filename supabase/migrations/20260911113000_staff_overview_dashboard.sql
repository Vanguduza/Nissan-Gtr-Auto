-- Staff Overview dashboard: one role-aware transactional snapshot + global search.
-- The dashboard never seeds demo values; empty production data returns real zero/empty states.

CREATE INDEX IF NOT EXISTS payment_entries_status_posted_at_idx
  ON public.payment_entries (status, posted_at DESC)
  WHERE posted_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS customers_created_at_idx
  ON public.customers (created_at DESC);

CREATE OR REPLACE FUNCTION public.get_staff_overview_dashboard(
  p_from TIMESTAMPTZ DEFAULT (now() - INTERVAL '30 days'),
  p_to TIMESTAMPTZ DEFAULT now()
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_roles TEXT[] := '{}'::text[];
  v_name TEXT;
  v_span INTERVAL;
  v_prev_from TIMESTAMPTZ;
  v_prev_to TIMESTAMPTZ;
  v_can_commercial BOOLEAN := false;
  v_can_finance BOOLEAN := false;
  v_can_customers BOOLEAN := false;
  v_can_inventory BOOLEAN := false;
  v_can_logistics BOOLEAN := false;
  v_ops JSONB := '{}'::jsonb;
  v_prev_ops JSONB := '{}'::jsonb;
  v_finance JSONB := '{}'::jsonb;
  v_prev_finance JSONB := '{}'::jsonb;
  v_customer_count BIGINT := 0;
  v_prev_customer_count BIGINT := 0;
  v_new_customers BIGINT := 0;
  v_trend JSONB := '[]'::jsonb;
  v_payment_mix JSONB := '[]'::jsonb;
  v_top_products JSONB := '[]'::jsonb;
  v_customer_segments JSONB := '[]'::jsonb;
  v_recent_orders JSONB := '[]'::jsonb;
  v_inventory JSONB := '{}'::jsonb;
  v_delivery JSONB := '{}'::jsonb;
BEGIN
  IF auth.role() <> 'service_role' AND NOT public.is_staff() THEN
    RAISE EXCEPTION 'staff access required';
  END IF;
  IF p_from IS NULL OR p_to IS NULL OR p_to <= p_from THEN
    RAISE EXCEPTION 'valid p_from / p_to required';
  END IF;
  IF p_to - p_from > INTERVAL '366 days' THEN
    RAISE EXCEPTION 'dashboard range cannot exceed 366 days';
  END IF;

  v_span := p_to - p_from;
  v_prev_to := p_from;
  v_prev_from := p_from - v_span;

  IF auth.role() = 'service_role' THEN
    v_roles := ARRAY['admin']::text[];
    v_name := 'Service';
  ELSE
    SELECT COALESCE(array_agg(sr.role::text ORDER BY sr.role::text), '{}'::text[])
      INTO v_roles
    FROM public.staff_roles sr
    WHERE sr.user_id = auth.uid();

    SELECT p.full_name INTO v_name
    FROM public.profiles p
    WHERE p.id = auth.uid();
  END IF;

  v_can_commercial := v_roles && ARRAY['admin','finance','sales']::text[];
  v_can_finance := v_roles && ARRAY['admin','finance','sales']::text[];
  v_can_customers := v_roles && ARRAY['admin','finance','sales']::text[];
  v_can_inventory := v_roles && ARRAY['admin','warehouse','finance','sales']::text[];
  v_can_logistics := v_roles && ARRAY['admin','warehouse','sales','dispatcher']::text[];

  IF v_can_commercial THEN
    v_ops := public.kpi_ops_sales_v1(p_from, p_to, 5);
    v_prev_ops := public.kpi_ops_sales_v1(v_prev_from, v_prev_to, 5);

    SELECT COALESCE(jsonb_agg(jsonb_build_object(
      'date', d.day::text,
      'revenue_usd', COALESCE(s.revenue_usd, 0),
      'orders', COALESCE(s.orders, 0)
    ) ORDER BY d.day), '[]'::jsonb)
    INTO v_trend
    FROM generate_series(
      (p_from AT TIME ZONE 'Africa/Harare')::date,
      ((p_to - INTERVAL '1 microsecond') AT TIME ZONE 'Africa/Harare')::date,
      INTERVAL '1 day'
    ) AS d(day)
    LEFT JOIN (
      SELECT
        (si.posted_at AT TIME ZONE 'Africa/Harare')::date AS day,
        COALESCE(SUM(si.total / NULLIF(COALESCE(si.exchange_rate_applied, 1), 0)), 0)::numeric AS revenue_usd,
        COUNT(*)::bigint AS orders
      FROM public.sales_invoices si
      WHERE si.doc_type = 'invoice'
        AND si.status = 'posted'
        AND si.posted_at >= p_from
        AND si.posted_at < p_to
      GROUP BY 1
    ) s ON s.day = d.day::date;

    SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.amount_usd DESC), '[]'::jsonb)
    INTO v_payment_mix
    FROM (
      SELECT
        pe.tender::text AS tender,
        COUNT(*)::bigint AS payments,
        COALESCE(SUM(pe.amount / NULLIF(COALESCE(pe.exchange_rate_applied, 1), 0)), 0)::numeric AS amount_usd
      FROM public.payment_entries pe
      WHERE pe.status = 'posted'
        AND pe.posted_at >= p_from
        AND pe.posted_at < p_to
      GROUP BY pe.tender
    ) t;

    SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.revenue_usd DESC, t.qty DESC), '[]'::jsonb)
    INTO v_top_products
    FROM (
      SELECT
        st.id AS stock_item_id,
        st.oem_part_number AS oem,
        st.description,
        COALESCE(SUM(sil.qty_base), 0)::numeric AS qty,
        COALESCE(SUM(sil.line_total / NULLIF(COALESCE(inv.exchange_rate_applied, 1), 0)), 0)::numeric AS revenue_usd,
        (
          SELECT sii.storage_path
          FROM public.stock_item_images sii
          WHERE sii.stock_item_id = st.id
          ORDER BY sii.is_primary DESC, sii.sort_order, sii.created_at
          LIMIT 1
        ) AS image_path
      FROM public.sales_invoice_lines sil
      JOIN public.sales_invoices inv ON inv.id = sil.invoice_id
      JOIN public.stock_items st ON st.id = sil.stock_item_id
      WHERE inv.doc_type = 'invoice'
        AND inv.status = 'posted'
        AND inv.posted_at >= p_from
        AND inv.posted_at < p_to
        AND NOT sil.is_core_charge
      GROUP BY st.id, st.oem_part_number, st.description
      ORDER BY revenue_usd DESC, qty DESC
      LIMIT 5
    ) t;

    SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.posted_at DESC), '[]'::jsonb)
    INTO v_recent_orders
    FROM (
      SELECT
        si.id,
        si.document_number,
        COALESCE(NULLIF(si.customer_business_name, ''), NULLIF(si.customer_display_name, ''), 'Walk-in') AS customer,
        si.total,
        si.currency::text AS currency,
        si.amount_paid,
        si.fulfillment_mode::text AS fulfillment_mode,
        si.delivery_payment_method::text AS delivery_payment_method,
        COALESCE(co.state::text, CASE WHEN si.amount_paid >= si.total THEN 'paid' ELSE 'balance_due' END) AS state,
        si.posted_at
      FROM public.sales_invoices si
      LEFT JOIN public.commerce_orders co ON co.sales_invoice_id = si.id
      WHERE si.doc_type = 'invoice'
        AND si.status = 'posted'
        AND si.posted_at >= p_from
        AND si.posted_at < p_to
      ORDER BY si.posted_at DESC
      LIMIT 5
    ) t;
  END IF;

  IF v_can_finance THEN
    v_finance := public.kpi_finance_performance_v1(p_from, p_to, 5);
    v_prev_finance := public.kpi_finance_performance_v1(v_prev_from, v_prev_to, 5);
  END IF;

  IF v_can_customers THEN
    SELECT COUNT(*)::bigint INTO v_customer_count
    FROM public.customers c
    WHERE c.created_at < p_to;

    SELECT COUNT(*)::bigint INTO v_prev_customer_count
    FROM public.customers c
    WHERE c.created_at < p_from;

    SELECT COUNT(*)::bigint INTO v_new_customers
    FROM public.customers c
    WHERE c.created_at >= p_from AND c.created_at < p_to;

    SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.customer_count DESC), '[]'::jsonb)
    INTO v_customer_segments
    FROM (
      SELECT COALESCE(NULLIF(c.customer_kind, ''), 'unspecified') AS segment,
             COUNT(*)::bigint AS customer_count
      FROM public.customers c
      WHERE c.created_at < p_to
      GROUP BY 1
    ) t;
  END IF;

  IF v_can_inventory THEN
    WITH sku_inventory AS (
      SELECT
        si.id,
        si.reorder_point,
        COALESCE(SUM(sl.quantity) FILTER (WHERE w.is_active AND NOT w.is_quarantine), 0)::numeric AS saleable_qty,
        COALESCE(SUM(sl.quantity) FILTER (WHERE w.is_active AND w.is_quarantine), 0)::numeric AS quarantine_qty
      FROM public.stock_items si
      LEFT JOIN public.stock_levels sl ON sl.stock_item_id = si.id
      LEFT JOIN public.warehouses w ON w.id = sl.warehouse_id
      GROUP BY si.id, si.reorder_point
    )
    SELECT jsonb_build_object(
      'total_skus', COUNT(*)::bigint,
      'out_of_stock', COUNT(*) FILTER (WHERE saleable_qty <= 0)::bigint,
      'low_stock', COUNT(*) FILTER (
        WHERE saleable_qty > 0 AND reorder_point IS NOT NULL AND saleable_qty <= reorder_point
      )::bigint,
      'in_stock', COUNT(*) FILTER (WHERE saleable_qty > 0)::bigint,
      'quarantine', COUNT(*) FILTER (WHERE quarantine_qty > 0)::bigint,
      'on_hand_qty', COALESCE(SUM(saleable_qty), 0)::numeric,
      'quarantine_qty', COALESCE(SUM(quarantine_qty), 0)::numeric
    ) INTO v_inventory
    FROM sku_inventory;
  END IF;

  IF v_can_logistics THEN
    SELECT jsonb_build_object(
      'open_jobs', COUNT(*) FILTER (WHERE dj.status IN ('pending','dispatched'))::bigint,
      'pending', COUNT(*) FILTER (WHERE dj.status = 'pending')::bigint,
      'dispatched', COUNT(*) FILTER (WHERE dj.status = 'dispatched')::bigint,
      'completed', COUNT(*) FILTER (
        WHERE dj.status = 'completed' AND dj.completed_at >= p_from AND dj.completed_at < p_to
      )::bigint,
      'failed', COUNT(*) FILTER (
        WHERE dj.status = 'failed' AND dj.failed_at >= p_from AND dj.failed_at < p_to
      )::bigint
    ) INTO v_delivery
    FROM public.delivery_jobs dj;
  END IF;

  RETURN jsonb_build_object(
    'generated_at', now(),
    'timezone', 'Africa/Harare',
    'period', jsonb_build_object(
      'from', p_from, 'to', p_to, 'previous_from', v_prev_from, 'previous_to', v_prev_to
    ),
    'staff', jsonb_build_object('name', v_name, 'roles', to_jsonb(v_roles)),
    'permissions', jsonb_build_object(
      'commercial', v_can_commercial,
      'finance', v_can_finance,
      'customers', v_can_customers,
      'inventory', v_can_inventory,
      'logistics', v_can_logistics
    ),
    'summary', jsonb_build_object(
      'sales', CASE WHEN v_can_commercial THEN v_ops->'sales' ELSE NULL END,
      'previous_sales', CASE WHEN v_can_commercial THEN v_prev_ops->'sales' ELSE NULL END,
      'customers', CASE WHEN v_can_customers THEN v_customer_count ELSE NULL END,
      'previous_customers', CASE WHEN v_can_customers THEN v_prev_customer_count ELSE NULL END,
      'new_customers', CASE WHEN v_can_customers THEN v_new_customers ELSE NULL END,
      'net_margin_pct', CASE WHEN v_can_finance THEN v_finance->'net_margin_pct' ELSE NULL END,
      'previous_net_margin_pct', CASE WHEN v_can_finance THEN v_prev_finance->'net_margin_pct' ELSE NULL END
    ),
    'sales_trend', v_trend,
    'payment_mix', v_payment_mix,
    'top_products', v_top_products,
    'customer_segments', v_customer_segments,
    'recent_orders', v_recent_orders,
    'inventory', v_inventory,
    'delivery', v_delivery
  );
END;
$$;

REVOKE ALL ON FUNCTION public.get_staff_overview_dashboard(TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_staff_overview_dashboard(TIMESTAMPTZ, TIMESTAMPTZ) TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.search_staff_overview(
  p_query TEXT,
  p_limit INT DEFAULT 12
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_q TEXT := trim(COALESCE(p_query, ''));
  v_limit INT := GREATEST(1, LEAST(COALESCE(p_limit, 12), 30));
  v_roles TEXT[] := '{}'::text[];
  v_results JSONB := '[]'::jsonb;
BEGIN
  IF auth.role() <> 'service_role' AND NOT public.is_staff() THEN
    RAISE EXCEPTION 'staff access required';
  END IF;
  IF length(v_q) < 2 THEN
    RETURN '[]'::jsonb;
  END IF;

  IF auth.role() = 'service_role' THEN
    v_roles := ARRAY['admin']::text[];
  ELSE
    SELECT COALESCE(array_agg(sr.role::text), '{}'::text[])
      INTO v_roles
    FROM public.staff_roles sr
    WHERE sr.user_id = auth.uid();
  END IF;

  IF v_roles && ARRAY['admin','warehouse','finance','sales']::text[] THEN
    SELECT v_results || COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
      INTO v_results
    FROM (
      SELECT 'part' AS kind, si.id, si.oem_part_number AS label,
             COALESCE(si.description, 'Stock item') AS subtitle,
             '/staff/warehouse/master-stock' AS destination,
             si.oem_part_number AS filter
      FROM public.stock_items si
      WHERE si.oem_part_number ILIKE '%' || v_q || '%'
         OR COALESCE(si.description, '') ILIKE '%' || v_q || '%'
      ORDER BY CASE WHEN si.oem_part_number ILIKE v_q || '%' THEN 0 ELSE 1 END,
               si.oem_part_number
      LIMIT LEAST(v_limit, 6)
    ) t;
  END IF;

  IF v_roles && ARRAY['admin','finance','sales']::text[] THEN
    SELECT v_results || COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
      INTO v_results
    FROM (
      SELECT 'customer' AS kind, c.id,
             COALESCE(NULLIF(c.business_name, ''), c.display_name) AS label,
             COALESCE(c.email, c.phone_e164, c.customer_kind, 'Customer') AS subtitle,
             '/staff/crm/credit' AS destination,
             COALESCE(NULLIF(c.business_name, ''), c.display_name) AS filter
      FROM public.customers c
      WHERE c.display_name ILIKE '%' || v_q || '%'
         OR COALESCE(c.business_name, '') ILIKE '%' || v_q || '%'
         OR COALESCE(c.email, '') ILIKE '%' || v_q || '%'
         OR COALESCE(c.phone_e164, '') ILIKE '%' || v_q || '%'
      ORDER BY label
      LIMIT LEAST(v_limit, 6)
    ) t;

    SELECT v_results || COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
      INTO v_results
    FROM (
      SELECT 'order' AS kind, si.id, COALESCE(si.document_number, 'Invoice') AS label,
             concat_ws(' · ', COALESCE(NULLIF(si.customer_business_name, ''), NULLIF(si.customer_display_name, ''), 'Walk-in'), si.currency::text, si.total::text) AS subtitle,
             '/staff/finance' AS destination,
             COALESCE(si.document_number, '') AS filter
      FROM public.sales_invoices si
      WHERE si.doc_type = 'invoice'
        AND si.status = 'posted'
        AND (
          COALESCE(si.document_number, '') ILIKE '%' || v_q || '%'
          OR COALESCE(si.customer_display_name, '') ILIKE '%' || v_q || '%'
          OR COALESCE(si.customer_business_name, '') ILIKE '%' || v_q || '%'
        )
      ORDER BY si.posted_at DESC
      LIMIT LEAST(v_limit, 6)
    ) t;
  END IF;

  IF v_roles && ARRAY['admin','warehouse','sales','dispatcher']::text[] THEN
    SELECT v_results || COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
      INTO v_results
    FROM (
      SELECT 'delivery' AS kind, dj.id, COALESCE(dj.document_number, 'Delivery job') AS label,
             initcap(dj.status::text) AS subtitle,
             '/staff/logistics' AS destination,
             COALESCE(dj.document_number, '') AS filter
      FROM public.delivery_jobs dj
      WHERE COALESCE(dj.document_number, '') ILIKE '%' || v_q || '%'
      ORDER BY dj.created_at DESC
      LIMIT LEAST(v_limit, 6)
    ) t;
  END IF;

  RETURN (
    SELECT COALESCE(jsonb_agg(x.value), '[]'::jsonb)
    FROM (
      SELECT value
      FROM jsonb_array_elements(v_results) value
      LIMIT v_limit
    ) x
  );
END;
$$;

REVOKE ALL ON FUNCTION public.search_staff_overview(TEXT, INT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.search_staff_overview(TEXT, INT) TO authenticated, service_role;
