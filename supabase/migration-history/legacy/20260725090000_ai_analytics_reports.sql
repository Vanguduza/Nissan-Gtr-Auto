-- AI staff analytics + scheduled reports (aggregates only; no PII to Gemini)
-- Plan: docs/plans/2026-07-25-ai-analytics-staff-reports.md
-- ADR: docs/decisions/2026-07-25-ai-report-schema-privacy.md
-- Exclusions: no ZIMRA, no payroll tax, no ledger line dumps

CREATE TYPE public.ai_report_cadence AS ENUM ('daily', 'weekly', 'monthly');
CREATE TYPE public.ai_report_run_status AS ENUM (
  'pending',
  'running',
  'succeeded',
  'partial',
  'failed'
);
CREATE TYPE public.ai_delivery_channel AS ENUM ('email', 'whatsapp');
CREATE TYPE public.ai_delivery_status AS ENUM (
  'queued',
  'sent',
  'failed',
  'skipped'
);

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------
CREATE TABLE public.ai_report_subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_by UUID REFERENCES auth.users (id) ON DELETE SET NULL,
  cadence public.ai_report_cadence NOT NULL DEFAULT 'daily',
  channels public.ai_delivery_channel[] NOT NULL
    DEFAULT ARRAY['email']::public.ai_delivery_channel[],
  recipient_emails TEXT[] NOT NULL DEFAULT '{}'::text[],
  recipient_whatsapp_e164 TEXT[] NOT NULL DEFAULT '{}'::text[],
  include_narrative BOOLEAN NOT NULL DEFAULT true,
  kpi_set TEXT NOT NULL DEFAULT 'ops_sales_v1',
  timezone TEXT NOT NULL DEFAULT 'Africa/Harare',
  active BOOLEAN NOT NULL DEFAULT true,
  last_run_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ai_report_subscriptions_channels_nonempty CHECK (
    cardinality(channels) >= 1
  ),
  CONSTRAINT ai_report_subscriptions_kpi_set_check CHECK (
    kpi_set IN ('ops_sales_v1')
  ),
  CONSTRAINT ai_report_subscriptions_recipients_check CHECK (
    cardinality(recipient_emails) >= 1
    OR cardinality(recipient_whatsapp_e164) >= 1
  )
);

CREATE INDEX ai_report_subscriptions_active_cadence_idx
  ON public.ai_report_subscriptions (active, cadence)
  WHERE active;

CREATE TABLE public.ai_report_runs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  subscription_id UUID REFERENCES public.ai_report_subscriptions (id) ON DELETE SET NULL,
  cadence public.ai_report_cadence NOT NULL,
  period_start TIMESTAMPTZ NOT NULL,
  period_end TIMESTAMPTZ NOT NULL,
  status public.ai_report_run_status NOT NULL DEFAULT 'pending',
  kpi_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  narrative TEXT,
  error TEXT,
  gemini_used BOOLEAN NOT NULL DEFAULT false,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ai_report_runs_period_check CHECK (period_end > period_start)
);

CREATE INDEX ai_report_runs_subscription_idx
  ON public.ai_report_runs (subscription_id, created_at DESC);
CREATE INDEX ai_report_runs_status_idx
  ON public.ai_report_runs (status);

CREATE TABLE public.ai_report_deliveries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id UUID NOT NULL REFERENCES public.ai_report_runs (id) ON DELETE CASCADE,
  channel public.ai_delivery_channel NOT NULL,
  recipient TEXT NOT NULL,
  status public.ai_delivery_status NOT NULL DEFAULT 'queued',
  provider_ref TEXT,
  error TEXT,
  sent_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ai_report_deliveries_run_idx
  ON public.ai_report_deliveries (run_id);

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
ALTER TABLE public.ai_report_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_report_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_report_deliveries ENABLE ROW LEVEL SECURITY;

CREATE POLICY ai_report_subscriptions_staff_select
  ON public.ai_report_subscriptions FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
  );

