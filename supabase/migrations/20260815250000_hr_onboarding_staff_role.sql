-- HR onboarding → coarse staff_roles assignment when Auth user is linked.
-- Safe mapping: explicit payload.staff_role / hr_roles.default_staff_role first;
-- heuristic never grants admin. Admin only when caller is admin (or service_role).
-- No ZIMRA / payroll-tax.

ALTER TABLE public.hr_roles
  ADD COLUMN IF NOT EXISTS default_staff_role public.staff_role;

COMMENT ON COLUMN public.hr_roles.default_staff_role IS
  'Optional coarse staff_role granted on onboarding auth link. Never auto-admin; '
  'admin requires explicit onboarding selection by an admin caller.';

-- Parse text → staff_role enum (null if blank/invalid).
CREATE OR REPLACE FUNCTION public._parse_staff_role_text(p_raw TEXT)
RETURNS public.staff_role
LANGUAGE plpgsql
IMMUTABLE
SET search_path = public
AS $$
DECLARE
  v TEXT := lower(trim(COALESCE(p_raw, '')));
BEGIN
  IF v = '' THEN
    RETURN NULL;
  END IF;
  CASE v
    WHEN 'admin' THEN RETURN 'admin'::public.staff_role;
    WHEN 'finance' THEN RETURN 'finance'::public.staff_role;
    WHEN 'warehouse' THEN RETURN 'warehouse'::public.staff_role;
    WHEN 'sales' THEN RETURN 'sales'::public.staff_role;
    WHEN 'dispatcher' THEN RETURN 'dispatcher'::public.staff_role;
    WHEN 'hr' THEN RETURN 'hr'::public.staff_role;
    WHEN 'driver' THEN RETURN 'driver'::public.staff_role;
    ELSE RETURN NULL;
  END CASE;
END;
$$;

-- Resolve coarse staff_role for onboarding (never invents admin via heuristic).
CREATE OR REPLACE FUNCTION public.resolve_hr_onboarding_staff_role(
  p_payload JSONB,
  p_hr_role_id UUID DEFAULT NULL
)
RETURNS public.staff_role
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_payload JSONB := COALESCE(p_payload, '{}'::jsonb);
  v_explicit public.staff_role;
  v_default public.staff_role;
  v_title TEXT;
  v_dept TEXT;
  v_modules TEXT;
  v_hay TEXT;
  v_role_id UUID;
BEGIN
  v_explicit := public._parse_staff_role_text(v_payload->>'staff_role');
  IF v_explicit IS NOT NULL THEN
    IF v_explicit = 'admin'::public.staff_role
       AND auth.role() IS DISTINCT FROM 'service_role'
       AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
      RAISE EXCEPTION 'admin staff_role requires an admin caller';
    END IF;
    RETURN v_explicit;
  END IF;

  v_role_id := COALESCE(
    p_hr_role_id,
    NULLIF(v_payload->>'hr_role_id', '')::uuid
  );

  IF v_role_id IS NOT NULL THEN
    SELECT r.default_staff_role, r.title, r.department, COALESCE(r.module_access::text, '')
    INTO v_default, v_title, v_dept, v_modules
    FROM public.hr_roles r
    WHERE r.id = v_role_id AND r.is_active;

    IF v_default IS NOT NULL THEN
      IF v_default = 'admin'::public.staff_role
         AND auth.role() IS DISTINCT FROM 'service_role'
         AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
        -- Skip unsafe organogram default; fall through to heuristic.
        NULL;
      ELSE
        RETURN v_default;
      END IF;
    END IF;
  ELSE
    v_title := NULL;
    v_dept := NULL;
    v_modules := '';
  END IF;

  v_hay := lower(
    trim(COALESCE(v_title, '')) || ' ' ||
    trim(COALESCE(v_dept, '')) || ' ' ||
    COALESCE(v_modules, '') || ' ' ||
    trim(COALESCE(v_payload->>'role_title', ''))
  );

  IF v_hay ~ '(driver|delivery|fleet|courier)' THEN
    RETURN 'driver'::public.staff_role;
  END IF;
  IF v_hay ~ '(warehouse|inventory|receiving|stock)' THEN
    RETURN 'warehouse'::public.staff_role;
  END IF;
  IF v_hay ~ '(finance|account|bookkeep|payroll)' THEN
    RETURN 'finance'::public.staff_role;
  END IF;
  IF v_hay ~ '(^|[^a-z])hr([^a-z]|$)|human.?resource|people.?ops' THEN
    RETURN 'hr'::public.staff_role;
  END IF;
  IF v_hay ~ '(dispatch)' THEN
    RETURN 'dispatcher'::public.staff_role;
  END IF;
  IF v_hay ~ '(sales|pos|till|counter|cashier|retail)' THEN
    RETURN 'sales'::public.staff_role;
  END IF;

  -- Safe shop-floor default — never admin.
  RETURN 'sales'::public.staff_role;
END;
$$;

