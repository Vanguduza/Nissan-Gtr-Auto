-- Backfill active employees for seed staff auth users.
-- resolve_staff_login_email requires employees.status = 'active' AND user_id set;
-- seed historically created auth.users + staff_roles only (no employees rows).
-- Idempotent: match by auth email; skip when user already has an employee.

INSERT INTO public.employees (
  user_id,
  employee_code,
  full_name,
  email,
  hire_date,
  status
)
SELECT
  u.id,
  v.employee_code,
  COALESCE(NULLIF(trim(p.full_name), ''), v.full_name),
  lower(u.email),
  CURRENT_DATE,
  'active'::public.employee_status
FROM (
  VALUES
    ('admin@gtr.local', 'SEED-ADMIN', 'Local Admin'),
    ('finance@gtr.local', 'SEED-FINANCE', 'Local Finance'),
    ('warehouse@gtr.local', 'SEED-WAREHOUSE', 'Local Warehouse')
) AS v(email, employee_code, full_name)
JOIN auth.users u ON lower(u.email) = v.email
JOIN public.profiles p ON p.id = u.id
WHERE NOT EXISTS (
  SELECT 1 FROM public.employees e WHERE e.user_id = u.id
)
  AND NOT EXISTS (
    SELECT 1 FROM public.employees e WHERE lower(e.employee_code) = lower(v.employee_code)
  );
