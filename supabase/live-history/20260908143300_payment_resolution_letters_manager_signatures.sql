-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908143300 payment_resolution_letters_manager_signatures).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Manager signatures + authoritative payment-resolution letters.
-- Signature assets and rendered letters are private. Payment facts are derived server-side.

ALTER TABLE public.employees
 ADD COLUMN IF NOT EXISTS signature_storage_bucket TEXT,
 ADD COLUMN IF NOT EXISTS signature_storage_path TEXT,
 ADD COLUMN IF NOT EXISTS signature_mime_type TEXT,
 ADD COLUMN IF NOT EXISTS signature_sha256 TEXT,
 ADD COLUMN IF NOT EXISTS signature_captured_at TIMESTAMPTZ,
 ADD COLUMN IF NOT EXISTS signature_updated_by UUID REFERENCES auth.users(id);

ALTER TABLE public.employees DROP CONSTRAINT IF EXISTS employees_signature_sha256_check;

ALTER TABLE public.employees ADD CONSTRAINT employees_signature_sha256_check
 CHECK(signature_sha256 IS NULL OR signature_sha256 ~ '^[0-9a-f]{64}$');

ALTER TABLE public.employees DROP CONSTRAINT IF EXISTS employees_signature_mime_check;

ALTER TABLE public.employees ADD CONSTRAINT employees_signature_mime_check
 CHECK(signature_mime_type IS NULL OR signature_mime_type IN('image/png','image/jpeg'));

INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
VALUES('staff-signatures','staff-signatures',false,2097152,ARRAY['image/png','image/jpeg']),
      ('payment-resolution-letters','payment-resolution-letters',false,5242880,ARRAY['application/pdf'])
ON CONFLICT(id) DO UPDATE SET public=false,file_size_limit=EXCLUDED.file_size_limit,allowed_mime_types=EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS staff_signatures_own_insert ON storage.objects;

CREATE POLICY staff_signatures_own_insert ON storage.objects FOR INSERT TO authenticated
 WITH CHECK(bucket_id='staff-signatures' AND (storage.foldername(name))[1]=(SELECT auth.uid())::text
   AND (public.is_pos_approver() OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])));

DROP POLICY IF EXISTS staff_signatures_own_update ON storage.objects;

CREATE POLICY staff_signatures_own_update ON storage.objects FOR UPDATE TO authenticated
 USING(bucket_id='staff-signatures' AND owner_id=(SELECT auth.uid())::text)
 WITH CHECK(bucket_id='staff-signatures' AND (storage.foldername(name))[1]=(SELECT auth.uid())::text);

DROP POLICY IF EXISTS staff_signatures_own_select ON storage.objects;

CREATE POLICY staff_signatures_own_select ON storage.objects FOR SELECT TO authenticated
 USING(bucket_id='staff-signatures' AND (owner_id=(SELECT auth.uid())::text OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])));

CREATE TABLE IF NOT EXISTS public.business_document_profile(
 singleton BOOLEAN PRIMARY KEY DEFAULT true CHECK(singleton),
 legal_name TEXT NOT NULL DEFAULT 'Nissan GTR Auto',
 trading_name TEXT NOT NULL DEFAULT 'Nissan GTR Auto',
 domain TEXT NOT NULL DEFAULT 'nissangtrauto.co.zw',
 city TEXT DEFAULT 'Harare', country TEXT DEFAULT 'Zimbabwe',
 address_line1 TEXT,address_line2 TEXT,phone_e164 TEXT,email TEXT,registration_number TEXT,
 updated_by UUID REFERENCES auth.users(id),updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO public.business_document_profile(singleton) VALUES(true) ON CONFLICT(singleton) DO NOTHING;

ALTER TABLE public.business_document_profile ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.business_document_profile FROM PUBLIC,anon,authenticated;

GRANT ALL ON public.business_document_profile TO service_role;

DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_type WHERE typname='payment_resolution_source_kind') THEN
  CREATE TYPE public.payment_resolution_source_kind AS ENUM('card_terminal','ecocash','paynow','contipay','split_leg','split_refund');
 END IF;
