-- Card machines could not be used at all; found by supabase/sim/e2e_card.py.
--
-- 1. Pairing a till tablet or driver phone with a card machine always failed: the register
--    functions run with an empty search_path and called digest() unqualified
--    ("function digest(bytea, unknown) does not exist"). Now extensions.digest().
-- 2. Starting a counter card charge always failed: the checkout marks the order's active payment
--    provider 'card_terminal', which commerce_orders_active_payment_provider_check did not allow.

ALTER TABLE public.commerce_orders DROP CONSTRAINT IF EXISTS commerce_orders_active_payment_provider_check;
ALTER TABLE public.commerce_orders ADD CONSTRAINT commerce_orders_active_payment_provider_check
  CHECK (active_payment_provider IS NULL OR active_payment_provider = ANY (ARRAY['contipay','paynow','ecocash','card_terminal']));

CREATE OR REPLACE FUNCTION public.register_pos_card_terminal_device_key(p_terminal_id uuid, p_device_id text, p_public_key_spki_base64 text, p_key_sha256 text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
DECLARE v_id UUID; v_bytes BYTEA;
BEGIN
 IF NOT public.has_staff_role(ARRAY['admin']::public.staff_role[]) THEN RAISE EXCEPTION 'admin role required'; END IF;
 IF trim(COALESCE(p_device_id,''))='' OR trim(COALESCE(p_public_key_spki_base64,''))='' OR COALESCE(p_key_sha256,'')!~'^[0-9a-f]{64}$' THEN
  RAISE EXCEPTION 'device id, public key and sha256 required';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM public.pos_card_terminals WHERE id=p_terminal_id AND is_active) THEN RAISE EXCEPTION 'active terminal required'; END IF;
 BEGIN v_bytes:=decode(p_public_key_spki_base64,'base64'); EXCEPTION WHEN OTHERS THEN RAISE EXCEPTION 'public key must be base64 SPKI'; END;
 IF octet_length(v_bytes)<200 OR octet_length(v_bytes)>1024 THEN RAISE EXCEPTION 'unexpected public key size'; END IF;
 IF encode(extensions.digest(v_bytes,'sha256'),'hex')<>lower(p_key_sha256) THEN RAISE EXCEPTION 'public key sha256 mismatch'; END IF;
 UPDATE public.pos_card_terminal_device_keys SET is_active=false,revoked_at=now()
 WHERE terminal_id=p_terminal_id AND device_id=trim(p_device_id) AND is_active AND revoked_at IS NULL;
 INSERT INTO public.pos_card_terminal_device_keys(terminal_id,device_id,public_key_spki_base64,key_sha256,is_active,created_by,revoked_at)
 VALUES(p_terminal_id,trim(p_device_id),trim(p_public_key_spki_base64),lower(p_key_sha256),true,auth.uid(),NULL)
 ON CONFLICT(terminal_id,device_id,key_sha256) DO UPDATE SET
  public_key_spki_base64=EXCLUDED.public_key_spki_base64,is_active=true,revoked_at=NULL,created_by=auth.uid()
 RETURNING id INTO v_id;
 RETURN v_id;
END $function$;

CREATE OR REPLACE FUNCTION public.register_delivery_card_terminal_device_key(p_terminal_id uuid, p_device_id text, p_public_key_spki_base64 text, p_key_sha256 text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
 IF encode(extensions.digest(v_bytes,'sha256'),'hex')<>lower(p_key_sha256) THEN
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
END $function$;