CREATE POLICY ai_report_subscriptions_staff_insert
  ON public.ai_report_subscriptions FOR INSERT TO authenticated
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
  );

CREATE POLICY ai_report_subscriptions_staff_update
  ON public.ai_report_subscriptions FOR UPDATE TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
  )
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
  );

CREATE POLICY ai_report_subscriptions_staff_delete
  ON public.ai_report_subscriptions FOR DELETE TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
  );

CREATE POLICY ai_report_runs_staff_select
  ON public.ai_report_runs FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
  );

CREATE POLICY ai_report_deliveries_staff_select
  ON public.ai_report_deliveries FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
  );

-- Writes for runs/deliveries: service_role / SECURITY DEFINER helpers only
REVOKE ALL ON TABLE public.ai_report_runs FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.ai_report_deliveries FROM PUBLIC, anon;
GRANT SELECT ON TABLE public.ai_report_subscriptions TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.ai_report_subscriptions TO authenticated;
GRANT SELECT ON TABLE public.ai_report_runs TO authenticated;
GRANT SELECT ON TABLE public.ai_report_deliveries TO authenticated;
GRANT ALL ON TABLE public.ai_report_subscriptions TO service_role;
GRANT ALL ON TABLE public.ai_report_runs TO service_role;
GRANT ALL ON TABLE public.ai_report_deliveries TO service_role;

-- ---------------------------------------------------------------------------
-- Auth helper for KPI / staff RPCs
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._assert_ai_analytics_staff()
RETURNS void
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin/finance/sales role required';
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public._assert_ai_analytics_staff() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public._assert_ai_analytics_staff()
  TO authenticated, service_role;

