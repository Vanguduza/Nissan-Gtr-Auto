-- AI autonomous layer Phase A: CRM promo (opt-in), finance KPI aggregates, stores ABC + directives
-- Plan: docs/plans/2026-08-03-ai-autonomous-erp-layer.md
-- ADR: docs/decisions/2026-08-03-ai-autonomous-layer-constraints.md
-- Exclusions: no ZIMRA, no payroll tax, no Text-to-SQL, no ledger mutations

-- ---------------------------------------------------------------------------
-- CRM: marketing opt-in + cooldown
-- ---------------------------------------------------------------------------
ALTER TABLE public.customers
  ADD COLUMN IF NOT EXISTS marketing_opt_in BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS last_promotional_message_at TIMESTAMPTZ;

COMMENT ON COLUMN public.customers.marketing_opt_in IS
  'Customer must opt in before autonomous CRM promo worker may contact them.';
COMMENT ON COLUMN public.customers.last_promotional_message_at IS
  'Cooldown stamp written by process-crm-promos worker only.';

CREATE OR REPLACE FUNCTION public.customers_protect_privileged_columns()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.role() = 'service_role' OR public.is_staff() THEN
    RETURN NEW;
  END IF;

  IF public._storefront_rpc_active() THEN
    IF NEW.profile_id IS DISTINCT FROM OLD.profile_id
       OR NEW.price_list_id IS DISTINCT FROM OLD.price_list_id
       OR NEW.credit_limit IS DISTINCT FROM OLD.credit_limit
       OR NEW.credit_hold IS DISTINCT FROM OLD.credit_hold
       OR NEW.currency IS DISTINCT FROM OLD.currency
       OR NEW.id IS DISTINCT FROM OLD.id
       OR NEW.last_promotional_message_at IS DISTINCT FROM OLD.last_promotional_message_at
    THEN
      RAISE EXCEPTION 'storefront path may not change commercial control fields';
    END IF;
    RETURN NEW;
  END IF;

  -- Self-service: contact + receipt prefs + marketing_opt_in (not cooldown stamp)
  IF NEW.profile_id IS DISTINCT FROM OLD.profile_id
     OR NEW.price_list_id IS DISTINCT FROM OLD.price_list_id
     OR NEW.credit_limit IS DISTINCT FROM OLD.credit_limit
     OR NEW.credit_hold IS DISTINCT FROM OLD.credit_hold
     OR NEW.open_balance IS DISTINCT FROM OLD.open_balance
     OR NEW.currency IS DISTINCT FROM OLD.currency
     OR NEW.id IS DISTINCT FROM OLD.id
     OR NEW.last_promotional_message_at IS DISTINCT FROM OLD.last_promotional_message_at
  THEN
    RAISE EXCEPTION 'customers may only update contact, receipt, and marketing opt-in fields';
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.set_own_marketing_opt_in(p_opt_in BOOLEAN)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  UPDATE public.customers
  SET marketing_opt_in = COALESCE(p_opt_in, false),
      updated_at = now()
  WHERE profile_id = auth.uid()
  RETURNING id INTO v_id;

  IF v_id IS NULL THEN
    RAISE EXCEPTION 'customer profile required';
  END IF;
  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public.set_own_marketing_opt_in(BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.set_own_marketing_opt_in(BOOLEAN) TO authenticated;

CREATE TYPE public.ai_promo_run_status AS ENUM (
  'pending',
  'running',
  'succeeded',
  'partial',
  'failed',
  'skipped'
);

CREATE TABLE public.ai_promo_settings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  active BOOLEAN NOT NULL DEFAULT true,
  inactivity_days INT NOT NULL DEFAULT 90 CHECK (inactivity_days >= 1),
  cooldown_days INT NOT NULL DEFAULT 30 CHECK (cooldown_days >= 1),
  max_skus_per_message INT NOT NULL DEFAULT 5 CHECK (max_skus_per_message BETWEEN 1 AND 20),
  channels public.ai_delivery_channel[] NOT NULL
    DEFAULT ARRAY['email']::public.ai_delivery_channel[],
  include_llm_copy BOOLEAN NOT NULL DEFAULT true,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ai_promo_settings_channels_nonempty CHECK (cardinality(channels) >= 1)
);

