-- Smoke: AI autonomous layer Phase A objects exist; no ZIMRA surface.
-- Run after 20260803150000_ai_autonomous_crm_stores_finance.sql

DO $$
BEGIN
  IF to_regclass('public.ai_promo_settings') IS NULL
     OR to_regclass('public.ai_promo_runs') IS NULL
     OR to_regclass('public.ai_promo_deliveries') IS NULL
     OR to_regclass('public.inventory_abc_snapshots') IS NULL
     OR to_regclass('public.inventory_ai_directives') IS NULL
  THEN
    RAISE EXCEPTION 'smoke fail: AI autonomous tables missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'customers'
      AND column_name = 'marketing_opt_in'
  ) THEN
    RAISE EXCEPTION 'smoke fail: customers.marketing_opt_in missing';
  END IF;

  IF to_regprocedure('public.kpi_finance_performance_v1(timestamptz,timestamptz,int)') IS NULL
     OR to_regprocedure('public.kpi_stores_forecast_v1(uuid,timestamptz,timestamptz,boolean)') IS NULL
     OR to_regprocedure('public.list_crm_promo_candidates(int,boolean)') IS NULL
     OR to_regprocedure('public.run_inventory_abc_classification(timestamptz,timestamptz)') IS NULL
  THEN
    RAISE EXCEPTION 'smoke fail: AI autonomous RPCs missing';
  END IF;

  -- Hard exclusions: no fiscal / payroll-tax AI tables
  IF to_regclass('public.zimra_devices') IS NOT NULL
     OR to_regclass('public.fdms_payloads') IS NOT NULL
     OR to_regclass('public.paye_brackets') IS NOT NULL
  THEN
    RAISE EXCEPTION 'smoke fail: excluded ZIMRA/PAYE tables must not exist';
  END IF;

  RAISE NOTICE 'ai_autonomous_layer_smoke OK';
END;
$$;