COMMENT ON FUNCTION public.resolve_hr_onboarding_staff_role(JSONB, UUID) IS
  'Maps onboarding payload / organogram default / title heuristics → staff_role. '
  'Never grants admin via heuristic; admin only from explicit field + admin caller.';

-- HR/admin/service_role may grant a resolved onboarding role (admin grant gated).
CREATE OR REPLACE FUNCTION public.apply_hr_onboarding_staff_role(
  p_user_id UUID,
  p_role public.staff_role
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF p_user_id IS NULL OR p_role IS NULL THEN
    RAISE EXCEPTION 'user_id and role required';
  END IF;

  IF auth.role() IS DISTINCT FROM 'service_role'
     AND NOT public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]) THEN
    RAISE EXCEPTION 'hr or admin role required to apply onboarding staff role';
  END IF;

  IF p_role = 'admin'::public.staff_role
     AND auth.role() IS DISTINCT FROM 'service_role'
     AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin staff_role requires an admin caller';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = p_user_id) THEN
    RAISE EXCEPTION 'profile not found: %', p_user_id;
  END IF;

  INSERT INTO public.staff_roles (user_id, role)
  VALUES (p_user_id, p_role)
  ON CONFLICT (user_id, role) DO NOTHING;
END;
$$;

CREATE OR REPLACE FUNCTION public.complete_hr_onboarding(p_draft_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_draft public.hr_onboarding_drafts%ROWTYPE;
  v_payload JSONB;
  v_full_name TEXT;
  v_email TEXT;
  v_phone TEXT;
  v_grade_id UUID;
  v_grade_code TEXT;
  v_hr_role_id UUID;
  v_emp_id UUID;
  v_emp_code TEXT;
  v_user_id UUID;
  v_temp_hint TEXT;
  v_staff_role public.staff_role;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]) THEN
    RAISE EXCEPTION 'hr or admin role required';
  END IF;

  SELECT * INTO v_draft
  FROM public.hr_onboarding_drafts
  WHERE id = p_draft_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'onboarding draft not found: %', p_draft_id;
  END IF;
  IF v_draft.completed_at IS NOT NULL THEN
    RAISE EXCEPTION 'onboarding already completed';
  END IF;

  v_payload := COALESCE(v_draft.payload, '{}'::jsonb);
  v_full_name := nullif(trim(COALESCE(v_payload->>'full_name', '')), '');
  v_email := nullif(trim(COALESCE(v_payload->>'email', '')), '');
  v_phone := nullif(trim(COALESCE(v_payload->>'phone_e164', '')), '');
  v_grade_id := NULLIF(v_payload->>'grade_id', '')::uuid;
  v_hr_role_id := NULLIF(v_payload->>'hr_role_id', '')::uuid;
  v_user_id := NULLIF(v_payload->>'user_id', '')::uuid;

  IF v_full_name IS NULL THEN
    RAISE EXCEPTION 'payload.full_name required to complete onboarding';
  END IF;
  IF v_grade_id IS NULL THEN
    RAISE EXCEPTION 'payload.grade_id required for GTR employee number';
  END IF;
  IF v_draft.banking_json IS NULL THEN
    RAISE EXCEPTION 'banking_json required before completion';
  END IF;

  SELECT code INTO v_grade_code FROM public.hr_grades WHERE id = v_grade_id AND is_active;
  IF v_grade_code IS NULL THEN
    RAISE EXCEPTION 'active grade not found: %', v_grade_id;
  END IF;

  IF v_hr_role_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM public.hr_roles WHERE id = v_hr_role_id AND is_active
  ) THEN
    RAISE EXCEPTION 'active hr_role not found: %', v_hr_role_id;
  END IF;

  v_staff_role := public.resolve_hr_onboarding_staff_role(v_payload, v_hr_role_id);

  v_emp_code := public.next_employee_code_for_grade(v_grade_code);
  v_temp_hint := encode(extensions.gen_random_bytes(9), 'base64');

  IF v_draft.employee_id IS NULL THEN
    INSERT INTO public.employees (
      employee_code, full_name, user_id, email, phone_e164, hire_date,
      hr_role_id, grade_id, status
    ) VALUES (
      v_emp_code,
      v_full_name,
      v_user_id,
      v_email,
      v_phone,
      COALESCE((v_payload->>'hire_date')::date, CURRENT_DATE),
      v_hr_role_id,
      v_grade_id,
      'active'
    )
    RETURNING id INTO v_emp_id;
  ELSE
    v_emp_id := v_draft.employee_id;
    UPDATE public.employees
    SET
      employee_code = v_emp_code,
      full_name = v_full_name,
      email = COALESCE(v_email, email),
      phone_e164 = COALESCE(v_phone, phone_e164),
      hr_role_id = COALESCE(v_hr_role_id, hr_role_id),
      grade_id = v_grade_id,
      user_id = COALESCE(v_user_id, user_id),
      updated_at = now()
    WHERE id = v_emp_id;
  END IF;

  IF v_user_id IS NOT NULL THEN
    UPDATE public.profiles
    SET must_change_password = true, updated_at = now()
    WHERE id = v_user_id;

    PERFORM public.apply_hr_onboarding_staff_role(v_user_id, v_staff_role);
  END IF;

  UPDATE public.hr_onboarding_drafts
  SET
    employee_id = v_emp_id,
    stage = 'credentials',
    completed_at = now(),
    updated_by = auth.uid(),
    updated_at = now(),
    payload = v_payload || jsonb_build_object(
      'employee_code', v_emp_code,
      'completed', true,
      'staff_role', v_staff_role::text
    )
  WHERE id = p_draft_id;

  RETURN jsonb_build_object(
    'draft_id', p_draft_id,
    'employee_id', v_emp_id,
    'employee_code', v_emp_code,
    'email', v_email,
    'phone_e164', v_phone,
    'user_id', v_user_id,
    'staff_role', v_staff_role::text,
    'must_change_password', v_user_id IS NOT NULL,
    'temp_password_hint', v_temp_hint,
    'message', 'Create/link auth user via hr-onboarding-create-auth Edge when user_id absent; staff_role assigned on link.'
  );