INSERT INTO public.ai_promo_settings (active)
SELECT true
WHERE NOT EXISTS (SELECT 1 FROM public.ai_promo_settings LIMIT 1);

CREATE TABLE public.ai_promo_runs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  status public.ai_promo_run_status NOT NULL DEFAULT 'pending',
  candidates_considered INT NOT NULL DEFAULT 0,
  messages_queued INT NOT NULL DEFAULT 0,
  error TEXT,
  gemini_used BOOLEAN NOT NULL DEFAULT false,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public.ai_promo_deliveries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  run_id UUID NOT NULL REFERENCES public.ai_promo_runs (id) ON DELETE CASCADE,
  customer_id UUID NOT NULL REFERENCES public.customers (id) ON DELETE CASCADE,
  channel public.ai_delivery_channel NOT NULL,
  recipient TEXT NOT NULL,
  status public.ai_delivery_status NOT NULL DEFAULT 'queued',
  body_preview TEXT,
  oem_skus TEXT[] NOT NULL DEFAULT '{}'::text[],
  vehicle_label TEXT,
  provider_ref TEXT,
  error TEXT,
  gemini_used BOOLEAN NOT NULL DEFAULT false,
  sent_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ai_promo_deliveries_run_idx ON public.ai_promo_deliveries (run_id);
CREATE INDEX ai_promo_deliveries_customer_idx
  ON public.ai_promo_deliveries (customer_id, created_at DESC);

ALTER TABLE public.ai_promo_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_promo_runs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ai_promo_deliveries ENABLE ROW LEVEL SECURITY;

CREATE POLICY ai_promo_settings_staff_select
  ON public.ai_promo_settings FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
  );

CREATE POLICY ai_promo_settings_admin_sales_write
  ON public.ai_promo_settings FOR ALL TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[])
  )
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[])
  );

CREATE POLICY ai_promo_runs_staff_select
  ON public.ai_promo_runs FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
  );

CREATE POLICY ai_promo_deliveries_staff_select
  ON public.ai_promo_deliveries FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'sales', 'finance']::public.staff_role[])
  );

REVOKE ALL ON TABLE public.ai_promo_settings FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.ai_promo_runs FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.ai_promo_deliveries FROM PUBLIC, anon;
GRANT SELECT ON TABLE public.ai_promo_settings TO authenticated;
GRANT INSERT, UPDATE, DELETE ON TABLE public.ai_promo_settings TO authenticated;
GRANT SELECT ON TABLE public.ai_promo_runs TO authenticated;
GRANT SELECT ON TABLE public.ai_promo_deliveries TO authenticated;
GRANT ALL ON TABLE public.ai_promo_settings TO service_role;
GRANT ALL ON TABLE public.ai_promo_runs TO service_role;
GRANT ALL ON TABLE public.ai_promo_deliveries TO service_role;

-- ---------------------------------------------------------------------------
-- Stores: ABC classification + AI directives
-- ---------------------------------------------------------------------------
CREATE TYPE public.inventory_abc_class AS ENUM ('A', 'B', 'C');

CREATE TABLE public.inventory_abc_snapshots (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  as_of TIMESTAMPTZ NOT NULL DEFAULT now(),
  period_start TIMESTAMPTZ NOT NULL,
  period_end TIMESTAMPTZ NOT NULL,
  stock_item_id UUID NOT NULL REFERENCES public.stock_items (id) ON DELETE CASCADE,
  oem_part_number VARCHAR(32) NOT NULL,
  qty_sold NUMERIC(18, 3) NOT NULL DEFAULT 0,
  revenue_usd NUMERIC(18, 4) NOT NULL DEFAULT 0,
  revenue_share NUMERIC(12, 8) NOT NULL DEFAULT 0,
  cumulative_share NUMERIC(12, 8) NOT NULL DEFAULT 0,
  abc_class public.inventory_abc_class NOT NULL,
  CONSTRAINT inventory_abc_period_check CHECK (period_end > period_start)
);

