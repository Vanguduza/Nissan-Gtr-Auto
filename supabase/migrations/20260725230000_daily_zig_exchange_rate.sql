-- Daily ZiG exchange rate (ZiG per 1 USD). Set by finance/admin; readable by storefront.
-- No ZIMRA / tax. Ledger still stores explicit currency + exchange_rate_applied per txn.

CREATE TABLE IF NOT EXISTS public.daily_exchange_rates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  currency public.currency_code NOT NULL DEFAULT 'ZIG',
  rate_date DATE NOT NULL DEFAULT (CURRENT_DATE),
  -- How many ZiG equal 1 USD (USD × rate = ZiG).
  rate NUMERIC(18, 8) NOT NULL,
  notes TEXT,
  set_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT daily_exchange_rates_rate_positive CHECK (rate > 0),
  CONSTRAINT daily_exchange_rates_zig_only CHECK (currency = 'ZIG'),
  UNIQUE (currency, rate_date)
);

CREATE INDEX IF NOT EXISTS daily_exchange_rates_date_idx
  ON public.daily_exchange_rates (rate_date DESC);

COMMENT ON TABLE public.daily_exchange_rates IS
  'Official daily ZiG/USD rate for storefront settlement display and payment intents. Catalog prices remain USD.';

ALTER TABLE public.daily_exchange_rates ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS daily_exchange_rates_select_authenticated
  ON public.daily_exchange_rates;
CREATE POLICY daily_exchange_rates_select_authenticated
  ON public.daily_exchange_rates FOR SELECT TO authenticated
  USING (true);

DROP POLICY IF EXISTS daily_exchange_rates_select_anon
  ON public.daily_exchange_rates;
CREATE POLICY daily_exchange_rates_select_anon
  ON public.daily_exchange_rates FOR SELECT TO anon
  USING (true);

-- Mutations only via SECURITY DEFINER RPCs
REVOKE INSERT, UPDATE, DELETE ON TABLE public.daily_exchange_rates FROM PUBLIC;
REVOKE INSERT, UPDATE, DELETE ON TABLE public.daily_exchange_rates FROM anon, authenticated;
GRANT SELECT ON TABLE public.daily_exchange_rates TO anon, authenticated;
GRANT ALL ON TABLE public.daily_exchange_rates TO service_role;

CREATE OR REPLACE FUNCTION public.get_zig_exchange_rate(
  p_as_of DATE DEFAULT CURRENT_DATE
)
RETURNS NUMERIC
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_rate NUMERIC;
BEGIN
  SELECT r.rate INTO v_rate
  FROM public.daily_exchange_rates r
  WHERE r.currency = 'ZIG'
    AND r.rate_date <= COALESCE(p_as_of, CURRENT_DATE)
  ORDER BY r.rate_date DESC
  LIMIT 1;

  IF v_rate IS NULL OR v_rate <= 0 THEN
    RETURN NULL;
  END IF;
  RETURN v_rate;
END;
$$;

CREATE OR REPLACE FUNCTION public.set_zig_exchange_rate(
  p_rate NUMERIC,
  p_rate_date DATE DEFAULT CURRENT_DATE,
  p_notes TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_date DATE := COALESCE(p_rate_date, CURRENT_DATE);
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  IF p_rate IS NULL OR p_rate <= 0 THEN
    RAISE EXCEPTION 'rate must be > 0 (ZiG per 1 USD)';
  END IF;

  INSERT INTO public.daily_exchange_rates (
    currency, rate_date, rate, notes, set_by
  )
  VALUES (
    'ZIG', v_date, p_rate, nullif(trim(p_notes), ''), auth.uid()
  )
  ON CONFLICT (currency, rate_date) DO UPDATE
  SET
    rate = EXCLUDED.rate,
    notes = EXCLUDED.notes,
    set_by = EXCLUDED.set_by,
    created_at = now()
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.list_zig_exchange_rates(
  p_limit INTEGER DEFAULT 30
)
RETURNS TABLE (
  id UUID,
  rate_date DATE,
  rate NUMERIC,
  notes TEXT,
  set_by UUID,
  created_at TIMESTAMPTZ
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_lim INTEGER := greatest(1, least(COALESCE(p_limit, 30), 100));
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'finance or admin role required';
  END IF;

  RETURN QUERY
  SELECT r.id, r.rate_date, r.rate, r.notes, r.set_by, r.created_at
  FROM public.daily_exchange_rates r
  WHERE r.currency = 'ZIG'
  ORDER BY r.rate_date DESC, r.created_at DESC
  LIMIT v_lim;
END;
$$;

REVOKE ALL ON FUNCTION public.get_zig_exchange_rate(DATE) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.set_zig_exchange_rate(NUMERIC, DATE, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_zig_exchange_rates(INTEGER) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.get_zig_exchange_rate(DATE)
  TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.set_zig_exchange_rate(NUMERIC, DATE, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_zig_exchange_rates(INTEGER)
  TO authenticated, service_role;

-- Seed today's rate from common local default if none exists (dev convenience).
INSERT INTO public.daily_exchange_rates (currency, rate_date, rate, notes)
SELECT 'ZIG', CURRENT_DATE, 1, 'Initial seed — replace via Finance → Exchange rate'
WHERE NOT EXISTS (
  SELECT 1 FROM public.daily_exchange_rates WHERE currency = 'ZIG'
);