END $$;

INSERT INTO public.naming_series(prefix,description,pad_length) VALUES('PDL-','Payment dispute letter',6) ON CONFLICT(prefix) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.payment_resolution_letters(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),document_number TEXT NOT NULL UNIQUE,
 source_kind public.payment_resolution_source_kind NOT NULL,source_id UUID NOT NULL,
 provider TEXT NOT NULL,observed_status TEXT NOT NULL,amount NUMERIC(18,2) NOT NULL CHECK(amount>=0),currency public.currency_code NOT NULL,
 external_reference TEXT,provider_reference TEXT,terminal_transaction_id TEXT,rrn TEXT,authorization_code TEXT,card_last4 TEXT,card_scheme TEXT,
 failure_detail TEXT,commerce_order_id UUID REFERENCES public.commerce_orders(id),sales_invoice_id UUID REFERENCES public.sales_invoices(id),customer_id UUID REFERENCES public.customers(id),
 customer_name TEXT,invoice_document_number TEXT,manager_user_id UUID NOT NULL REFERENCES auth.users(id),manager_employee_id UUID NOT NULL REFERENCES public.employees(id),
 manager_name TEXT NOT NULL,manager_title TEXT,manager_employee_code TEXT NOT NULL,
 signature_storage_bucket TEXT NOT NULL,signature_storage_path TEXT NOT NULL,signature_mime_type TEXT NOT NULL,signature_sha256 TEXT NOT NULL,
 issue_notes TEXT,rendered_storage_bucket TEXT,rendered_storage_path TEXT,rendered_sha256 TEXT,rendered_at TIMESTAMPTZ,
 issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS payment_resolution_source_once_idx ON public.payment_resolution_letters(source_kind,source_id,manager_user_id);

CREATE INDEX IF NOT EXISTS payment_resolution_invoice_idx ON public.payment_resolution_letters(sales_invoice_id,issued_at DESC);

CREATE INDEX IF NOT EXISTS payment_resolution_customer_idx ON public.payment_resolution_letters(customer_id,issued_at DESC);

ALTER TABLE public.payment_resolution_letters ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.payment_resolution_letters FROM PUBLIC,anon,authenticated;

GRANT ALL ON public.payment_resolution_letters TO service_role;

CREATE OR REPLACE FUNCTION public.get_my_manager_signature()
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE e public.employees%ROWTYPE;
BEGIN
 IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])) THEN RAISE EXCEPTION 'manager/finance/admin role required'; END IF;
 SELECT * INTO e FROM public.employees WHERE user_id=auth.uid() AND status='active';
 IF NOT FOUND THEN RAISE EXCEPTION 'active employee profile required'; END IF;
 RETURN jsonb_build_object('employee_id',e.id,'employee_code',e.employee_code,'full_name',e.full_name,
  'signature_bucket',e.signature_storage_bucket,'signature_path',e.signature_storage_path,'signature_mime_type',e.signature_mime_type,
  'signature_sha256',e.signature_sha256,'signature_captured_at',e.signature_captured_at,'has_signature',e.signature_storage_path IS NOT NULL);
END $$;