CREATE INDEX inventory_abc_snapshots_as_of_idx
  ON public.inventory_abc_snapshots (as_of DESC);
CREATE INDEX inventory_abc_snapshots_class_idx
  ON public.inventory_abc_snapshots (as_of DESC, abc_class);
CREATE INDEX inventory_abc_snapshots_item_idx
  ON public.inventory_abc_snapshots (stock_item_id, as_of DESC);

CREATE TABLE public.inventory_ai_directives (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  warehouse_id UUID REFERENCES public.warehouses (id) ON DELETE SET NULL,
  period_start TIMESTAMPTZ,
  period_end TIMESTAMPTZ,
  kpi_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  directives JSONB NOT NULL DEFAULT '[]'::jsonb,
  narrative TEXT,
  gemini_used BOOLEAN NOT NULL DEFAULT false,
  error TEXT,
  created_by UUID REFERENCES auth.users (id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX inventory_ai_directives_wh_idx
  ON public.inventory_ai_directives (warehouse_id, created_at DESC);

ALTER TABLE public.inventory_abc_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_ai_directives ENABLE ROW LEVEL SECURITY;

CREATE POLICY inventory_abc_snapshots_staff_select
  ON public.inventory_abc_snapshots FOR SELECT TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'finance', 'sales']::public.staff_role[]
    )
  );

CREATE POLICY inventory_ai_directives_staff_select
  ON public.inventory_ai_directives FOR SELECT TO authenticated
  USING (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]
    )
  );

CREATE POLICY inventory_ai_directives_staff_insert
  ON public.inventory_ai_directives FOR INSERT TO authenticated
  WITH CHECK (
    public.has_staff_role(
      ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]
    )
  );

REVOKE ALL ON TABLE public.inventory_abc_snapshots FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.inventory_ai_directives FROM PUBLIC, anon;
GRANT SELECT ON TABLE public.inventory_abc_snapshots TO authenticated;
GRANT SELECT, INSERT ON TABLE public.inventory_ai_directives TO authenticated;
GRANT ALL ON TABLE public.inventory_abc_snapshots TO service_role;
GRANT ALL ON TABLE public.inventory_ai_directives TO service_role;

-- ---------------------------------------------------------------------------
-- Finance KPI set on existing subscriptions
-- ---------------------------------------------------------------------------
ALTER TABLE public.ai_report_subscriptions
  DROP CONSTRAINT IF EXISTS ai_report_subscriptions_kpi_set_check;

ALTER TABLE public.ai_report_subscriptions
  ADD CONSTRAINT ai_report_subscriptions_kpi_set_check CHECK (
    kpi_set IN ('ops_sales_v1', 'finance_performance_v1')
  );

