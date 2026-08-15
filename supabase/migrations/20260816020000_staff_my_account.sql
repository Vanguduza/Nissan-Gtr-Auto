-- Staff My Account: self profile fields + history RPCs.
-- Editable: phone, address, email sync. Never grade/role/wages/staff_roles.
-- Gross payslips only; no PAYE/NSSA/ZIMRA.

ALTER TABLE public.employees
  ADD COLUMN IF NOT EXISTS address TEXT,
  ADD COLUMN IF NOT EXISTS photo_storage_path TEXT;

COMMENT ON COLUMN public.employees.address IS
  'Residential / postal address; staff may update via update_my_staff_profile.';
COMMENT ON COLUMN public.employees.photo_storage_path IS
  'Optional Storage object key for ID photo (read-only on My Account).';

-- Backfill address / photo path from completed onboarding drafts when present.
UPDATE public.employees e
SET
  address = COALESCE(
    e.address,
    nullif(trim(COALESCE(d.payload->>'address', '')), '')
  ),
  photo_storage_path = COALESCE(
    e.photo_storage_path,
    nullif(trim(COALESCE(
      d.payload->>'photo_storage_path',
      d.payload->>'photo_file_name',
      ''
    )), '')
  ),
  updated_at = now()
FROM public.hr_onboarding_drafts d
WHERE d.employee_id = e.id
  AND d.completed_at IS NOT NULL
  AND (
    (e.address IS NULL AND nullif(trim(COALESCE(d.payload->>'address', '')), '') IS NOT NULL)
    OR (
      e.photo_storage_path IS NULL
      AND nullif(trim(COALESCE(
        d.payload->>'photo_storage_path',
        d.payload->>'photo_file_name',
        ''
      )), '') IS NOT NULL
    )
  );

CREATE OR REPLACE FUNCTION public.get_my_staff_profile()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_emp public.employees%ROWTYPE;
  v_grade_code TEXT;
  v_grade_title TEXT;
  v_role_title TEXT;
  v_module_access JSONB;
  v_staff_roles TEXT[];
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;

  SELECT * INTO v_emp
  FROM public.employees
  WHERE user_id = auth.uid()
  LIMIT 1;

  IF NOT FOUND THEN
    RETURN jsonb_build_object(
      'has_employee', false,
      'user_id', auth.uid()
    );
  END IF;

  IF v_emp.grade_id IS NOT NULL THEN
    SELECT g.code, g.title INTO v_grade_code, v_grade_title
    FROM public.hr_grades g
    WHERE g.id = v_emp.grade_id;
  END IF;

  IF v_emp.hr_role_id IS NOT NULL THEN
    SELECT r.title, COALESCE(r.module_access, '[]'::jsonb)
    INTO v_role_title, v_module_access
    FROM public.hr_roles r
    WHERE r.id = v_emp.hr_role_id;
  ELSE
    v_module_access := '[]'::jsonb;
  END IF;

  SELECT COALESCE(array_agg(sr.role::text ORDER BY sr.role::text), ARRAY[]::text[])
  INTO v_staff_roles
  FROM public.staff_roles sr
  WHERE sr.user_id = auth.uid();

  RETURN jsonb_build_object(
    'has_employee', true,
    'employee_id', v_emp.id,
    'employee_code', v_emp.employee_code,
    'full_name', v_emp.full_name,
    'email', v_emp.email,
    'phone_e164', v_emp.phone_e164,
    'address', v_emp.address,
    'photo_storage_path', v_emp.photo_storage_path,
    'status', v_emp.status,
    'hire_date', v_emp.hire_date,
    'grade_id', v_emp.grade_id,
    'grade_code', v_grade_code,
    'grade_title', v_grade_title,
    'hr_role_id', v_emp.hr_role_id,
    'role_title', v_role_title,
    'module_access', COALESCE(v_module_access, '[]'::jsonb),
    'staff_roles', to_jsonb(v_staff_roles),
    'user_id', v_emp.user_id
  );
END;
$$;

COMMENT ON FUNCTION public.get_my_staff_profile() IS
  'Staff self-service identity payload; includes read-only grade/role/module_access.';