CREATE OR REPLACE FUNCTION public.register_my_manager_signature(p_storage_path TEXT,p_mime_type TEXT,p_sha256 TEXT)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE e public.employees%ROWTYPE; v_path TEXT;
BEGIN
 IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])) THEN RAISE EXCEPTION 'manager/finance/admin role required'; END IF;
 SELECT * INTO e FROM public.employees WHERE user_id=auth.uid() AND status='active' FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'active employee profile required'; END IF;
 v_path:=trim(COALESCE(p_storage_path,''));
 IF v_path='' OR split_part(v_path,'/',1)<>auth.uid()::text THEN RAISE EXCEPTION 'signature path must be owned by current manager'; END IF;
 IF p_mime_type NOT IN('image/png','image/jpeg') OR COALESCE(p_sha256,'')!~'^[0-9a-f]{64}$' THEN RAISE EXCEPTION 'valid PNG/JPEG signature and sha256 required'; END IF;
 IF NOT EXISTS(SELECT 1 FROM storage.objects WHERE bucket_id='staff-signatures' AND name=v_path) THEN RAISE EXCEPTION 'uploaded signature object not found'; END IF;
 UPDATE public.employees SET signature_storage_bucket='staff-signatures',signature_storage_path=v_path,signature_mime_type=p_mime_type,
  signature_sha256=lower(p_sha256),signature_captured_at=now(),signature_updated_by=auth.uid(),updated_at=now() WHERE id=e.id;
 RETURN public.get_my_manager_signature();
END $$;

CREATE OR REPLACE FUNCTION public.set_business_document_profile(
 p_legal_name TEXT,p_trading_name TEXT,p_domain TEXT,p_city TEXT DEFAULT NULL,p_country TEXT DEFAULT NULL,p_address_line1 TEXT DEFAULT NULL,
 p_address_line2 TEXT DEFAULT NULL,p_phone_e164 TEXT DEFAULT NULL,p_email TEXT DEFAULT NULL,p_registration_number TEXT DEFAULT NULL)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 IF NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN RAISE EXCEPTION 'admin role required'; END IF;
 UPDATE public.business_document_profile SET legal_name=trim(p_legal_name),trading_name=trim(p_trading_name),domain=trim(p_domain),
  city=NULLIF(trim(COALESCE(p_city,'')),''),country=NULLIF(trim(COALESCE(p_country,'')),''),address_line1=NULLIF(trim(COALESCE(p_address_line1,'')),''),
  address_line2=NULLIF(trim(COALESCE(p_address_line2,'')),''),phone_e164=NULLIF(trim(COALESCE(p_phone_e164,'')),''),email=NULLIF(trim(COALESCE(p_email,'')),''),
  registration_number=NULLIF(trim(COALESCE(p_registration_number,'')),''),updated_by=auth.uid(),updated_at=now() WHERE singleton=true;
 RETURN (SELECT to_jsonb(x) FROM public.business_document_profile x WHERE singleton=true);
END $$;

CREATE OR REPLACE FUNCTION public.create_payment_resolution_letter(p_source_kind public.payment_resolution_source_kind,p_source_id UUID,p_issue_notes TEXT DEFAULT NULL)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE e public.employees%ROWTYPE; v_title TEXT; v_provider TEXT; v_status TEXT; v_amount NUMERIC; v_currency public.currency_code;
 v_ext TEXT; v_pref TEXT; v_txn TEXT; v_rrn TEXT; v_auth TEXT; v_last4 TEXT; v_scheme TEXT; v_failure TEXT; v_order UUID; v_invoice UUID; v_customer UUID;
 v_customer_name TEXT; v_invoice_no TEXT; v_id UUID; a public.pos_card_terminal_attempts%ROWTYPE; l public.pos_split_payment_legs%ROWTYPE; r public.pos_split_refund_requests%ROWTYPE;
