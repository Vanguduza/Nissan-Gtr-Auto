-- Exported from the hosted project's supabase_migrations.schema_migrations (20260908193031 delivery_terminal_device_pairing).
-- Source of record for what production ran; see supabase/live-history/README.md.

-- Driver self-pairing for admin-assigned delivery terminals.
-- Drivers cannot create/modify terminal profiles; admin must enable delivery and bind device_id first.

CREATE OR REPLACE FUNCTION public.register_delivery_card_terminal_device_key(
 p_terminal_id UUID,
 p_device_id TEXT,
 p_public_key_spki_base64 TEXT,
 p_key_sha256 TEXT
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path=''
AS $$
DECLARE
 v_id UUID;
 v_bytes BYTEA;
 v_terminal public.pos_card_terminals%ROWTYPE;
BEGIN
 IF NOT public.has_staff_role(ARRAY['driver']::public.staff_role[]) THEN
  RAISE EXCEPTION 'driver role required';
 END IF;
 IF trim(COALESCE(p_device_id,''))='' OR trim(COALESCE(p_public_key_spki_base64,''))='' OR COALESCE(p_key_sha256,'')!~'^[0-9a-f]{64}$' THEN
  RAISE EXCEPTION 'device id, public key and sha256 required';
 END IF;
 SELECT * INTO v_terminal FROM public.pos_card_terminals WHERE id=p_terminal_id AND is_active FOR UPDATE;
 IF NOT FOUND OR NOT v_terminal.allow_delivery THEN
  RAISE EXCEPTION 'active delivery-enabled terminal required';
 END IF;
 IF v_terminal.device_id IS NULL OR v_terminal.device_id<>trim(p_device_id) THEN
  RAISE EXCEPTION 'terminal must be admin-assigned to this delivery device before pairing';
 END IF;
 BEGIN
  v_bytes:=decode(p_public_key_spki_base64,'base64');
 EXCEPTION WHEN OTHERS THEN
  RAISE EXCEPTION 'public key must be base64 SPKI';
 END;
 IF octet_length(v_bytes)<200 OR octet_length(v_bytes)>1024 THEN
  RAISE EXCEPTION 'unexpected public key size';
 END IF;
 IF encode(digest(v_bytes,'sha256'),'hex')<>lower(p_key_sha256) THEN
  RAISE EXCEPTION 'public key sha256 mismatch';
 END IF;

 UPDATE public.pos_card_terminal_device_keys
 SET is_active=false,revoked_at=now()
 WHERE terminal_id=p_terminal_id AND device_id=trim(p_device_id)
   AND is_active AND revoked_at IS NULL;
 INSERT INTO public.pos_card_terminal_device_keys(
  terminal_id,device_id,public_key_spki_base64,key_sha256,is_active,created_by,revoked_at
 ) VALUES(
  p_terminal_id,trim(p_device_id),trim(p_public_key_spki_base64),lower(p_key_sha256),true,auth.uid(),NULL
 )
 ON CONFLICT(terminal_id,device_id,key_sha256) DO UPDATE SET
  public_key_spki_base64=EXCLUDED.public_key_spki_base64,
  is_active=true,revoked_at=NULL,created_by=auth.uid()
 RETURNING id INTO v_id;
 RETURN v_id;
END $$;

REVOKE ALL ON FUNCTION public.register_delivery_card_terminal_device_key(UUID,TEXT,TEXT,TEXT) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.register_delivery_card_terminal_device_key(UUID,TEXT,TEXT,TEXT) TO authenticated,service_role;

-- Include warehouse in the driver payment context so the app can request only terminals
-- valid for the invoice warehouse before starting a charge.
CREATE OR REPLACE FUNCTION public.get_delivery_job_payment_context(p_delivery_job_id UUID)
RETURNS JSONB LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE j public.delivery_jobs%ROWTYPE;i public.sales_invoices%ROWTYPE;v_due NUMERIC;
BEGIN
 SELECT * INTO j FROM public.delivery_jobs WHERE id=p_delivery_job_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'delivery job not found'; END IF;
 IF auth.role()<>'service_role' AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) AND j.assignee_user_id IS DISTINCT FROM auth.uid() THEN
  RAISE EXCEPTION 'not authorized for delivery job settlement';
 END IF;
 SELECT si.* INTO i FROM public.delivery_notes dn JOIN public.sales_invoices si ON si.id=dn.sales_invoice_id WHERE dn.id=j.delivery_note_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'delivery invoice not found'; END IF;
 v_due:=greatest(round(i.total-i.amount_paid,2),0);
 RETURN jsonb_build_object(
  'delivery_job_id',j.id,'sales_invoice_id',i.id,'document_number',i.document_number,
  'warehouse_id',i.warehouse_id,'currency',i.currency,'invoice_total',i.total,
  'amount_paid',i.amount_paid,'amount_due',v_due,
  'invoice_total_minor',public._major_to_minor(i.total),'amount_paid_minor',public._major_to_minor(i.amount_paid),
  'amount_due_minor',public._major_to_minor(v_due),'delivery_payment_method',i.delivery_payment_method,
  'may_collect_cash',i.delivery_payment_method IN('cash_on_delivery','cash_or_card_on_delivery'),
  'may_collect_card',i.delivery_payment_method IN('card_on_delivery','cash_or_card_on_delivery'),'job_status',j.status);
END $$;
REVOKE ALL ON FUNCTION public.get_delivery_job_payment_context(UUID) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.get_delivery_job_payment_context(UUID) TO authenticated,service_role;

CREATE OR REPLACE FUNCTION public.get_delivery_card_terminal_recovery(p_delivery_job_id UUID)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path='' AS $$
DECLARE j public.delivery_jobs%ROWTYPE; v_attempt UUID;
BEGIN
 SELECT * INTO j FROM public.delivery_jobs WHERE id=p_delivery_job_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'delivery job not found'; END IF;
 IF auth.role()<>'service_role' AND NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) AND j.assignee_user_id IS DISTINCT FROM auth.uid() THEN
  RAISE EXCEPTION 'not authorized for delivery job settlement';
 END IF;
 SELECT a.id INTO v_attempt
 FROM public.pos_card_terminal_attempts a
 WHERE a.delivery_job_id=j.id AND (a.status IN('initiated','approved','unknown') OR a.finalization_error IS NOT NULL)
 ORDER BY a.created_at DESC LIMIT 1;
 IF v_attempt IS NULL THEN RETURN NULL; END IF;
 RETURN private.pos_card_terminal_attempt_payload(v_attempt);
END $$;
REVOKE ALL ON FUNCTION public.get_delivery_card_terminal_recovery(UUID) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.get_delivery_card_terminal_recovery(UUID) TO authenticated,service_role;
