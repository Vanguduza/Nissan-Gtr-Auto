-- Reconcile missing own-account and real employee-card functionality.
-- Identity and authorization come from the authenticated actor, never request-body roles.
ALTER TABLE public.employees ADD COLUMN IF NOT EXISTS address TEXT,
  ADD COLUMN IF NOT EXISTS photo_storage_path TEXT;

-- Match organogram module restrictions in newly exposed staff data RPCs.
CREATE OR REPLACE FUNCTION private.staff_module_allowed(p_module TEXT)
RETURNS BOOLEAN LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=public AS $$
DECLARE v_access JSONB;v_count INT;v_allowed BOOLEAN;
BEGIN
  IF auth.role()='service_role' OR public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN RETURN true; END IF;
  IF auth.uid() IS NULL OR NOT public.is_staff() THEN RETURN false; END IF;
  v_access:=public.my_module_access();
  IF v_access IS NULL THEN RETURN true; END IF;
  IF jsonb_typeof(v_access)<>'array' THEN RETURN false; END IF;
  SELECT count(*),COALESCE(bool_or(lower(trim(value))=lower(trim(p_module))),false)
  INTO v_count,v_allowed FROM jsonb_array_elements_text(v_access) x(value) WHERE trim(value)<>'';
  RETURN v_count=0 OR v_allowed;
END;
$$;
REVOKE ALL ON FUNCTION private.staff_module_allowed(TEXT) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION private.staff_module_allowed(TEXT) TO service_role;

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
    'email', (SELECT u.email FROM auth.users u WHERE u.id = auth.uid()),
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

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'employee-photos',
  'employee-photos',
  false,
  5242880,
  ARRAY['image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO NOTHING;

-- Path convention: {employee_id}/{filename}
DROP POLICY IF EXISTS employee_photos_storage_select ON storage.objects;
CREATE POLICY employee_photos_storage_select
  ON storage.objects FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'employee-photos'
    AND (
      public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
      OR EXISTS (
        SELECT 1
        FROM public.employees e
        WHERE e.user_id = (SELECT auth.uid())
          AND name LIKE (e.id::text || '/%')
      )
    )
  );

DROP POLICY IF EXISTS employee_photos_storage_insert ON storage.objects;
CREATE POLICY employee_photos_storage_insert
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'employee-photos'
    AND (
      public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
      OR EXISTS (
        SELECT 1
        FROM public.employees e
        WHERE e.user_id = (SELECT auth.uid())
          AND name LIKE (e.id::text || '/%')
      )
    )
  );

DROP POLICY IF EXISTS employee_photos_storage_update ON storage.objects;
CREATE POLICY employee_photos_storage_update
  ON storage.objects FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'employee-photos'
    AND (
      public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
      OR EXISTS (
        SELECT 1
        FROM public.employees e
        WHERE e.user_id = (SELECT auth.uid())
          AND name LIKE (e.id::text || '/%')
      )
    )
  )
  WITH CHECK (
    bucket_id = 'employee-photos'
    AND (
      public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
      OR EXISTS (
        SELECT 1
        FROM public.employees e
        WHERE e.user_id = (SELECT auth.uid())
          AND name LIKE (e.id::text || '/%')
      )
    )
  );

DROP POLICY IF EXISTS employee_photos_storage_delete ON storage.objects;
CREATE POLICY employee_photos_storage_delete
  ON storage.objects FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'employee-photos'
    AND (
      public.has_staff_role(ARRAY['admin', 'hr']::public.staff_role[])
      OR EXISTS (
        SELECT 1
        FROM public.employees e
        WHERE e.user_id = (SELECT auth.uid())
          AND name LIKE (e.id::text || '/%')
      )
    )
  );

CREATE OR REPLACE FUNCTION public.update_my_staff_profile(
  p_phone_e164 TEXT DEFAULT NULL, p_address TEXT DEFAULT NULL,
  p_email TEXT DEFAULT NULL, p_photo_storage_path TEXT DEFAULT NULL
) RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_employee UUID; v_email TEXT; v_photo TEXT;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
  SELECT id INTO v_employee FROM public.employees WHERE user_id = auth.uid();
  IF v_employee IS NULL THEN RAISE EXCEPTION 'no employee linked to this user'; END IF;
  IF length(COALESCE(p_phone_e164,'')) > 32 OR length(COALESCE(p_address,'')) > 2000 THEN
    RAISE EXCEPTION 'contact field too long';
  END IF;
  IF p_email IS NOT NULL THEN
    SELECT lower(u.email) INTO v_email FROM auth.users u WHERE u.id = auth.uid();
    IF lower(trim(p_email)) IS DISTINCT FROM v_email THEN
      RAISE EXCEPTION 'confirm the email change with authentication before syncing the staff profile';
    END IF;
  END IF;
  v_photo := NULLIF(trim(p_photo_storage_path),'');
  IF v_photo IS NOT NULL THEN
    IF split_part(v_photo,'/',1) <> v_employee::text OR position('..' in v_photo) > 0 THEN
      RAISE EXCEPTION 'photo path must be under own employee id';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM storage.objects WHERE bucket_id='employee-photos' AND name=v_photo) THEN
      RAISE EXCEPTION 'upload the employee photo before selecting it';
    END IF;
  END IF;
  UPDATE public.employees SET
    phone_e164=CASE WHEN p_phone_e164 IS NULL THEN phone_e164 ELSE NULLIF(trim(p_phone_e164),'') END,
    address=CASE WHEN p_address IS NULL THEN address ELSE NULLIF(trim(p_address),'') END,
    email=CASE WHEN p_email IS NULL THEN email ELSE v_email END,
    photo_storage_path=CASE WHEN p_photo_storage_path IS NULL THEN photo_storage_path ELSE v_photo END,
    updated_at=now()
  WHERE id=v_employee AND user_id=auth.uid();
  IF p_phone_e164 IS NOT NULL THEN
    UPDATE public.profiles SET phone_e164=NULLIF(trim(p_phone_e164),''),updated_at=now() WHERE id=auth.uid();
  END IF;
  RETURN public.get_my_staff_profile();