-- ---------------------------------------------------------------------------
-- KPI RPCs (aggregates only — no customer PII, no journal dumps)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.kpi_sales_summary(
  p_from TIMESTAMPTZ,
  p_to TIMESTAMPTZ
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_by_currency JSONB;
  v_order_count BIGINT;
  v_aov JSONB;
BEGIN
  PERFORM public._assert_ai_analytics_staff();
  IF p_from IS NULL OR p_to IS NULL OR p_to <= p_from THEN
    RAISE EXCEPTION 'valid p_from / p_to required';
  END IF;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.currency), '[]'::jsonb)
  INTO v_by_currency
  FROM (
    SELECT
      si.currency::text AS currency,
      COUNT(*)::bigint AS invoice_count,
      COALESCE(SUM(si.total), 0)::numeric AS revenue,
      COALESCE(SUM(si.subtotal), 0)::numeric AS subtotal
    FROM public.sales_invoices si
    WHERE si.doc_type = 'invoice'
      AND si.status = 'posted'
      AND si.posted_at >= p_from
      AND si.posted_at < p_to
    GROUP BY si.currency
  ) t;

  SELECT COUNT(*)::bigint INTO v_order_count
  FROM public.sales_invoices si
  WHERE si.doc_type = 'invoice'
    AND si.status = 'posted'
    AND si.posted_at >= p_from
    AND si.posted_at < p_to;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.currency), '[]'::jsonb)
  INTO v_aov
  FROM (
    SELECT
      si.currency::text AS currency,
      CASE
        WHEN COUNT(*) = 0 THEN 0::numeric
        ELSE ROUND(COALESCE(SUM(si.total), 0) / COUNT(*), 2)
      END AS average_order_value
    FROM public.sales_invoices si
    WHERE si.doc_type = 'invoice'
      AND si.status = 'posted'
      AND si.posted_at >= p_from
      AND si.posted_at < p_to
    GROUP BY si.currency
  ) t;

  RETURN jsonb_build_object(
    'period_from', p_from,
    'period_to', p_to,
    'order_count', v_order_count,
    'by_currency', v_by_currency,
    'average_order_value', v_aov
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.kpi_returns_summary(
  p_from TIMESTAMPTZ,
  p_to TIMESTAMPTZ
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_by_currency JSONB;
  v_cn_count BIGINT;
BEGIN
  PERFORM public._assert_ai_analytics_staff();
  IF p_from IS NULL OR p_to IS NULL OR p_to <= p_from THEN
    RAISE EXCEPTION 'valid p_from / p_to required';
  END IF;

  SELECT COUNT(*)::bigint INTO v_cn_count
  FROM public.sales_invoices si
  WHERE si.doc_type = 'credit_note'
    AND si.status = 'posted'
    AND si.posted_at >= p_from
    AND si.posted_at < p_to;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.currency), '[]'::jsonb)
  INTO v_by_currency
  FROM (
    SELECT
      si.currency::text AS currency,
      COUNT(*)::bigint AS credit_note_count,
      COALESCE(SUM(si.total), 0)::numeric AS returns_total
    FROM public.sales_invoices si
    WHERE si.doc_type = 'credit_note'
      AND si.status = 'posted'
      AND si.posted_at >= p_from
      AND si.posted_at < p_to
    GROUP BY si.currency
  ) t;

  RETURN jsonb_build_object(
    'period_from', p_from,
    'period_to', p_to,
    'credit_note_count', v_cn_count,
    'by_currency', v_by_currency
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.kpi_top_skus(
  p_from TIMESTAMPTZ,
  p_to TIMESTAMPTZ,
  p_limit INT DEFAULT 10
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_limit INT := GREATEST(1, LEAST(COALESCE(p_limit, 10), 50));
  v_rows JSONB;
BEGIN
  PERFORM public._assert_ai_analytics_staff();
  IF p_from IS NULL OR p_to IS NULL OR p_to <= p_from THEN
    RAISE EXCEPTION 'valid p_from / p_to required';
  END IF;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
  INTO v_rows
  FROM (
    SELECT
      si.oem_part_number AS oem,
      COALESCE(SUM(sil.qty_base), 0)::numeric AS qty,
      sil_cur.currency,
      COALESCE(SUM(sil.line_total), 0)::numeric AS revenue
    FROM public.sales_invoice_lines sil
    JOIN public.sales_invoices inv ON inv.id = sil.invoice_id
    JOIN public.stock_items si ON si.id = sil.stock_item_id
    CROSS JOIN LATERAL (SELECT inv.currency::text AS currency) sil_cur
    WHERE inv.doc_type = 'invoice'
      AND inv.status = 'posted'
      AND inv.posted_at >= p_from
      AND inv.posted_at < p_to
      AND NOT sil.is_core_charge
    GROUP BY si.oem_part_number, sil_cur.currency
    ORDER BY SUM(sil.qty_base) DESC, SUM(sil.line_total) DESC
    LIMIT v_limit
  ) t;

  RETURN jsonb_build_object(
    'period_from', p_from,
    'period_to', p_to,
    'limit', v_limit,
    'items', v_rows
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.kpi_inventory_summary()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_on_hand NUMERIC;
  v_low_stock BIGINT;
  v_stockouts BIGINT;
  v_quarantine NUMERIC;
BEGIN
  PERFORM public._assert_ai_analytics_staff();

  SELECT COALESCE(SUM(sl.quantity), 0)
  INTO v_on_hand
  FROM public.stock_levels sl
  JOIN public.warehouses w ON w.id = sl.warehouse_id
  WHERE w.is_active AND NOT w.is_quarantine;

  SELECT COUNT(*)::bigint
  INTO v_low_stock
  FROM public.stock_items si
  JOIN public.stock_levels sl ON sl.stock_item_id = si.id
  JOIN public.warehouses w ON w.id = sl.warehouse_id
  WHERE w.is_active
    AND NOT w.is_quarantine
    AND si.reorder_point IS NOT NULL
    AND sl.quantity > 0
    AND sl.quantity <= si.reorder_point;

  SELECT COUNT(*)::bigint
  INTO v_stockouts
  FROM public.stock_items si
  JOIN public.stock_levels sl ON sl.stock_item_id = si.id
  JOIN public.warehouses w ON w.id = sl.warehouse_id
  WHERE w.is_active
    AND NOT w.is_quarantine
    AND sl.quantity = 0
    AND (
      si.reorder_point IS NOT NULL
      OR EXISTS (
        SELECT 1
        FROM public.stock_levels sl2
        WHERE sl2.stock_item_id = si.id
          AND sl2.quantity > 0
      )
    );

  SELECT COALESCE(SUM(sl.quantity), 0)
  INTO v_quarantine
  FROM public.stock_levels sl
  JOIN public.warehouses w ON w.id = sl.warehouse_id
  WHERE w.is_active AND w.is_quarantine;

  RETURN jsonb_build_object(
    'on_hand_qty', v_on_hand,
    'low_stock_count', v_low_stock,
    'stockout_count', v_stockouts,
    'quarantine_qty', v_quarantine
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.kpi_ar_aging_snapshot()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_buckets JSONB;
  v_open_customers BIGINT;
  v_open_balance_by_currency JSONB;
BEGIN
  PERFORM public._assert_ai_analytics_staff();

  -- Age unpaid invoice balances by posted_at (aggregates only; no customer ids)
  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.bucket), '[]'::jsonb)
  INTO v_buckets
  FROM (
    SELECT
      b.bucket,
      b.currency,
      COUNT(*)::bigint AS invoice_count,
      COALESCE(SUM(b.open_amt), 0)::numeric AS open_amount
    FROM (
      SELECT
        si.currency::text AS currency,
        GREATEST(si.total - si.amount_paid, 0)::numeric AS open_amt,
        CASE
          WHEN si.posted_at >= now() - INTERVAL '30 days' THEN '0_30'
          WHEN si.posted_at >= now() - INTERVAL '60 days' THEN '31_60'
          WHEN si.posted_at >= now() - INTERVAL '90 days' THEN '61_90'
          ELSE '90_plus'
        END AS bucket
      FROM public.sales_invoices si
      WHERE si.doc_type = 'invoice'
        AND si.status = 'posted'
        AND (si.total - si.amount_paid) > 0
    ) b
    GROUP BY b.bucket, b.currency
  ) t;

  SELECT COUNT(*)::bigint INTO v_open_customers
  FROM public.customers c
  WHERE c.open_balance > 0;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.currency), '[]'::jsonb)
  INTO v_open_balance_by_currency
  FROM (
    SELECT
      c.currency::text AS currency,
      COUNT(*) FILTER (WHERE c.open_balance > 0)::bigint AS customer_count,
      COALESCE(SUM(c.open_balance), 0)::numeric AS open_balance
    FROM public.customers c
    GROUP BY c.currency
  ) t;

  RETURN jsonb_build_object(
    'as_of', now(),
    'customers_with_open_balance', v_open_customers,
    'customer_open_balance_by_currency', v_open_balance_by_currency,
    'invoice_aging_buckets', v_buckets
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.kpi_credit_holds_summary()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_held_customers BIGINT;
  v_on_hold_invoices BIGINT;
  v_on_hold_by_currency JSONB;
BEGIN
  PERFORM public._assert_ai_analytics_staff();

  SELECT COUNT(*)::bigint INTO v_held_customers
  FROM public.customers c
  WHERE c.credit_hold;

  SELECT COUNT(*)::bigint INTO v_on_hold_invoices
  FROM public.sales_invoices si
  WHERE si.status = 'on_hold'
    AND si.doc_type = 'invoice';

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.currency), '[]'::jsonb)
  INTO v_on_hold_by_currency
  FROM (
    SELECT
      si.currency::text AS currency,
      COUNT(*)::bigint AS invoice_count,
      COALESCE(SUM(si.total), 0)::numeric AS total
    FROM public.sales_invoices si
    WHERE si.status = 'on_hold'
      AND si.doc_type = 'invoice'
    GROUP BY si.currency
  ) t;

  RETURN jsonb_build_object(
    'customers_on_credit_hold', v_held_customers,
    'invoices_on_hold', v_on_hold_invoices,
    'on_hold_by_currency', v_on_hold_by_currency
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.kpi_open_deliveries_summary()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_open_dns BIGINT;
  v_open_jobs BIGINT;
  v_jobs_by_status JSONB;
BEGIN
  PERFORM public._assert_ai_analytics_staff();

  SELECT COUNT(*)::bigint INTO v_open_dns
  FROM public.delivery_notes dn
  WHERE dn.status = 'draft';

  SELECT COUNT(*)::bigint INTO v_open_jobs
  FROM public.delivery_jobs dj
  WHERE dj.status IN ('pending', 'dispatched');

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.status), '[]'::jsonb)
  INTO v_jobs_by_status
  FROM (
    SELECT
      dj.status::text AS status,
      COUNT(*)::bigint AS job_count
    FROM public.delivery_jobs dj
    WHERE dj.status IN ('pending', 'dispatched')
    GROUP BY dj.status
  ) t;

  RETURN jsonb_build_object(
    'open_delivery_notes_draft', v_open_dns,
    'open_delivery_jobs', v_open_jobs,
    'jobs_by_status', v_jobs_by_status
  );
