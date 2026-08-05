-- Batch 1 §1.6 — staff can read own organogram module_access (hr_roles RLS is HR/admin only).

CREATE OR REPLACE FUNCTION public.my_module_access()
RETURNS JSONB
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT COALESCE(r.module_access, '[]'::jsonb)
  FROM public.employees e
  JOIN public.hr_roles r ON r.id = e.hr_role_id
  WHERE e.user_id = auth.uid()
  LIMIT 1;
$$;

COMMENT ON FUNCTION public.my_module_access() IS
  'Returns hr_roles.module_access for the signed-in employee; empty array if none.';

REVOKE ALL ON FUNCTION public.my_module_access() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.my_module_access() TO authenticated, service_role;
