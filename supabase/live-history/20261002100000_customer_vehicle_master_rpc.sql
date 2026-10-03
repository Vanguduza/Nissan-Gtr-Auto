-- Exported from the hosted project's supabase_migrations.schema_migrations (20261002100000 customer_vehicle_master_rpc).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Vehicle selector master for every app (web, Android customer, iOS, POS web and tablet).
--
-- The published Nissan catalogue routes by vehicle: `catalog_r2_vehicle_master` holds one row per
-- published vehicle (its `r2_scope_key` is the R2 serving-shard scope), and the `catalog-live-r2`
-- gateway answers customer-stock / customer-search / staff-sections for that key. The apps load the
-- selector through `list_customer_vehicle_master`, which the hosted project did not have, so every
-- vehicle picker failed. This adds it over the routing table: the returned `id` is the gateway
-- `vehicle_id`.
--
-- The routing table is created here only where it does not exist yet (local `supabase db reset`);
-- the hosted project already has it, filled by the catalogue release. RLS stays on with no
-- policies: clients read it only through this function, which exposes the non-sensitive selector
-- columns.

CREATE TABLE IF NOT EXISTS public.catalog_r2_vehicle_master (
  r2_scope_key TEXT PRIMARY KEY,
  maker_slug TEXT NOT NULL,
  model TEXT NOT NULL,
  chassis_code TEXT NOT NULL,
  engine_code TEXT NOT NULL,
  year_start INTEGER,
  year_end INTEGER,
  sales_region TEXT,
  source_release_version TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.catalog_r2_vehicle_master ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.list_customer_vehicle_master(
  p_maker TEXT DEFAULT 'nissan',
  p_limit INTEGER DEFAULT 1000,
  p_offset INTEGER DEFAULT 0
)
RETURNS TABLE (
  id TEXT,
  make TEXT,
  model_family TEXT,
  model_variant TEXT,
  chassis_code TEXT,
  engine_code TEXT,
  production_year INTEGER,
  year_start INTEGER,
  year_end INTEGER,
  sales_region TEXT,
  display_name TEXT,
  vin_prefix TEXT
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
  SELECT
    v.r2_scope_key,
    'Nissan'::TEXT,
    v.model,
    'Nissan ' || v.model,
    v.chassis_code,
    v.engine_code,
    v.year_start,
    v.year_start,
    v.year_end,
    v.sales_region,
    'Nissan ' || v.model || ' · ' || concat_ws(' · ',
      v.chassis_code,
      v.engine_code,
      CASE WHEN v.year_start IS NOT NULL OR v.year_end IS NOT NULL
        THEN concat(v.year_start, '–', v.year_end) END,
      v.sales_region),
    NULL::TEXT
  FROM public.catalog_r2_vehicle_master v
  WHERE v.maker_slug = lower(trim(COALESCE(p_maker, 'nissan')))
  ORDER BY v.model, v.year_start NULLS LAST, v.chassis_code, v.engine_code
  LIMIT GREATEST(1, LEAST(COALESCE(p_limit, 1000), 10000))
  OFFSET GREATEST(0, COALESCE(p_offset, 0));
$$;

COMMENT ON FUNCTION public.list_customer_vehicle_master(TEXT, INTEGER, INTEGER) IS
  'Vehicle selector rows from the published catalogue routing table; id = catalog-live-r2 vehicle_id.';

REVOKE ALL ON FUNCTION public.list_customer_vehicle_master(TEXT, INTEGER, INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_customer_vehicle_master(TEXT, INTEGER, INTEGER) TO anon, authenticated, service_role;
