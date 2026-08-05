-- Batch 1 §2.1 / §2.2 — HR grades + organogram roles (module_access → future STAFF_NAV gates).
-- No payroll tax. Role delete blocked while employees hold the role (history preserved).

CREATE TABLE IF NOT EXISTS public.hr_grades (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code TEXT NOT NULL UNIQUE,
  title TEXT NOT NULL,
  sort_order INT NOT NULL DEFAULT 100,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT hr_grades_code_format CHECK (code ~ '^[A-Z][0-9]+$')
);

COMMENT ON TABLE public.hr_grades IS
  'Admin-editable seniority bands (A1 CEO, B1 managers, C1 attendants, …).';

CREATE TABLE IF NOT EXISTS public.hr_contract_clause_templates (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code TEXT NOT NULL UNIQUE,
  title TEXT NOT NULL,
  body_md TEXT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TYPE public.hr_pay_frequency AS ENUM (
  'weekly',
  'fortnightly',
  'monthly'
);

CREATE TABLE IF NOT EXISTS public.hr_roles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  parent_role_id UUID REFERENCES public.hr_roles (id) ON DELETE RESTRICT,
  title TEXT NOT NULL,
  department TEXT,
  grade_id UUID NOT NULL REFERENCES public.hr_grades (id),
  duties_md TEXT,
  remuneration_notes TEXT,
  pay_frequency public.hr_pay_frequency NOT NULL DEFAULT 'monthly',
  module_access JSONB NOT NULL DEFAULT '[]'::jsonb,
  comms_preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
  clause_template_ids UUID[] NOT NULL DEFAULT '{}',
  is_active BOOLEAN NOT NULL DEFAULT true,
  archived_at TIMESTAMPTZ,
  created_by UUID REFERENCES auth.users (id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS hr_roles_parent_idx ON public.hr_roles (parent_role_id);
CREATE INDEX IF NOT EXISTS hr_roles_grade_idx ON public.hr_roles (grade_id);
CREATE INDEX IF NOT EXISTS hr_roles_active_idx ON public.hr_roles (is_active) WHERE is_active;

ALTER TABLE public.employees
  ADD COLUMN IF NOT EXISTS hr_role_id UUID REFERENCES public.hr_roles (id) ON DELETE RESTRICT,
  ADD COLUMN IF NOT EXISTS grade_id UUID REFERENCES public.hr_grades (id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS employees_hr_role_idx ON public.employees (hr_role_id);

-- Seed confirmed grades from brief (§2.1)
INSERT INTO public.hr_grades (code, title, sort_order) VALUES
  ('A1', 'CEO', 10),
  ('A2', 'Director', 20),
  ('B1', 'Shop & warehouse manager', 30),
  ('C1', 'Shop attendant', 40),
  ('C2', 'Delivery personnel', 50)
ON CONFLICT (code) DO UPDATE
SET title = EXCLUDED.title, sort_order = EXCLUDED.sort_order, is_active = true, updated_at = now();

ALTER TABLE public.hr_grades ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.hr_contract_clause_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.hr_roles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS hr_grades_select ON public.hr_grades;
CREATE POLICY hr_grades_select ON public.hr_grades
  FOR SELECT TO authenticated
  USING (
    public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
  );

DROP POLICY IF EXISTS hr_grades_write ON public.hr_grades;
CREATE POLICY hr_grades_write ON public.hr_grades
  FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin']::public.staff_role[]));

DROP POLICY IF EXISTS hr_clause_select ON public.hr_contract_clause_templates;
CREATE POLICY hr_clause_select ON public.hr_contract_clause_templates
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

DROP POLICY IF EXISTS hr_clause_write ON public.hr_contract_clause_templates;
CREATE POLICY hr_clause_write ON public.hr_contract_clause_templates
  FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

DROP POLICY IF EXISTS hr_roles_select ON public.hr_roles;
CREATE POLICY hr_roles_select ON public.hr_roles
  FOR SELECT TO authenticated
  USING (public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]));

DROP POLICY IF EXISTS hr_roles_write ON public.hr_roles;
CREATE POLICY hr_roles_write ON public.hr_roles
  FOR ALL TO authenticated
  USING (public.has_staff_role(ARRAY['admin']::public.staff_role[]))
  WITH CHECK (public.has_staff_role(ARRAY['admin']::public.staff_role[]));

GRANT SELECT ON TABLE public.hr_grades TO authenticated;
GRANT ALL ON TABLE public.hr_grades TO service_role;
GRANT SELECT ON TABLE public.hr_contract_clause_templates TO authenticated;
GRANT ALL ON TABLE public.hr_contract_clause_templates TO service_role;
GRANT SELECT ON TABLE public.hr_roles TO authenticated;
GRANT ALL ON TABLE public.hr_roles TO service_role;

-- High-level official (admin) creates a role in the organogram
CREATE OR REPLACE FUNCTION public.create_hr_role(
  p_title TEXT,
  p_grade_id UUID,
  p_parent_role_id UUID DEFAULT NULL,
  p_department TEXT DEFAULT NULL,
  p_duties_md TEXT DEFAULT NULL,
  p_remuneration_notes TEXT DEFAULT NULL,
  p_pay_frequency public.hr_pay_frequency DEFAULT 'monthly',
  p_module_access JSONB DEFAULT '[]'::jsonb,
  p_comms_preferences JSONB DEFAULT '{}'::jsonb,
  p_clause_template_ids UUID[] DEFAULT '{}'
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin role required to create organogram roles';
  END IF;
  IF p_title IS NULL OR length(trim(p_title)) = 0 THEN
    RAISE EXCEPTION 'title required';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.hr_grades g WHERE g.id = p_grade_id AND g.is_active) THEN
    RAISE EXCEPTION 'active grade required';
  END IF;
  IF p_parent_role_id IS NOT NULL
     AND NOT EXISTS (SELECT 1 FROM public.hr_roles r WHERE r.id = p_parent_role_id AND r.is_active) THEN
    RAISE EXCEPTION 'parent role not found or inactive';
  END IF;

  INSERT INTO public.hr_roles (
    parent_role_id, title, department, grade_id, duties_md, remuneration_notes,
    pay_frequency, module_access, comms_preferences, clause_template_ids, created_by
  )
  VALUES (
    p_parent_role_id, trim(p_title), p_department, p_grade_id, p_duties_md, p_remuneration_notes,
    COALESCE(p_pay_frequency, 'monthly'),
    COALESCE(p_module_access, '[]'::jsonb),
    COALESCE(p_comms_preferences, '{}'::jsonb),
    COALESCE(p_clause_template_ids, '{}'),
    auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.archive_hr_role(p_role_id UUID)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_held INT;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin role required to archive organogram roles';
  END IF;

  SELECT COUNT(*)::int INTO v_held
  FROM public.employees e
  WHERE e.hr_role_id = p_role_id AND e.status = 'active';

  IF v_held > 0 THEN
    RAISE EXCEPTION 'reassign % active employee(s) before archiving this role', v_held;
  END IF;

  IF EXISTS (SELECT 1 FROM public.hr_roles c WHERE c.parent_role_id = p_role_id AND c.is_active) THEN
    RAISE EXCEPTION 'archive or reparent child roles first';
  END IF;

  UPDATE public.hr_roles
  SET is_active = false, archived_at = now(), updated_at = now()
  WHERE id = p_role_id;

  RETURN p_role_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_hr_grade(
  p_code TEXT,
  p_title TEXT,
  p_sort_order INT DEFAULT 100
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_code TEXT;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin role required to create grades';
  END IF;
  v_code := upper(trim(p_code));
  IF v_code !~ '^[A-Z][0-9]+$' THEN
    RAISE EXCEPTION 'grade code must look like A1, B2, C3';
  END IF;

  INSERT INTO public.hr_grades (code, title, sort_order)
  VALUES (v_code, trim(p_title), COALESCE(p_sort_order, 100))
  ON CONFLICT (code) DO UPDATE
  SET title = EXCLUDED.title, sort_order = EXCLUDED.sort_order, is_active = true, updated_at = now()
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public.create_hr_role(
  TEXT, UUID, UUID, TEXT, TEXT, TEXT, public.hr_pay_frequency, JSONB, JSONB, UUID[]
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_hr_role(
  TEXT, UUID, UUID, TEXT, TEXT, TEXT, public.hr_pay_frequency, JSONB, JSONB, UUID[]
) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.archive_hr_role(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.archive_hr_role(UUID) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.create_hr_grade(TEXT, TEXT, INT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_hr_grade(TEXT, TEXT, INT) TO authenticated, service_role;