END;
$$;

CREATE OR REPLACE FUNCTION public.link_employee_auth_user(
  p_employee_id UUID,
  p_user_id UUID,
  p_phone_e164 TEXT DEFAULT NULL,
  p_full_name TEXT DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_emp public.employees%ROWTYPE;
  v_staff_role public.staff_role;
  v_draft_payload JSONB;
BEGIN
  IF auth.role() IS DISTINCT FROM 'service_role' THEN
    RAISE EXCEPTION 'service_role required';
  END IF;

  IF p_employee_id IS NULL OR p_user_id IS NULL THEN
    RAISE EXCEPTION 'employee_id and user_id required';
  END IF;

  SELECT * INTO v_emp FROM public.employees WHERE id = p_employee_id FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'employee not found: %', p_employee_id;
  END IF;

  IF v_emp.user_id IS NOT NULL AND v_emp.user_id IS DISTINCT FROM p_user_id THEN
    RAISE EXCEPTION 'employee already linked to a different user';
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.employees e
    WHERE e.user_id = p_user_id AND e.id IS DISTINCT FROM p_employee_id
  ) THEN
    RAISE EXCEPTION 'user already linked to another employee';
  END IF;

  UPDATE public.employees
  SET
    user_id = p_user_id,
    phone_e164 = COALESCE(NULLIF(trim(COALESCE(p_phone_e164, '')), ''), phone_e164),
    full_name = COALESCE(NULLIF(trim(COALESCE(p_full_name, '')), ''), full_name),
    updated_at = now()
  WHERE id = p_employee_id;

  UPDATE public.profiles
  SET
    must_change_password = true,
    phone_e164 = COALESCE(
      NULLIF(trim(COALESCE(p_phone_e164, '')), ''),
      phone_e164
    ),
    full_name = COALESCE(
      NULLIF(trim(COALESCE(p_full_name, '')), ''),
      full_name
    ),
    updated_at = now()
  WHERE id = p_user_id;

  SELECT d.payload INTO v_draft_payload
  FROM public.hr_onboarding_drafts d
  WHERE d.employee_id = p_employee_id
    AND d.completed_at IS NOT NULL
  ORDER BY d.completed_at DESC
  LIMIT 1;

  v_staff_role := public.resolve_hr_onboarding_staff_role(
    COALESCE(v_draft_payload, '{}'::jsonb),
    v_emp.hr_role_id
  );
  PERFORM public.apply_hr_onboarding_staff_role(p_user_id, v_staff_role);

  RETURN p_employee_id;
END;
$$;

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
  p_clause_template_ids UUID[] DEFAULT '{}',
  p_default_staff_role public.staff_role DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
  v_default public.staff_role := p_default_staff_role;
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

  -- Organogram may set default_staff_role including admin only for admins (caller already admin).
  INSERT INTO public.hr_roles (
    parent_role_id, title, department, grade_id, duties_md, remuneration_notes,
    pay_frequency, module_access, comms_preferences, clause_template_ids,
    default_staff_role, created_by
  )
  VALUES (
    p_parent_role_id, trim(p_title), p_department, p_grade_id, p_duties_md, p_remuneration_notes,
    COALESCE(p_pay_frequency, 'monthly'),
    COALESCE(p_module_access, '[]'::jsonb),
    COALESCE(p_comms_preferences, '{}'::jsonb),
    COALESCE(p_clause_template_ids, '{}'),
    v_default,
    auth.uid()
  )
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public._parse_staff_role_text(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.resolve_hr_onboarding_staff_role(JSONB, UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.apply_hr_onboarding_staff_role(UUID, public.staff_role) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.resolve_hr_onboarding_staff_role(JSONB, UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.apply_hr_onboarding_staff_role(UUID, public.staff_role)
  TO authenticated, service_role;