CREATE OR REPLACE FUNCTION public.update_my_staff_profile(
  p_phone_e164 TEXT DEFAULT NULL,
  p_address TEXT DEFAULT NULL,
  p_email TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_emp_id UUID;
  v_phone TEXT;
  v_address TEXT;
  v_email TEXT;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;

  SELECT id INTO v_emp_id
  FROM public.employees
  WHERE user_id = auth.uid()
  LIMIT 1;

  IF v_emp_id IS NULL THEN
    RAISE EXCEPTION 'no employee linked to this user';
  END IF;

  v_phone := nullif(trim(COALESCE(p_phone_e164, '')), '');
  v_address := nullif(trim(COALESCE(p_address, '')), '');
  v_email := nullif(lower(trim(COALESCE(p_email, ''))), '');

  IF v_email IS NOT NULL AND v_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' THEN
    RAISE EXCEPTION 'invalid email';
  END IF;

  UPDATE public.employees
  SET
    phone_e164 = CASE WHEN p_phone_e164 IS NULL THEN phone_e164 ELSE v_phone END,
    address = CASE WHEN p_address IS NULL THEN address ELSE v_address END,
    email = CASE WHEN p_email IS NULL THEN email ELSE v_email END,
    updated_at = now()
  WHERE id = v_emp_id
    AND user_id = auth.uid();

  IF p_phone_e164 IS NOT NULL THEN
    UPDATE public.profiles
    SET phone_e164 = v_phone, updated_at = now()
    WHERE id = auth.uid();
  END IF;

  RETURN public.get_my_staff_profile();
END;
$$;

COMMENT ON FUNCTION public.update_my_staff_profile(TEXT, TEXT, TEXT) IS
  'Staff may update own phone/address/email on employees (+ profiles.phone). '
  'Does not change grade, hr_role, wages, or staff_roles. Email auth change is client GoTrue.';

CREATE OR REPLACE FUNCTION public.list_my_payslip_history()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_self UUID;
  v_rows JSONB;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;

  v_self := public.current_employee_id();
  IF v_self IS NULL THEN
    RETURN '[]'::jsonb;
  END IF;

  SELECT COALESCE(
    jsonb_agg(
      jsonb_build_object(
        'payroll_line_id', pl.id,
        'payroll_run_id', pl.payroll_run_id,
        'payslip_id', ps.id,
        'period_start', pr.period_start,
        'period_end', pr.period_end,
        'document_number', pr.document_number,
        'run_status', pr.status,
        'currency', pl.currency,
        'gross_amount', pl.gross_amount,
        'deductions_amount', pl.deductions_amount,
        'net_amount', pl.net_amount,
        'funded', pl.payment_journal_id IS NOT NULL,
        'storage_bucket', ps.storage_bucket,
        'storage_path', ps.storage_path,
        'generated_at', ps.generated_at
      )
      ORDER BY pr.period_end DESC, pl.created_at DESC
    ),
    '[]'::jsonb
  )
  INTO v_rows
  FROM public.payroll_lines pl
  JOIN public.payroll_runs pr ON pr.id = pl.payroll_run_id
  LEFT JOIN public.payslips ps ON ps.payroll_line_id = pl.id
  WHERE pl.employee_id = v_self
    AND pr.status IN ('submitted', 'cancelled');

  RETURN v_rows;
END;
$$;

COMMENT ON FUNCTION public.list_my_payslip_history() IS
  'Own payroll lines on submitted/cancelled runs with optional payslip storage metadata. Gross-only; no tax.';

-- Persist address + photo path on onboarding complete (extends 20260815250000 body).
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
  v_address TEXT;
  v_photo TEXT;
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
  v_address := nullif(trim(COALESCE(v_payload->>'address', '')), '');
  v_photo := nullif(trim(COALESCE(
    v_payload->>'photo_storage_path',
    v_payload->>'photo_file_name',
    ''
  )), '');
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
      employee_code, full_name, user_id, email, phone_e164, address,
      photo_storage_path, hire_date, hr_role_id, grade_id, status
    ) VALUES (
      v_emp_code,
      v_full_name,
      v_user_id,
      v_email,
      v_phone,
      v_address,
      v_photo,
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
      address = COALESCE(v_address, address),
      photo_storage_path = COALESCE(v_photo, photo_storage_path),
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

REVOKE ALL ON FUNCTION public.get_my_staff_profile() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.update_my_staff_profile(TEXT, TEXT, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.list_my_payslip_history() FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.get_my_staff_profile()
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.update_my_staff_profile(TEXT, TEXT, TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.list_my_payslip_history()
  TO authenticated, service_role;