END;
$$;
REVOKE ALL ON FUNCTION public.update_my_staff_profile(TEXT,TEXT,TEXT,TEXT) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.update_my_staff_profile(TEXT,TEXT,TEXT,TEXT) TO authenticated,service_role;

-- Staff-only verification reveals the identity printed on the card, not private contact/pay data.
CREATE OR REPLACE FUNCTION public.verify_staff_identity(p_employee_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
DECLARE v_result JSONB;
BEGIN
  IF auth.role() <> 'service_role' AND (auth.uid() IS NULL OR NOT public.is_staff()) THEN
    RAISE EXCEPTION 'staff access required';
  END IF;
  SELECT jsonb_build_object('employee_id',e.id,'employee_code',e.employee_code,
    'full_name',e.full_name,'role_title',r.title,'status',e.status)
  INTO v_result FROM public.employees e LEFT JOIN public.hr_roles r ON r.id=e.hr_role_id
  WHERE e.id=p_employee_id;
  IF v_result IS NULL THEN RAISE EXCEPTION 'staff identity not found'; END IF;
  RETURN v_result;
END;
$$;
REVOKE ALL ON FUNCTION public.verify_staff_identity(UUID) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.verify_staff_identity(UUID) TO authenticated,service_role;

-- A printable card is derived server-side; only own employee or HR/admin can issue one.
CREATE OR REPLACE FUNCTION public.staff_identity_card_payload(p_employee_code TEXT)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
DECLARE v_employee public.employees%ROWTYPE; v_role TEXT; v_staff_role TEXT;
BEGIN
  IF auth.role() <> 'service_role' AND (auth.uid() IS NULL OR NOT public.is_staff()) THEN
    RAISE EXCEPTION 'staff access required';
  END IF;
  SELECT * INTO v_employee FROM public.employees WHERE employee_code=p_employee_code;
  IF NOT FOUND THEN RAISE EXCEPTION 'employee not found'; END IF;
  IF auth.role() <> 'service_role' AND v_employee.user_id IS DISTINCT FROM auth.uid()
    AND NOT public.has_staff_role(ARRAY['admin','hr']::public.staff_role[]) THEN
    RAISE EXCEPTION 'own employee or HR/admin only';
  END IF;
  SELECT title INTO v_role FROM public.hr_roles WHERE id=v_employee.hr_role_id;
  SELECT role::text INTO v_staff_role FROM public.staff_roles WHERE user_id=v_employee.user_id ORDER BY role LIMIT 1;
  RETURN jsonb_build_object('storeName','Nissan GTR Auto','fullName',v_employee.full_name,
    'employeeCode',v_employee.employee_code,'roleTitle',COALESCE(v_role,'Staff'),
    'staffRole',v_staff_role,'photoStoragePath',v_employee.photo_storage_path,
    'verifyUrl','https://nissangtrauto.co.zw/staff/verify/'||v_employee.id::text);
END;
$$;
REVOKE ALL ON FUNCTION public.staff_identity_card_payload(TEXT) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.staff_identity_card_payload(TEXT) TO authenticated,service_role;

-- Keep later onboarding code intact; extend completion with the two identity fields only.
CREATE OR REPLACE FUNCTION private.persist_staff_onboarding_identity()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path=public AS $$
BEGIN
  IF NEW.completed_at IS NOT NULL AND NEW.employee_id IS NOT NULL THEN
    UPDATE public.employees SET
      address=COALESCE(address,NULLIF(trim(NEW.payload->>'address'),'')),
      photo_storage_path=COALESCE(photo_storage_path,NULLIF(trim(NEW.payload->>'photo_storage_path'),'')),
      updated_at=now() WHERE id=NEW.employee_id;
  END IF;
  RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION private.persist_staff_onboarding_identity() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER persist_staff_onboarding_identity AFTER INSERT OR UPDATE OF completed_at,employee_id
  ON public.hr_onboarding_drafts FOR EACH ROW EXECUTE FUNCTION private.persist_staff_onboarding_identity();

REVOKE ALL ON FUNCTION public.get_my_staff_profile() FROM PUBLIC,anon;
REVOKE ALL ON FUNCTION public.list_my_payslip_history() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.get_my_staff_profile() TO authenticated,service_role;
GRANT EXECUTE ON FUNCTION public.list_my_payslip_history() TO authenticated,service_role;