END;
$$;

-- Bundle for ops_sales_v1 (staff UI + worker)
CREATE OR REPLACE FUNCTION public.kpi_ops_sales_v1(
  p_from TIMESTAMPTZ,
  p_to TIMESTAMPTZ,
  p_top_limit INT DEFAULT 10
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  PERFORM public._assert_ai_analytics_staff();
  RETURN jsonb_build_object(
    'kpi_set', 'ops_sales_v1',
    'period_from', p_from,
    'period_to', p_to,
    'sales', public.kpi_sales_summary(p_from, p_to),
    'returns', public.kpi_returns_summary(p_from, p_to),
    'top_skus', public.kpi_top_skus(p_from, p_to, p_top_limit),
    'inventory', public.kpi_inventory_summary(),
    'ar_aging', public.kpi_ar_aging_snapshot(),
    'credit_holds', public.kpi_credit_holds_summary(),
    'open_deliveries', public.kpi_open_deliveries_summary()
  );
END;
$$;

-- ---------------------------------------------------------------------------
-- Worker helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.list_due_ai_report_subscriptions(
  p_cadence public.ai_report_cadence,
  p_now TIMESTAMPTZ DEFAULT now(),
  p_force BOOLEAN DEFAULT false,
  p_subscription_id UUID DEFAULT NULL
)
RETURNS SETOF public.ai_report_subscriptions
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.role() <> 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  RETURN QUERY
  SELECT s.*
  FROM public.ai_report_subscriptions s
  WHERE s.active
    AND s.cadence = p_cadence
    AND (p_subscription_id IS NULL OR s.id = p_subscription_id)
    AND (
      p_force
      OR s.last_run_at IS NULL
      OR (
        p_cadence = 'daily'
        AND s.last_run_at < (p_now - INTERVAL '20 hours')
      )
      OR (
        p_cadence = 'weekly'
        AND s.last_run_at < (p_now - INTERVAL '6 days')
      )
      OR (
        p_cadence = 'monthly'
        AND s.last_run_at < (p_now - INTERVAL '27 days')
      )
    )
  ORDER BY s.created_at;
END;
$$;

CREATE OR REPLACE FUNCTION public.insert_ai_report_run(
  p_subscription_id UUID,
  p_cadence public.ai_report_cadence,
  p_period_start TIMESTAMPTZ,
  p_period_end TIMESTAMPTZ
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  IF auth.role() <> 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  INSERT INTO public.ai_report_runs (
    subscription_id,
    cadence,
    period_start,
    period_end,
    status,
    started_at
  )
  VALUES (
    p_subscription_id,
    p_cadence,
    p_period_start,
    p_period_end,
    'running',
    now()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.finalize_ai_report_run(
  p_run_id UUID,
  p_status public.ai_report_run_status,
  p_kpi_json JSONB DEFAULT NULL,
  p_narrative TEXT DEFAULT NULL,
  p_error TEXT DEFAULT NULL,
  p_gemini_used BOOLEAN DEFAULT false
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_sub UUID;
BEGIN
  IF auth.role() <> 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  UPDATE public.ai_report_runs
  SET
    status = p_status,
    kpi_json = COALESCE(p_kpi_json, kpi_json),
    narrative = p_narrative,
    error = p_error,
    gemini_used = COALESCE(p_gemini_used, false),
    finished_at = now()
  WHERE id = p_run_id
  RETURNING subscription_id INTO v_sub;

  IF v_sub IS NOT NULL AND p_status IN ('succeeded', 'partial') THEN
    UPDATE public.ai_report_subscriptions
    SET last_run_at = now(), updated_at = now()
    WHERE id = v_sub;
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.insert_ai_report_delivery(
  p_run_id UUID,
  p_channel public.ai_delivery_channel,
  p_recipient TEXT,
  p_status public.ai_delivery_status,
  p_provider_ref TEXT DEFAULT NULL,
  p_error TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  IF auth.role() <> 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  INSERT INTO public.ai_report_deliveries (
    run_id,
    channel,
    recipient,
    status,
    provider_ref,
    error,
    sent_at
  )
  VALUES (
    p_run_id,
    p_channel,
    trim(p_recipient),
    p_status,
    p_provider_ref,
    p_error,
    CASE WHEN p_status = 'sent' THEN now() ELSE NULL END
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

-- Grants
REVOKE ALL ON FUNCTION public.kpi_sales_summary(TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.kpi_returns_summary(TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.kpi_top_skus(TIMESTAMPTZ, TIMESTAMPTZ, INT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.kpi_inventory_summary() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.kpi_ar_aging_snapshot() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.kpi_credit_holds_summary() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.kpi_open_deliveries_summary() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.kpi_ops_sales_v1(TIMESTAMPTZ, TIMESTAMPTZ, INT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_due_ai_report_subscriptions(
  public.ai_report_cadence, TIMESTAMPTZ, BOOLEAN, UUID
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.insert_ai_report_run(
  UUID, public.ai_report_cadence, TIMESTAMPTZ, TIMESTAMPTZ
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.finalize_ai_report_run(
  UUID, public.ai_report_run_status, JSONB, TEXT, TEXT, BOOLEAN
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.insert_ai_report_delivery(
  UUID, public.ai_delivery_channel, TEXT, public.ai_delivery_status, TEXT, TEXT
) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.kpi_sales_summary(TIMESTAMPTZ, TIMESTAMPTZ)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.kpi_returns_summary(TIMESTAMPTZ, TIMESTAMPTZ)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.kpi_top_skus(TIMESTAMPTZ, TIMESTAMPTZ, INT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.kpi_inventory_summary()
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.kpi_ar_aging_snapshot()
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.kpi_credit_holds_summary()
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.kpi_open_deliveries_summary()
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.kpi_ops_sales_v1(TIMESTAMPTZ, TIMESTAMPTZ, INT)
  TO authenticated, service_role;

GRANT EXECUTE ON FUNCTION public.list_due_ai_report_subscriptions(
  public.ai_report_cadence, TIMESTAMPTZ, BOOLEAN, UUID
) TO service_role;
GRANT EXECUTE ON FUNCTION public.insert_ai_report_run(
  UUID, public.ai_report_cadence, TIMESTAMPTZ, TIMESTAMPTZ
) TO service_role;
GRANT EXECUTE ON FUNCTION public.finalize_ai_report_run(
  UUID, public.ai_report_run_status, JSONB, TEXT, TEXT, BOOLEAN
) TO service_role;
GRANT EXECUTE ON FUNCTION public.insert_ai_report_delivery(
  UUID, public.ai_delivery_channel, TEXT, public.ai_delivery_status, TEXT, TEXT
) TO service_role;
