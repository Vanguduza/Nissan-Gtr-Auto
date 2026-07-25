-- Harden ai_report_subscriptions mutation RLS: admin|finance write; sales read-only.
-- KPI role set (admin|finance|sales) unchanged.

REVOKE ALL ON TABLE public.ai_report_subscriptions FROM PUBLIC, anon;

DROP POLICY IF EXISTS ai_report_subscriptions_staff_insert
  ON public.ai_report_subscriptions;
DROP POLICY IF EXISTS ai_report_subscriptions_staff_update
  ON public.ai_report_subscriptions;
DROP POLICY IF EXISTS ai_report_subscriptions_staff_delete
  ON public.ai_report_subscriptions;

-- SELECT unchanged: admin | finance | sales (policy from 20260725090000)

CREATE POLICY ai_report_subscriptions_admin_finance_insert
  ON public.ai_report_subscriptions FOR INSERT TO authenticated
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
    AND (created_by IS NULL OR created_by = auth.uid())
  );

CREATE POLICY ai_report_subscriptions_admin_finance_update
  ON public.ai_report_subscriptions FOR UPDATE TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  )
  WITH CHECK (
    public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  );

CREATE POLICY ai_report_subscriptions_admin_finance_delete
  ON public.ai_report_subscriptions FOR DELETE TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'finance']::public.staff_role[])
  );
