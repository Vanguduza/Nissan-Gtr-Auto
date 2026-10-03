-- Exported from the hosted project's supabase_migrations.schema_migrations (20260905130955 harden_provider_payment_ledger_origin).
-- Source of record for what production ran; see supabase/live-history/README.md.

CREATE OR REPLACE FUNCTION public.guard_provider_payment_entry_trusted_origin()
RETURNS trigger
LANGUAGE plpgsql
SET search_path TO ''
AS $$
BEGIN
  IF NEW.tender IN ('ecocash'::public.payment_tender,'paynow'::public.payment_tender,'contipay'::public.payment_tender)
     AND COALESCE(auth.role(),'') <> 'service_role' THEN
    RAISE EXCEPTION 'provider payment entries require verified service-role settlement';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS payment_entries_provider_origin_guard ON public.payment_entries;
CREATE TRIGGER payment_entries_provider_origin_guard
BEFORE INSERT OR UPDATE OF tender,status ON public.payment_entries
FOR EACH ROW EXECUTE FUNCTION public.guard_provider_payment_entry_trusted_origin();

REVOKE EXECUTE ON FUNCTION public.create_ecocash_intent(text,text,numeric,public.currency_code,numeric,text,text,uuid,uuid,uuid,public.currency_code,numeric,numeric,jsonb) FROM anon;
REVOKE EXECUTE ON FUNCTION public.create_paynow_intent(text,public.paynow_method,numeric,public.currency_code,numeric,uuid,public.currency_code,numeric,numeric,jsonb) FROM anon;
REVOKE EXECUTE ON FUNCTION public.create_contipay_intent(text,public.contipay_method,numeric,public.currency_code,numeric,uuid,public.currency_code,numeric,numeric,jsonb) FROM anon;
REVOKE EXECUTE ON FUNCTION public.create_customer_ecocash_intent(uuid,text,text,text,numeric,text,public.currency_code,numeric,numeric,jsonb) FROM anon;
REVOKE EXECUTE ON FUNCTION public.create_customer_paynow_intent(uuid,public.paynow_method,text,numeric,public.currency_code,numeric,numeric,jsonb) FROM anon;
REVOKE EXECUTE ON FUNCTION public.create_customer_contipay_intent(uuid,public.contipay_method,text,numeric,public.currency_code,numeric,numeric,jsonb) FROM anon;
