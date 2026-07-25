CREATE OR REPLACE FUNCTION public._can_select_delivery_pod_object(p_name TEXT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    auth.role() = 'service_role'
    OR (
      public.has_staff_role(
        ARRAY['admin', 'warehouse', 'dispatcher']::public.staff_role[]
      )
      AND public._delivery_pod_job_id_from_path(p_name) IS NOT NULL
    )
    OR EXISTS (
      SELECT 1
      FROM public.delivery_jobs dj
      WHERE dj.id = public._delivery_pod_job_id_from_path(p_name)
        AND dj.assignee_user_id = auth.uid()
        AND public.has_staff_role(ARRAY['driver']::public.staff_role[])
    );
$$;