-- ---------------------------------------------------------------------------
-- Assert helpers
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public._assert_ai_finance_staff()
RETURNS VOID
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public._assert_ai_stores_staff()
RETURNS VOID
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(
      ARRAY['admin', 'warehouse', 'finance']::public.staff_role[]
    )
  ) THEN
    RAISE EXCEPTION 'warehouse/finance/admin role required';
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public._assert_ai_crm_staff()
RETURNS VOID
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'sales or admin role required';
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- Finance performance aggregates (no journal line dumps)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.kpi_finance_performance_v1(
  p_from TIMESTAMPTZ,
  p_to TIMESTAMPTZ,
  p_top_expenses INT DEFAULT 8
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_from_d DATE;
  v_to_d DATE;
  v_income NUMERIC := 0;
  v_expense NUMERIC := 0;
  v_by_type JSONB;
  v_top_expenses JSONB;
  v_sales JSONB;
  v_ar JSONB;
BEGIN
  PERFORM public._assert_ai_finance_staff();
  IF p_from IS NULL OR p_to IS NULL OR p_to <= p_from THEN
    RAISE EXCEPTION 'valid p_from / p_to required';
  END IF;

  v_from_d := (p_from AT TIME ZONE 'UTC')::date;
  v_to_d := ((p_to - INTERVAL '1 second') AT TIME ZONE 'UTC')::date;

  SELECT COALESCE(SUM(amount_usd) FILTER (WHERE account_type = 'income'), 0),
         COALESCE(SUM(-amount_usd) FILTER (WHERE account_type = 'expense'), 0)
  INTO v_income, v_expense
  FROM public.report_profit_and_loss(v_from_d, v_to_d, NULL);

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.account_type, t.account_code), '[]'::jsonb)
  INTO v_by_type
  FROM (
    SELECT
      account_code,
      account_name,
      account_type::text,
      amount,
      amount_usd
    FROM public.report_profit_and_loss(v_from_d, v_to_d, NULL)
  ) t;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
  INTO v_top_expenses
  FROM (
    SELECT
      account_code,
      account_name,
      (-amount_usd)::numeric AS expense_usd
    FROM public.report_profit_and_loss(v_from_d, v_to_d, NULL)
    WHERE account_type = 'expense'
    ORDER BY (-amount_usd) DESC
    LIMIT GREATEST(1, LEAST(COALESCE(p_top_expenses, 8), 25))
  ) t;

  -- Sales / AR reuse analytics helpers (admin|finance pass _assert_ai_analytics_staff)
  v_sales := public.kpi_sales_summary(p_from, p_to);
  v_ar := public.kpi_ar_aging_snapshot();

  RETURN jsonb_build_object(
    'kpi_set', 'finance_performance_v1',
    'period_from', p_from,
    'period_to', p_to,
    'income_usd', v_income,
    'expense_usd', v_expense,
    'net_profit_usd', v_income - v_expense,
    'net_margin_pct', CASE
      WHEN v_income = 0 THEN NULL
      ELSE round(((v_income - v_expense) / NULLIF(v_income, 0)) * 100, 2)
    END,
    'pnl_by_account', v_by_type,
    'top_expense_accounts', v_top_expenses,
    'sales', v_sales,
    'ar_aging', v_ar,
    'note', 'Aggregates only; tax-agnostic; no journal line dumps'
  );
END;
$$;

-- ---------------------------------------------------------------------------
-- ABC classification
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.run_inventory_abc_classification(
  p_from TIMESTAMPTZ,
  p_to TIMESTAMPTZ
)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_total NUMERIC := 0;
  v_cum NUMERIC := 0;
  v_count INT := 0;
  v_as_of TIMESTAMPTZ := now();
  r RECORD;
  v_class public.inventory_abc_class;
  v_share NUMERIC;
BEGIN
  PERFORM public._assert_ai_stores_staff();
  IF p_from IS NULL OR p_to IS NULL OR p_to <= p_from THEN
    RAISE EXCEPTION 'valid p_from / p_to required';
  END IF;

  DELETE FROM public.inventory_abc_snapshots
  WHERE as_of > now() - INTERVAL '1 minute';

  SELECT COALESCE(SUM(
    sil.line_total / NULLIF(COALESCE(inv.exchange_rate_applied, 1), 0)
  ), 0)
  INTO v_total
  FROM public.sales_invoice_lines sil
  JOIN public.sales_invoices inv ON inv.id = sil.invoice_id
  WHERE inv.doc_type = 'invoice'
    AND inv.status = 'posted'
    AND inv.posted_at >= p_from
    AND inv.posted_at < p_to
    AND NOT sil.is_core_charge;

  IF v_total IS NULL OR v_total <= 0 THEN
    RETURN 0;
  END IF;

  FOR r IN
    SELECT
      si.id AS stock_item_id,
      si.oem_part_number,
      COALESCE(SUM(sil.qty_base), 0)::numeric AS qty_sold,
      COALESCE(SUM(
        sil.line_total / NULLIF(COALESCE(inv.exchange_rate_applied, 1), 0)
      ), 0)::numeric AS revenue_usd
    FROM public.sales_invoice_lines sil
    JOIN public.sales_invoices inv ON inv.id = sil.invoice_id
    JOIN public.stock_items si ON si.id = sil.stock_item_id
    WHERE inv.doc_type = 'invoice'
      AND inv.status = 'posted'
      AND inv.posted_at >= p_from
      AND inv.posted_at < p_to
      AND NOT sil.is_core_charge
    GROUP BY si.id, si.oem_part_number
    HAVING COALESCE(SUM(
      sil.line_total / NULLIF(COALESCE(inv.exchange_rate_applied, 1), 0)
    ), 0) > 0
    ORDER BY revenue_usd DESC
  LOOP
    v_share := r.revenue_usd / v_total;
    -- Class by cumulative share *before* this SKU so a single 90% seller is still A
    IF v_cum < 0.80 THEN
      v_class := 'A';
    ELSIF v_cum < 0.95 THEN
      v_class := 'B';
    ELSE
      v_class := 'C';
    END IF;
    v_cum := v_cum + v_share;

    INSERT INTO public.inventory_abc_snapshots (
      as_of, period_start, period_end, stock_item_id, oem_part_number,
      qty_sold, revenue_usd, revenue_share, cumulative_share, abc_class
    ) VALUES (
      v_as_of, p_from, p_to, r.stock_item_id, r.oem_part_number,
      r.qty_sold, r.revenue_usd, v_share, v_cum, v_class
    );
    v_count := v_count + 1;
  END LOOP;

  RETURN v_count;
