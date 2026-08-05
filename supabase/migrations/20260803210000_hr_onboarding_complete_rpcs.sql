-- Batch 1 follow-up: HR onboarding save/advance/complete RPCs.
-- Employee number GTR{grade}{3-digit} per grade. must_change_password on link.
-- Banking/health only via SECURITY DEFINER RPCs (HR/admin). No payroll tax.

CREATE OR REPLACE FUNCTION public.next_employee_code_for_grade(p_grade_code TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_code TEXT;
  v_prefix TEXT;
BEGIN
  v_code := upper(trim(COALESCE(p_grade_code, '')));
  IF v_code !~ '^[A-Z][0-9]+$' THEN
    RAISE EXCEPTION 'invalid grade code for employee number: %', p_grade_code;
  END IF;
  v_prefix := 'GTR' || v_code;

  INSERT INTO public.naming_series (prefix, current_value, pad_length, description)
  VALUES (v_prefix, 0, 3, format('Employee numbers grade %s', v_code))
  ON CONFLICT (prefix) DO UPDATE
  SET pad_length = 3;

  RETURN public.next_series_value(v_prefix);
END;
$$;

CREATE OR REPLACE FUNCTION public.save_hr_onboarding_stage(
  p_draft_id UUID DEFAULT NULL,
  p_stage public.hr_onboarding_stage DEFAULT 'personal',
  p_payload JSONB DEFAULT '{}'::jsonb,
  p_banking_json JSONB DEFAULT NULL,
  p_health_json JSONB DEFAULT NULL,
  p_employee_id UUID DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[]) THEN
    RAISE EXCEPTION 'hr or admin role required';
  END IF;

  IF p_draft_id IS NULL THEN
    INSERT INTO public.hr_onboarding_drafts (
      stage, payload, banking_json, health_json, employee_id,
      created_by, updated_by
    ) VALUES (
      p_stage,
      COALESCE(p_payload, '{}'::jsonb),
      p_banking_json,
      p_health_json,
      p_employee_id,
      auth.uid(),
      auth.uid()
    )
    RETURNING id INTO v_id;
    RETURN v_id;
  END IF;

  UPDATE public.hr_onboarding_drafts
  SET
    stage = p_stage,
    payload = COALESCE(p_payload, payload),
    banking_json = CASE
      WHEN p_banking_json IS NOT NULL THEN p_banking_json
      ELSE banking_json
    END,
    health_json = CASE
      WHEN p_health_json IS NOT NULL THEN p_health_json
      ELSE health_json
    END,
    employee_id = COALESCE(p_employee_id, employee_id),
    updated_by = auth.uid(),
    updated_at = now()
  WHERE id = p_draft_id
    AND completed_at IS NULL
  RETURNING id INTO v_id;

  IF v_id IS NULL THEN
    RAISE EXCEPTION 'onboarding draft not found or already completed: %', p_draft_id;
  END IF;
  RETURN v_id;
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

  v_emp_code := public.next_employee_code_for_grade(v_grade_code);
  -- Temp password hint for Edge / HR outbox — not stored as plaintext auth secret
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
      'completed', true
    )
  WHERE id = p_draft_id;

  RETURN jsonb_build_object(
    'draft_id', p_draft_id,
    'employee_id', v_emp_id,
    'employee_code', v_emp_code,
    'email', v_email,
    'phone_e164', v_phone,
    'user_id', v_user_id,
    'must_change_password', v_user_id IS NOT NULL,
    'temp_password_hint', v_temp_hint,
    'message', 'Create/link auth user via complete-hr-onboarding Edge when user_id absent; deliver credentials via existing outbox.'
  );
END;
$$;

-- Staff may set marketing opt-in for a customer (CRM); cooldown stamp stays worker-only
CREATE OR REPLACE FUNCTION public.set_customer_marketing_opt_in(
  p_customer_id UUID,
  p_opt_in BOOLEAN
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_id UUID;
BEGIN
  IF NOT public.has_staff_role(ARRAY['admin', 'sales', 'hr']::public.staff_role[]) THEN
    RAISE EXCEPTION 'admin, sales, or hr role required';
  END IF;

  UPDATE public.customers
  SET marketing_opt_in = COALESCE(p_opt_in, false),
      updated_at = now()
  WHERE id = p_customer_id
  RETURNING id INTO v_id;

  IF v_id IS NULL THEN
    RAISE EXCEPTION 'customer not found: %', p_customer_id;
  END IF;
  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION public.next_employee_code_for_grade(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.save_hr_onboarding_stage(
  UUID, public.hr_onboarding_stage, JSONB, JSONB, JSONB, UUID
) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.complete_hr_onboarding(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.set_customer_marketing_opt_in(UUID, BOOLEAN) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.next_employee_code_for_grade(TEXT)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.save_hr_onboarding_stage(
  UUID, public.hr_onboarding_stage, JSONB, JSONB, JSONB, UUID
) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.complete_hr_onboarding(UUID)
  TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.set_customer_marketing_opt_in(UUID, BOOLEAN)
  TO authenticated, service_role;
