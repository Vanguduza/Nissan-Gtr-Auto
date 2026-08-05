-- AI ops Phase B: worker schedule metadata (mirror analytics subscription cadences).
-- External / pg_cron callers use documented Edge URLs + x-worker-secret.
-- No ZIMRA. Does not auto-invoke Edge from SQL (pg_net optional ops).

CREATE TABLE IF NOT EXISTS public.ai_worker_schedules (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  worker_key TEXT NOT NULL UNIQUE,
  cadence TEXT NOT NULL CHECK (cadence IN ('hourly', 'daily', 'weekly', 'monthly')),
  cron_expr TEXT NOT NULL,
  edge_path TEXT NOT NULL,
  body_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  is_active BOOLEAN NOT NULL DEFAULT true,
  last_run_at TIMESTAMPTZ,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE public.ai_worker_schedules IS
  'Cadence registry for process-crm-promos / process-ai-reports — ops cron mirrors these rows.';

INSERT INTO public.ai_worker_schedules (worker_key, cadence, cron_expr, edge_path, body_json, notes)
VALUES
  (
    'process-crm-promos-daily',
    'daily',
    '0 7 * * *',
    '/functions/v1/process-crm-promos',
    '{"limit":25,"force":false}'::jsonb,
    'Opt-in CRM promos; respects marketing_opt_in + cooldown'
  ),
  (
    'process-ai-reports-daily',
    'daily',
    '0 6 * * *',
    '/functions/v1/process-ai-reports',
    '{"cadence":"daily"}'::jsonb,
    'Analytics subscriptions due daily'
  ),
  (
    'process-ai-reports-weekly',
    'weekly',
    '0 6 * * 1',
    '/functions/v1/process-ai-reports',
    '{"cadence":"weekly"}'::jsonb,
    'Analytics subscriptions due weekly (Mondays)'
  ),
  (
    'process-ai-reports-monthly',
    'monthly',
    '0 6 1 * *',
    '/functions/v1/process-ai-reports',
    '{"cadence":"monthly"}'::jsonb,
    'Analytics subscriptions due monthly (1st)'
  )
ON CONFLICT (worker_key) DO UPDATE
SET
  cron_expr = EXCLUDED.cron_expr,
  edge_path = EXCLUDED.edge_path,
  body_json = EXCLUDED.body_json,
  notes = EXCLUDED.notes,
  is_active = true,
  updated_at = now();

CREATE OR REPLACE FUNCTION public.touch_ai_worker_schedule(p_worker_key TEXT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF auth.role() <> 'service_role'
     AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin or service_role required';
  END IF;
  UPDATE public.ai_worker_schedules
  SET last_run_at = now(), updated_at = now()
  WHERE worker_key = p_worker_key;
END;
$$;

ALTER TABLE public.ai_worker_schedules ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS ai_worker_schedules_select ON public.ai_worker_schedules;
CREATE POLICY ai_worker_schedules_select ON public.ai_worker_schedules
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'finance', 'sales']::public.staff_role[]));

DROP POLICY IF EXISTS ai_worker_schedules_write ON public.ai_worker_schedules;
CREATE POLICY ai_worker_schedules_write ON public.ai_worker_schedules
  FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin']::public.staff_role[]));

REVOKE ALL ON FUNCTION public.touch_ai_worker_schedule(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.touch_ai_worker_schedule(TEXT)
  TO authenticated, service_role;
GRANT SELECT ON TABLE public.ai_worker_schedules TO authenticated;
GRANT ALL ON TABLE public.ai_worker_schedules TO service_role;