BEGIN
 IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])) THEN RAISE EXCEPTION 'manager/finance/admin role required'; END IF;
 SELECT * INTO e FROM public.employees WHERE user_id=auth.uid() AND status='active';
 IF NOT FOUND OR e.signature_storage_path IS NULL OR e.signature_sha256 IS NULL THEN RAISE EXCEPTION 'manager profile signature required before issuing a payment-resolution letter'; END IF;
 SELECT hr.title INTO v_title FROM public.hr_roles hr WHERE hr.id=e.hr_role_id;
 IF p_source_kind='card_terminal' THEN
  SELECT * INTO a FROM public.pos_card_terminal_attempts WHERE id=p_source_id; IF NOT FOUND THEN RAISE EXCEPTION 'card terminal attempt not found'; END IF;
  v_provider:='card_terminal';v_status:=a.status::text;v_amount:=a.amount;v_currency:=a.currency;v_ext:=a.external_ref;v_pref:=a.terminal_transaction_id;
  v_txn:=a.terminal_transaction_id;v_rrn:=a.rrn;v_auth:=a.authorization_code;v_last4:=a.card_last4;v_scheme:=a.card_scheme;v_failure:=COALESCE(a.finalization_error,a.response_message);
  v_order:=a.commerce_order_id;v_invoice:=a.source_invoice_id; IF v_invoice IS NULL THEN v_invoice:=a.invoice_id; END IF;
 ELSIF p_source_kind='split_leg' THEN
  SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=p_source_id; IF NOT FOUND THEN RAISE EXCEPTION 'split payment leg not found'; END IF;
  v_provider:=l.tender::text;v_status:=l.status::text;v_amount:=l.requested_amount;v_ext:=l.external_reference;v_pref:=l.provider_ref;v_failure:=l.status_detail;
  SELECT s.currency,s.commerce_order_id INTO v_currency,v_order FROM public.pos_split_payment_sessions s WHERE s.id=l.session_id;
  IF l.card_terminal_attempt_id IS NOT NULL THEN SELECT terminal_transaction_id,rrn,authorization_code,card_last4,card_scheme INTO v_txn,v_rrn,v_auth,v_last4,v_scheme FROM public.pos_card_terminal_attempts WHERE id=l.card_terminal_attempt_id; END IF;
 ELSIF p_source_kind='split_refund' THEN
  SELECT * INTO r FROM public.pos_split_refund_requests WHERE id=p_source_id; IF NOT FOUND THEN RAISE EXCEPTION 'split refund not found'; END IF;
  SELECT * INTO l FROM public.pos_split_payment_legs WHERE id=r.leg_id; SELECT s.currency,s.commerce_order_id INTO v_currency,v_order FROM public.pos_split_payment_sessions s WHERE s.id=r.session_id;
  v_provider:=l.tender::text;v_status:='refund_'||r.status::text;v_amount:=r.gross_amount;v_ext:=r.external_reference;v_pref:=r.provider_ref;v_failure:=r.failure_reason;
 ELSE
  IF p_source_kind='ecocash' THEN SELECT 'ecocash',status::text,amount,currency,external_ref,provider_ref,failure_reason,commerce_order_id,sales_invoice_id,customer_id INTO v_provider,v_status,v_amount,v_currency,v_ext,v_pref,v_failure,v_order,v_invoice,v_customer FROM public.ecocash_payment_intents WHERE id=p_source_id;
  ELSIF p_source_kind='paynow' THEN SELECT 'paynow',status::text,amount,currency,external_ref,provider_ref,failure_reason,commerce_order_id,NULL::uuid,customer_id INTO v_provider,v_status,v_amount,v_currency,v_ext,v_pref,v_failure,v_order,v_invoice,v_customer FROM public.paynow_payment_intents WHERE id=p_source_id;
  ELSIF p_source_kind='contipay' THEN SELECT 'contipay',status::text,amount,currency,external_ref,provider_ref,failure_reason,commerce_order_id,NULL::uuid,customer_id INTO v_provider,v_status,v_amount,v_currency,v_ext,v_pref,v_failure,v_order,v_invoice,v_customer FROM public.contipay_payment_intents WHERE id=p_source_id; END IF;
  IF v_provider IS NULL THEN RAISE EXCEPTION 'payment intent not found'; END IF;
 END IF;
 IF v_order IS NOT NULL THEN SELECT sales_invoice_id,customer_id INTO v_invoice,v_customer FROM public.commerce_orders WHERE id=v_order; END IF;
 IF v_invoice IS NOT NULL THEN SELECT document_number,customer_id INTO v_invoice_no,v_customer FROM public.sales_invoices WHERE id=v_invoice; END IF;
 IF v_customer IS NOT NULL THEN SELECT display_name INTO v_customer_name FROM public.customers WHERE id=v_customer; END IF;
 INSERT INTO public.payment_resolution_letters(document_number,source_kind,source_id,provider,observed_status,amount,currency,external_reference,provider_reference,
  terminal_transaction_id,rrn,authorization_code,card_last4,card_scheme,failure_detail,commerce_order_id,sales_invoice_id,customer_id,customer_name,invoice_document_number,
  manager_user_id,manager_employee_id,manager_name,manager_title,manager_employee_code,signature_storage_bucket,signature_storage_path,signature_mime_type,signature_sha256,issue_notes)
 VALUES(public.next_series_value('PDL-'),p_source_kind,p_source_id,v_provider,v_status,COALESCE(v_amount,0),COALESCE(v_currency,'USD'),v_ext,v_pref,v_txn,v_rrn,v_auth,v_last4,v_scheme,
  v_failure,v_order,v_invoice,v_customer,v_customer_name,v_invoice_no,auth.uid(),e.id,e.full_name,v_title,e.employee_code,e.signature_storage_bucket,e.signature_storage_path,e.signature_mime_type,e.signature_sha256,p_issue_notes)
 ON CONFLICT(source_kind,source_id,manager_user_id) DO UPDATE SET observed_status=EXCLUDED.observed_status,failure_detail=EXCLUDED.failure_detail,provider_reference=EXCLUDED.provider_reference,
  issue_notes=EXCLUDED.issue_notes,issued_at=now() RETURNING id INTO v_id;
 RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION public.get_payment_resolution_letter_render_data(p_letter_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE l public.payment_resolution_letters%ROWTYPE; b public.business_document_profile%ROWTYPE;
BEGIN
 IF NOT (public.is_pos_approver() OR public.has_staff_role(ARRAY['admin','finance']::public.staff_role[])) THEN RAISE EXCEPTION 'manager/finance/admin role required'; END IF;
 SELECT * INTO l FROM public.payment_resolution_letters WHERE id=p_letter_id; IF NOT FOUND THEN RAISE EXCEPTION 'payment-resolution letter not found'; END IF;
 SELECT * INTO b FROM public.business_document_profile WHERE singleton=true;
 RETURN jsonb_build_object('letter',to_jsonb(l),'business',to_jsonb(b));
END $$;

CREATE OR REPLACE FUNCTION private.mark_payment_resolution_letter_rendered(p_letter_id UUID,p_bucket TEXT,p_path TEXT,p_sha256 TEXT)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 IF auth.role()<>'service_role' THEN RAISE EXCEPTION 'service role required'; END IF;
 UPDATE public.payment_resolution_letters SET rendered_storage_bucket=p_bucket,rendered_storage_path=p_path,rendered_sha256=p_sha256,rendered_at=now() WHERE id=p_letter_id;
END $$;

REVOKE ALL ON FUNCTION public.get_my_manager_signature(),public.register_my_manager_signature(TEXT,TEXT,TEXT),
 public.set_business_document_profile(TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT),
 public.create_payment_resolution_letter(public.payment_resolution_source_kind,UUID,TEXT),public.get_payment_resolution_letter_render_data(UUID) FROM PUBLIC,anon;

GRANT EXECUTE ON FUNCTION public.get_my_manager_signature(),public.register_my_manager_signature(TEXT,TEXT,TEXT),
 public.create_payment_resolution_letter(public.payment_resolution_source_kind,UUID,TEXT),public.get_payment_resolution_letter_render_data(UUID) TO authenticated,service_role;

GRANT EXECUTE ON FUNCTION public.set_business_document_profile(TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT,TEXT) TO authenticated,service_role;

REVOKE ALL ON FUNCTION private.mark_payment_resolution_letter_rendered(UUID,TEXT,TEXT,TEXT) FROM PUBLIC,anon,authenticated;

GRANT EXECUTE ON FUNCTION private.mark_payment_resolution_letter_rendered(UUID,TEXT,TEXT,TEXT) TO service_role;
