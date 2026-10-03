-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905135206 close_anonymous_supplier_admin_bypass).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION public.create_supplier(
  p_code text,
  p_name text,
  p_email text DEFAULT NULL::text,
  p_phone_e164 text DEFAULT NULL::text,
  p_default_currency public.currency_code DEFAULT 'USD'::public.currency_code
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_id uuid;
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin role required';
  END IF;

  IF NULLIF(trim(COALESCE(p_code,'')),'') IS NULL THEN
    RAISE EXCEPTION 'supplier code required';
  END IF;
  IF NULLIF(trim(COALESCE(p_name,'')),'') IS NULL THEN
    RAISE EXCEPTION 'supplier name required';
  END IF;

  INSERT INTO public.suppliers(code,name,email,phone_e164,default_currency)
  VALUES(upper(trim(p_code)),trim(p_name),NULLIF(trim(COALESCE(p_email,'')),''),NULLIF(trim(COALESCE(p_phone_e164,'')),''),p_default_currency)
  RETURNING id INTO v_id;
  RETURN v_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.link_supplier_profile(
  p_supplier_id uuid,
  p_profile_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
  IF NOT (
    auth.role() = 'service_role'
    OR public.has_staff_role(ARRAY['admin']::public.staff_role[])
  ) THEN
    RAISE EXCEPTION 'admin role required';
  END IF;

  IF p_supplier_id IS NULL OR p_profile_id IS NULL THEN
    RAISE EXCEPTION 'supplier_id and profile_id required';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.profiles p WHERE p.id=p_profile_id) THEN
    RAISE EXCEPTION 'profile not found';
  END IF;

  UPDATE public.suppliers
  SET profile_id=p_profile_id,updated_at=now()
  WHERE id=p_supplier_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'supplier not found: %',p_supplier_id;
  END IF;
END;
$function$;

REVOKE ALL ON FUNCTION public.create_supplier(text,text,text,text,public.currency_code) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.link_supplier_profile(uuid,uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_supplier(text,text,text,text,public.currency_code) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.link_supplier_profile(uuid,uuid) TO authenticated, service_role;