END;
$$;

CREATE OR REPLACE FUNCTION public.kpi_stores_forecast_v1(
  p_warehouse_id UUID,
  p_from TIMESTAMPTZ DEFAULT (now() - INTERVAL '90 days'),
  p_to TIMESTAMPTZ DEFAULT now(),
  p_run_abc BOOLEAN DEFAULT true
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_abc_count INT := 0;
  v_latest TIMESTAMPTZ;
  v_class_counts JSONB;
  v_tier_a JSONB;
  v_tier_c JSONB;
  v_suggestions JSONB;
  v_on_hand NUMERIC := 0;
  v_low_stock BIGINT := 0;
BEGIN
  PERFORM public._assert_ai_stores_staff();
  IF p_warehouse_id IS NULL THEN
    RAISE EXCEPTION 'warehouse_id required';
  END IF;

  IF COALESCE(p_run_abc, true) THEN
    v_abc_count := public.run_inventory_abc_classification(p_from, p_to);
  END IF;

  SELECT MAX(as_of) INTO v_latest FROM public.inventory_abc_snapshots;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb ORDER BY t.abc_class), '[]'::jsonb)
  INTO v_class_counts
  FROM (
    SELECT abc_class::text, COUNT(*)::bigint AS sku_count,
           COALESCE(SUM(revenue_usd), 0)::numeric AS revenue_usd
    FROM public.inventory_abc_snapshots
    WHERE as_of = v_latest
    GROUP BY abc_class
  ) t;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
  INTO v_tier_a
  FROM (
    SELECT
      s.oem_part_number AS oem,
      s.qty_sold,
      s.revenue_usd,
      COALESCE(sl.quantity, 0)::numeric AS on_hand,
      si.reorder_point,
      si.reorder_qty
    FROM public.inventory_abc_snapshots s
    JOIN public.stock_items si ON si.id = s.stock_item_id
    LEFT JOIN public.stock_levels sl
      ON sl.stock_item_id = s.stock_item_id AND sl.warehouse_id = p_warehouse_id
    WHERE s.as_of = v_latest AND s.abc_class = 'A'
    ORDER BY s.revenue_usd DESC
    LIMIT 25
  ) t;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
  INTO v_tier_c
  FROM (
    SELECT
      s.oem_part_number AS oem,
      s.qty_sold,
      s.revenue_usd,
      COALESCE(sl.quantity, 0)::numeric AS on_hand
    FROM public.inventory_abc_snapshots s
    LEFT JOIN public.stock_levels sl
      ON sl.stock_item_id = s.stock_item_id AND sl.warehouse_id = p_warehouse_id
    WHERE s.as_of = v_latest
      AND s.abc_class = 'C'
      AND COALESCE(sl.quantity, 0) > 0
    ORDER BY COALESCE(sl.quantity, 0) DESC, s.revenue_usd ASC
    LIMIT 25
  ) t;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
  INTO v_suggestions
  FROM (
    SELECT
      fs.id,
      si.oem_part_number AS oem,
      fs.suggested_qty,
      fs.on_hand,
      fs.horizon_days,
      fs.reason,
      fs.status::text
    FROM public.forecast_suggestions fs
    JOIN public.stock_items si ON si.id = fs.stock_item_id
    WHERE fs.warehouse_id = p_warehouse_id
      AND fs.status = 'open'
    ORDER BY fs.created_at DESC
    LIMIT 50
  ) t;

  SELECT COALESCE(SUM(sl.quantity), 0) INTO v_on_hand
  FROM public.stock_levels sl
  WHERE sl.warehouse_id = p_warehouse_id;

  SELECT COUNT(*)::bigint INTO v_low_stock
  FROM public.stock_items si
  LEFT JOIN public.stock_levels sl
    ON sl.stock_item_id = si.id AND sl.warehouse_id = p_warehouse_id
  WHERE si.reorder_point IS NOT NULL
    AND COALESCE(sl.quantity, 0) <= si.reorder_point;

  RETURN jsonb_build_object(
    'kpi_set', 'stores_forecast_v1',
    'warehouse_id', p_warehouse_id,
    'period_from', p_from,
    'period_to', p_to,
    'abc_rows_written', v_abc_count,
    'abc_as_of', v_latest,
    'abc_class_counts', v_class_counts,
    'tier_a_fast_movers', v_tier_a,
    'tier_c_slow_movers', v_tier_c,
    'open_forecast_suggestions', v_suggestions,
    'warehouse_inventory', jsonb_build_object(
      'on_hand_qty', v_on_hand,
      'low_stock_count', v_low_stock
    )
  );
END;
$$;

-- ---------------------------------------------------------------------------
-- CRM promo candidates (opt-in only; limited PII for copy)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.list_crm_promo_candidates(
  p_limit INT DEFAULT 25,
  p_force BOOLEAN DEFAULT false
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_settings public.ai_promo_settings%ROWTYPE;
  v_limit INT := GREATEST(1, LEAST(COALESCE(p_limit, 25), 100));
  v_fallback TEXT[];
  v_rows JSONB := '[]'::jsonb;
BEGIN
  IF auth.role() <> 'service_role'
     AND NOT public.has_staff_role(ARRAY['admin', 'sales']::public.staff_role[])
  THEN
    RAISE EXCEPTION 'sales or admin role required';
  END IF;

  SELECT * INTO v_settings
  FROM public.ai_promo_settings
  ORDER BY updated_at DESC
  LIMIT 1;

  IF v_settings.id IS NULL OR NOT v_settings.active THEN
    RETURN jsonb_build_object(
      'settings_active', false,
      'candidates', '[]'::jsonb
    );
  END IF;

  SELECT COALESCE(array_agg(oem ORDER BY rn), '{}'::text[])
  INTO v_fallback
  FROM (
    SELECT
      si.oem_part_number AS oem,
      ROW_NUMBER() OVER (ORDER BY SUM(sil.qty_base) DESC) AS rn
    FROM public.sales_invoice_lines sil
    JOIN public.sales_invoices inv ON inv.id = sil.invoice_id
    JOIN public.stock_items si ON si.id = sil.stock_item_id
    WHERE inv.doc_type = 'invoice'
      AND inv.status = 'posted'
      AND inv.posted_at >= now() - INTERVAL '90 days'
      AND NOT sil.is_core_charge
    GROUP BY si.oem_part_number
    ORDER BY SUM(sil.qty_base) DESC
    LIMIT v_settings.max_skus_per_message
  ) x;

  SELECT COALESCE(jsonb_agg(row_to_json(t)::jsonb), '[]'::jsonb)
  INTO v_rows
  FROM (
    SELECT
      c.id AS customer_id,
      c.display_name,
      c.email,
      c.phone_e164,
      c.whatsapp_e164,
      lp.last_purchase_at,
      c.last_promotional_message_at,
      gv.vehicle_label,
      gv.make,
      gv.model,
      gv.engine,
      COALESCE(
        (
          SELECT CASE
            WHEN cardinality(arr) > 0 THEN arr
            ELSE v_fallback
          END
          FROM (
            SELECT COALESCE(array_agg(oem), '{}'::text[]) AS arr
            FROM (
              SELECT DISTINCT si.oem_part_number AS oem
              FROM public.part_fitment pf
              JOIN public.stock_items si ON si.oem_part_number = pf.oem_part_number
              WHERE (
                  gv.model IS NOT NULL
                  AND pf.chassis_code IS NOT NULL
                  AND pf.chassis_code ILIKE '%' || gv.model || '%'
                )
                OR (
                  gv.engine IS NOT NULL
                  AND pf.engine_code IS NOT NULL
                  AND pf.engine_code ILIKE '%' || gv.engine || '%'
                )
              LIMIT v_settings.max_skus_per_message
            ) oems
          ) wrapped
        ),
        v_fallback
      ) AS oem_skus
    FROM public.customers c
    JOIN LATERAL (
      SELECT MAX(inv.posted_at) AS last_purchase_at
      FROM public.sales_invoices inv
      WHERE inv.customer_id = c.id
        AND inv.doc_type = 'invoice'
        AND inv.status = 'posted'
    ) lp ON lp.last_purchase_at IS NOT NULL
    LEFT JOIN LATERAL (
      SELECT
        g.make,
        g.model,
        g.engine,
        trim(both ' ' FROM concat_ws(
          ' ',
          NULLIF(g.make, ''),
          NULLIF(g.model, ''),
          NULLIF(g.generation, ''),
          NULLIF(g.engine, '')
        )) AS vehicle_label
      FROM public.customer_garage_vehicles g
      WHERE g.customer_id = c.id
      ORDER BY g.is_primary DESC, g.updated_at DESC
      LIMIT 1
    ) gv ON true
    WHERE c.marketing_opt_in
      AND lp.last_purchase_at < now() - make_interval(days => v_settings.inactivity_days)
      AND (
        COALESCE(p_force, false)
        OR c.last_promotional_message_at IS NULL
        OR c.last_promotional_message_at
          < now() - make_interval(days => v_settings.cooldown_days)
      )
    ORDER BY lp.last_purchase_at ASC
    LIMIT v_limit
  ) t;

  RETURN jsonb_build_object(
    'settings_active', true,
    'inactivity_days', v_settings.inactivity_days,
    'cooldown_days', v_settings.cooldown_days,
    'max_skus_per_message', v_settings.max_skus_per_message,
    'channels', to_jsonb(v_settings.channels),
    'include_llm_copy', v_settings.include_llm_copy,
    'candidates', COALESCE(v_rows, '[]'::jsonb)
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.insert_ai_promo_run()
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
  INSERT INTO public.ai_promo_runs (status, started_at)
  VALUES ('running', now())
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.finalize_ai_promo_run(
  p_run_id UUID,
  p_status public.ai_promo_run_status,
  p_candidates INT,
  p_queued INT,
  p_gemini_used BOOLEAN,
  p_error TEXT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.role() <> 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;
  UPDATE public.ai_promo_runs
  SET
    status = p_status,
    candidates_considered = COALESCE(p_candidates, 0),
    messages_queued = COALESCE(p_queued, 0),
    gemini_used = COALESCE(p_gemini_used, false),
    error = p_error,
    finished_at = now()
  WHERE id = p_run_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.insert_ai_promo_delivery(
  p_run_id UUID,
  p_customer_id UUID,
  p_channel public.ai_delivery_channel,
  p_recipient TEXT,
  p_status public.ai_delivery_status,
  p_body_preview TEXT,
  p_oem_skus TEXT[],
  p_vehicle_label TEXT,
  p_provider_ref TEXT DEFAULT NULL,
  p_error TEXT DEFAULT NULL,
  p_gemini_used BOOLEAN DEFAULT false
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
  INSERT INTO public.ai_promo_deliveries (
    run_id, customer_id, channel, recipient, status, body_preview,
    oem_skus, vehicle_label, provider_ref, error, gemini_used, sent_at
  ) VALUES (
    p_run_id, p_customer_id, p_channel, p_recipient, p_status, p_body_preview,
    COALESCE(p_oem_skus, '{}'::text[]), p_vehicle_label, p_provider_ref, p_error,
    COALESCE(p_gemini_used, false),
    CASE WHEN p_status = 'sent' THEN now() ELSE NULL END
  )
  RETURNING id INTO v_id;

  IF p_status = 'sent' THEN
    UPDATE public.customers
    SET last_promotional_message_at = now(), updated_at = now()
    WHERE id = p_customer_id;
  END IF;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.insert_inventory_ai_directive(
  p_warehouse_id UUID,
  p_period_start TIMESTAMPTZ,
  p_period_end TIMESTAMPTZ,
  p_kpi_json JSONB,
  p_directives JSONB,
  p_narrative TEXT,
  p_gemini_used BOOLEAN,
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
  PERFORM public._assert_ai_stores_staff();
  INSERT INTO public.inventory_ai_directives (
    warehouse_id, period_start, period_end, kpi_json, directives,
    narrative, gemini_used, error, created_by
  ) VALUES (
    p_warehouse_id, p_period_start, p_period_end,
    COALESCE(p_kpi_json, '{}'::jsonb),
    COALESCE(p_directives, '[]'::jsonb),
    p_narrative,
    COALESCE(p_gemini_used, false),
    p_error,
    auth.uid()
  )
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$$;

-- Grants
REVOKE ALL ON FUNCTION public._assert_ai_finance_staff() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._assert_ai_stores_staff() FROM PUBLIC;
REVOKE ALL ON FUNCTION public._assert_ai_crm_staff() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.kpi_finance_performance_v1(TIMESTAMPTZ, TIMESTAMPTZ, INT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.run_inventory_abc_classification(TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.kpi_stores_forecast_v1(UUID, TIMESTAMPTZ, TIMESTAMPTZ, BOOLEAN) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_crm_promo_candidates(INT, BOOLEAN) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.insert_ai_promo_run() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.finalize_ai_promo_run(UUID, public.ai_promo_run_status, INT, INT, BOOLEAN, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.insert_ai_promo_delivery(UUID, UUID, public.ai_delivery_channel, TEXT, public.ai_delivery_status, TEXT, TEXT[], TEXT, TEXT, TEXT, BOOLEAN) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.insert_inventory_ai_directive(UUID, TIMESTAMPTZ, TIMESTAMPTZ, JSONB, JSONB, TEXT, BOOLEAN, TEXT) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.kpi_finance_performance_v1(TIMESTAMPTZ, TIMESTAMPTZ, INT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.run_inventory_abc_classification(TIMESTAMPTZ, TIMESTAMPTZ)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.kpi_stores_forecast_v1(UUID, TIMESTAMPTZ, TIMESTAMPTZ, BOOLEAN)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_crm_promo_candidates(INT, BOOLEAN)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.insert_ai_promo_run() TO service_role;
GRANT EXECUTE ON FUNCTION public.finalize_ai_promo_run(UUID, public.ai_promo_run_status, INT, INT, BOOLEAN, TEXT)
  TO service_role;
GRANT EXECUTE ON FUNCTION public.insert_ai_promo_delivery(UUID, UUID, public.ai_delivery_channel, TEXT, public.ai_delivery_status, TEXT, TEXT[], TEXT, TEXT, TEXT, BOOLEAN)
  TO service_role;
GRANT EXECUTE ON FUNCTION public.insert_inventory_ai_directive(UUID, TIMESTAMPTZ, TIMESTAMPTZ, JSONB, JSONB, TEXT, BOOLEAN, TEXT)
  TO authenticated, service_role;
